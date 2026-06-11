package cn.bitloom.agentic.tool.file;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件读取工具，从本地文件系统读取文件内容。
 */
public class ReadTool extends AbstractTool<ReadTool.Input> {

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

			int startLine = offset != null ? offset : 1;
			int maxLines = limit != null ? limit : 2000;

			if (startLine < 1) {
				startLine = 1;
			}

			List<String> lines = new ArrayList<>();
			int currentLine = 0;
			int linesRead = 0;

			try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
				String line;
				while ((line = reader.readLine()) != null) {
					currentLine++;

					if (currentLine < startLine) {
						continue;
					}

					if (linesRead >= maxLines) {
						break;
					}

					if (line.length() > 2000) {
						line = line.substring(0, 2000) + "... （行已截断）";
					}

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
			rawOutput.append(String.format("显示第%d-%d行，共%d行\n\n", startLine, startLine + linesRead - 1, currentLine));

			for (String line : lines) {
				rawOutput.append(line).append("\n");
			}

			Map<String, Object> data = new LinkedHashMap<>();
			data.put("file", filePath);
			data.put("start_line", startLine);
			data.put("end_line", startLine + linesRead - 1);
			data.put("total_lines", currentLine);

			return ToolResult.builder()
					.status(ToolResult.Status.SUCCESS)
					.message(filePath + " (第" + startLine + "-" + (startLine + linesRead - 1) + "行)")
					.data(data)
					.rawOutput(rawOutput.toString())
					.build();

		}
		catch (IOException e) {
			return ToolResult.error("读取文件时出错", "读取文件时出错: " + e.getMessage());
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
