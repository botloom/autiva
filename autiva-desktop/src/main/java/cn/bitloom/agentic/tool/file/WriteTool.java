package cn.bitloom.agentic.tool.file;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文件写入工具，将内容写入本地文件系统。
 */
public class WriteTool extends AbstractTool<WriteTool.Input> {

	private static final String DESCRIPTION = """
			将文件写入本地文件系统。

			用法：
			- 如果提供的路径已有文件，此工具将覆盖现有文件。
			- 如果是现有文件，你必须先使用Read工具读取文件内容。如果你没有先读取文件，此工具将失败。
			- 始终优先编辑代码库中的现有文件。除非明确需要，否则不要创建新文件。
			- 不要主动创建文档文件（*.md）或README文件。仅在用户明确请求时才创建文档文件。
			- 仅在用户明确请求时使用表情符号。除非被要求，否则避免向文件写入表情符号。
			""";

	private WriteTool() {
		super("Write", DESCRIPTION, Input.class);
	}

	public record Input(
		@ToolParam(description = "要写入的文件的绝对路径（必须是绝对路径，不是相对路径）") String filePath,
		@ToolParam(description = "要写入文件的内容") String content
	) {}

	@Override
	public ToolResult execute(Input input, ToolContext context) {
		String filePath = input.filePath();
		String content = input.content();

		try {
			content = content != null ? content : "";

			Path path = Paths.get(filePath);
			File file = path.toFile();

			File parentDir = file.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				if (!parentDir.mkdirs()) {
					return ToolResult.error("无法创建父目录: " + filePath);
				}
			}

			boolean fileExists = file.exists();

			try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
				writer.write(content);
			}

			String action = fileExists ? "覆盖" : "创建";
			String message = String.format("已%s %s（%d字节）", action, filePath, content.length());

			Map<String, Object> data = new LinkedHashMap<>();
			data.put("file", filePath);
			data.put("bytes", content.length());
			data.put("action", action);

			return ToolResult.success(message, data);

		}
		catch (IOException e) {
			return ToolResult.error("写入文件时出错: " + e.getMessage());
		}
		catch (Exception e) {
			return ToolResult.error("错误: " + e.getMessage());
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		public WriteTool build() {
			return new WriteTool();
		}
	}

}
