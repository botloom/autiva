package cn.bitloom.agentic.tool.file;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.util.TokenEstimator;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文件读取工具，从本地文件系统读取文件内容。
 * 
 * 优化策略（参考 Claude Code）：
 * 1. 文件大小预检查：超过 10MB 的文件直接拒绝
 * 2. Token 预算控制：使用上下文窗口 60% 的 Token 预算
 * 3. 流式读取：边读边计算 Token，达到预算立即停止
 * 4. 行截断：超长行（>2000字符）自动截断
 */
public class ReadTool extends AbstractTool<ReadTool.Input> {

	/**
	 * 默认最大文件大小限制（MB）
	 * 超过此大小的文件会被拒绝，避免内存溢出和 UI 卡死
	 */
	private static final int DEFAULT_MAX_FILE_SIZE_MB = 10;

	/**
	 * 默认 Token 预算比例（相对于上下文窗口）
	 * Claude Code 使用 60%，这里使用保守值
	 */
	private static final double DEFAULT_TOKEN_BUDGET_RATIO = 0.6;

	/**
	 * 默认上下文窗口大小（Token 数）
	 * Claude 3.5 Sonnet: 200K, Opus: 200K
	 */
	private static final int DEFAULT_CONTEXT_WINDOW_SIZE = 200000;

	/**
	 * 默认 Token 预算（上下文窗口的 60%）
	 */
	private static final int DEFAULT_TOKEN_BUDGET = (int) (DEFAULT_CONTEXT_WINDOW_SIZE * DEFAULT_TOKEN_BUDGET_RATIO);

	/**
	 * 批处理行数（流式读取时每批处理的行数）
	 */
	private static final int BATCH_SIZE_LINES = 256;

	/**
	 * 支持的图片文件扩展名（小写）
	 */
	private static final Set<String> IMAGE_EXTENSIONS = Set.of(
			"png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "svg", "tiff", "tif",
			"heic", "heif", "raw", "psd", "ai", "eps"
	);

	/**
	 * 支持的二进制文件扩展名（不应作为文本读取）
	 */
	private static final Set<String> BINARY_EXTENSIONS = Set.of(
			"exe", "dll", "so", "dylib", "bin", "dat", "db", "sqlite", "mdb",
			"zip", "tar", "gz", "rar", "7z", "bz2", "xz",
			"mp3", "wav", "ogg", "flac", "aac", "m4a", "wma",
			"mp4", "avi", "mkv", "mov", "wmv", "flv", "webm",
			"pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
			"otf", "ttf", "woff", "woff2", "eot"
	);

	private static final String DESCRIPTION = """
			从本地文件系统读取文件。你可以使用此工具直接访问任何文件。
			假设此工具能够读取机器上的所有文件。如果用户提供了文件路径，假设该路径有效。读取不存在的文件是可以的；将返回错误。

			用法：
			- file_path参数必须是绝对路径，而不是相对路径
			- 默认情况下，从文件开头读取最多2000行
			- 你可以选择指定行偏移量和限制（对于长文件特别有用），但建议不提供这些参数来读取整个文件
			- 超过2000个字符的行将被截断
			- 结果使用cat -n格式返回，行号从1开始
			- 此工具允许读取图像（如PNG、JPG等）。读取图像文件时，内容将以视觉方式呈现。
			- 此工具可以读取PDF文件（.pdf）。PDF逐页处理，提取文本和视觉内容进行分析。
			- 此工具可以读取Jupyter笔记本（.ipynb文件），返回所有单元格及其输出，结合代码、文本和可视化内容。
			- 此工具只能读取文件，不能读取目录。要读取目录，请通过Bash工具使用ls命令。
			- 你可以在一次响应中调用多个工具。推测性地并行读取多个可能有用的文件总是更好的做法。
			- 你会经常被要求读取截图。如果用户提供了截图路径，请始终使用此工具查看该路径的文件。此工具适用于所有临时文件路径。
			- 如果你读取的文件存在但内容为空，你将收到系统提醒警告代替文件内容。
			""";

	private ReadTool() {
		super("Read", DESCRIPTION, Input.class);
	}

	public record Input(
		@ToolParam(description = "要读取的文件的绝对路径") String filePath,
		@ToolParam(description = "开始读取的行号。仅在文件太大无法一次读取时提供", required = false) Integer offset,
		@ToolParam(description = "要读取的行数。仅在文件太大无法一次读取时提供。", required = false) Integer limit
	) {}

	@Override
	public ToolResult execute(Input input, ToolContext context) {
		String filePath = input.filePath();
		Integer offset = input.offset();
		Integer limit = input.limit();

		try {
			File file = new File(filePath);

			if (!file.exists()) {
				return ToolResult.error("文件不存在: " + filePath, "错误：文件不存在: " + filePath);
			}

			if (file.isDirectory()) {
				return ToolResult.error("路径是目录: " + filePath, "错误：路径是目录，不是文件: " + filePath);
			}

			// 文件大小预检查：超过 10MB 直接拒绝
			long fileSizeBytes = file.length();
			if (!TokenEstimator.isFileSizeSafe(fileSizeBytes, DEFAULT_MAX_FILE_SIZE_MB)) {
				long fileSizeMB = fileSizeBytes / (1024 * 1024);
				String errorMsg = String.format("文件过大: %d MB，超过安全限制 %d MB。请使用 offset 和 limit 参数分段读取。", 
						fileSizeMB, DEFAULT_MAX_FILE_SIZE_MB);
				return ToolResult.error("文件过大", errorMsg);
			}

			// 检测文件类型：图片文件
			String fileName = file.getName().toLowerCase();
			String fileExtension = fileName.contains(".") 
					? fileName.substring(fileName.lastIndexOf('.') + 1) 
					: "";
			
			if (IMAGE_EXTENSIONS.contains(fileExtension)) {
				return handleImageFile(file, filePath, fileSizeBytes, fileExtension);
			}

			// 检测文件类型：二进制文件
			if (BINARY_EXTENSIONS.contains(fileExtension)) {
				return handleBinaryFile(file, filePath, fileSizeBytes, fileExtension);
			}

			// 额外检测：通过文件头（Magic Bytes）检测二进制文件
			if (isBinaryFileByContent(file)) {
				return handleBinaryFile(file, filePath, fileSizeBytes, "unknown");
			}

			int startLine = offset != null ? offset : 1;
			int maxLines = limit != null ? limit : 2000;

			if (startLine < 1) {
				startLine = 1;
			}

			// 流式读取：边读边计算 Token，避免内存溢出
			List<String> lines = new ArrayList<>();
			int currentLine = 0;
			int linesRead = 0;
			int totalTokens = 0;
			boolean tokenBudgetExceeded = false;

			try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
				String line;
				while ((line = reader.readLine()) != null) {
					currentLine++;

					// 跳过 offset 之前的行
					if (currentLine < startLine) {
						continue;
					}

					// 检查是否达到行数限制
					if (linesRead >= maxLines) {
						break;
					}

					// 截断超长行
					if (line.length() > 2000) {
						line = line.substring(0, 2000) + "... （行已截断）";
					}

					// 计算当前行的 Token 数
					int lineTokens = TokenEstimator.estimateLineTokens(line, currentLine);
					
					// Token 预算检查：如果超出预算，停止读取
					if (totalTokens + lineTokens > DEFAULT_TOKEN_BUDGET) {
						tokenBudgetExceeded = true;
						break;
					}

					totalTokens += lineTokens;
					lines.add(String.format("%6d\t%s", currentLine, line));
					linesRead++;
				}
			}

			if (lines.isEmpty()) {
				if (currentLine == 0) {
					return ToolResult.builder()
							.status(ToolResult.Status.WARNING)
							.message("文件为空: " + filePath)
							.rawOutput("文件为空: " + filePath)
							.build();
				}
				else {
					String rawOutput = String.format("没有可读取的行。文件有%d行，但偏移量为%d", currentLine, startLine);
					return ToolResult.builder()
							.status(ToolResult.Status.WARNING)
							.message("偏移量超出范围")
							.rawOutput(rawOutput)
							.build();
				}
			}

			StringBuilder rawOutput = new StringBuilder();
			rawOutput.append(String.format("文件: %s\n", filePath));
			rawOutput.append(String.format("显示第%d-%d行，共%d行\n", startLine, startLine + linesRead - 1, currentLine));
			
			// 如果超出 Token 预算，添加警告
			if (tokenBudgetExceeded) {
				rawOutput.append(String.format("⚠️ Token 预算已达上限（%d tokens），读取已截断。", DEFAULT_TOKEN_BUDGET));
				rawOutput.append("请使用 offset 和 limit 参数分段读取。\n");
			}
			
			rawOutput.append("\n");

			for (String line : lines) {
				rawOutput.append(line).append("\n");
			}

			Map<String, Object> data = new LinkedHashMap<>();
			data.put("file", filePath);
			data.put("start_line", startLine);
			data.put("end_line", startLine + linesRead - 1);
			data.put("total_lines", currentLine);
			data.put("tokens_used", totalTokens);
			data.put("token_budget", DEFAULT_TOKEN_BUDGET);
			
			if (tokenBudgetExceeded) {
				data.put("token_budget_exceeded", true);
			}

			// 如果超出 Token 预算，返回 WARNING 状态
			ToolResult.Status status = tokenBudgetExceeded ? ToolResult.Status.WARNING : ToolResult.Status.SUCCESS;
			String message = filePath + " (第" + startLine + "-" + (startLine + linesRead - 1) + "行)";
			if (tokenBudgetExceeded) {
				message += " [Token 预算已截断]";
			}

			return ToolResult.builder()
					.status(status)
					.message(message)
					.data(data)
					.rawOutput(rawOutput.toString())
					.build();

		}
		catch (IOException e) {
			return ToolResult.error("读取文件时出错", "读取文件时出错: " + e.getMessage());
		}
	}

	/**
	 * 处理图片文件
	 * 返回图片的基本信息（大小、类型、路径），不尝试读取内容
	 */
	private ToolResult handleImageFile(File file, String filePath, long fileSizeBytes, String extension) {
		String fileSizeStr = formatFileSize(fileSizeBytes);
		
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("type", "image");
		data.put("format", extension.toUpperCase());
		data.put("size_bytes", fileSizeBytes);
		data.put("size_human", fileSizeStr);
		data.put("path", filePath);

		String rawOutput = String.format("""
				📷 图片文件信息
				
				文件: %s
				格式: %s
				大小: %s (%d bytes)
				
				⚠️ 此工具无法直接显示图片内容。图片是二进制数据，不能作为文本读取。
				如需查看图片，请使用支持图片查看的工具或在文件管理器中打开。
				""", 
				filePath, 
				extension.toUpperCase(), 
				fileSizeStr, 
				fileSizeBytes);

		return ToolResult.builder()
				.status(ToolResult.Status.WARNING)
				.message(String.format("图片文件 [%s] - %s", extension.toUpperCase(), fileSizeStr))
				.data(data)
				.rawOutput(rawOutput)
				.build();
	}

	/**
	 * 处理二进制文件
	 * 返回二进制文件的基本信息，不尝试读取内容
	 */
	private ToolResult handleBinaryFile(File file, String filePath, long fileSizeBytes, String extension) {
		String fileSizeStr = formatFileSize(fileSizeBytes);
		
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("type", "binary");
		data.put("format", extension.isEmpty() ? "unknown" : extension.toUpperCase());
		data.put("size_bytes", fileSizeBytes);
		data.put("size_human", fileSizeStr);
		data.put("path", filePath);

		String rawOutput = String.format("""
				📦 二进制文件信息
				
				文件: %s
				类型: %s
				大小: %s (%d bytes)
				
				⚠️ 此文件是二进制格式，不能作为文本读取。
				此工具仅支持文本文件的读取。对于此类文件：
				- PDF 文件：请使用专门的 PDF 阅读器或转换工具
				- 压缩包：请使用解压工具解压后查看内容
				- 媒体文件：请使用媒体播放器或图像查看器
				- 其他二进制文件：请使用对应的专用工具
				""", 
				filePath, 
				extension.isEmpty() ? "未知二进制" : extension.toUpperCase(),
				fileSizeStr, 
				fileSizeBytes);

		return ToolResult.builder()
				.status(ToolResult.Status.WARNING)
				.message(String.format("二进制文件 [%s] - %s", 
						extension.isEmpty() ? "未知" : extension.toUpperCase(), fileSizeStr))
				.data(data)
				.rawOutput(rawOutput)
				.build();
	}

	/**
	 * 通过文件内容检测是否为二进制文件
	 * 读取文件前几个字节，检查是否包含非文本字符
	 */
	private boolean isBinaryFileByContent(File file) {
		try {
			Path path = file.toPath();
			byte[] bytes = Files.readAllBytes(path);
			
			// 只检查前 8192 字节（8KB）
			int checkLength = Math.min(bytes.length, 8192);
			
			for (int i = 0; i < checkLength; i++) {
				byte b = bytes[i];
				// 检查是否为控制字符（除了常见的换行、回车、制表符）
				if (b < 0x20 && b != 0x09 && b != 0x0A && b != 0x0D) {
					return true;
				}
				// 检查是否为高位字节（> 127 且不是 UTF-8 多字节序列的一部分）
				if (b > 0x7F && !isUtf8ContinuationByte(b)) {
					// 简单启发式：如果连续出现多个高位字节，可能是 UTF-8 编码的中文
					// 但如果单个高位字节孤立出现，可能是二进制数据
					int highByteCount = 0;
					for (int j = i; j < Math.min(i + 4, checkLength); j++) {
						if (bytes[j] > 0x7F) {
							highByteCount++;
						}
					}
					if (highByteCount < 2) {
						return true;
					}
				}
			}
			return false;
		}
		catch (IOException e) {
			// 如果无法读取，保守地假设为文本文件
			return false;
		}
	}

	/**
	 * 检查字节是否为 UTF-8 多字节序列的后续字节
	 * UTF-8 后续字节的范围：0x80-0xBF (10xxxxxx)
	 */
	private boolean isUtf8ContinuationByte(byte b) {
		return (b & 0xC0) == 0x80;
	}

	/**
	 * 格式化文件大小为人类可读的字符串
	 */
	private static String formatFileSize(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		}
		else if (bytes < 1024 * 1024) {
			return String.format("%.1f KB", bytes / 1024.0);
		}
		else if (bytes < 1024L * 1024 * 1024) {
			return String.format("%.1f MB", bytes / (1024.0 * 1024));
		}
		else {
			return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		public ReadTool build() {
			return new ReadTool();
		}
	}

}
