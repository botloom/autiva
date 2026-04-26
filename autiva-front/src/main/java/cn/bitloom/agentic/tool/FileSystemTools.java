/*
* Copyright 2025 - 2025 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package cn.bitloom.agentic.tool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * @author Christian Tzolov
 */
public class FileSystemTools {

	// @formatter:off
	@Tool(name = "Read", description = """
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
		""")
	public String read(
		@ToolParam(description = "要读取的文件的绝对路径") String filePath,
		@ToolParam(description = "开始读取的行号。仅在文件太大无法一次读取时提供", required = false) Integer offset,
		@ToolParam(description = "要读取的行数。仅在文件太大无法一次读取时提供。", required = false) Integer limit) { // @formatter:on

		try {
			File file = new File(filePath);

			if (!file.exists()) {
				return "错误：文件不存在: " + filePath;
			}

			if (file.isDirectory()) {
				return "错误：路径是目录，不是文件: " + filePath;
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
					return "文件为空: " + filePath;
				}
				else {
					return String.format("没有可读取的行。文件有%d行，但偏移量为%d", currentLine,
							startLine);
				}
			}

			StringBuilder result = new StringBuilder();
			result.append(String.format("文件: %s\n", filePath));
			result.append(
					String.format("显示第%d-%d行，共%d行\n\n", startLine, startLine + linesRead - 1, currentLine));

			for (String line : lines) {
				result.append(line).append("\n");
			}

			return result.toString();

		}
		catch (IOException e) {
			return "读取文件时出错: " + e.getMessage();
		}
	}

	// @formatter:off
	@Tool(name = "Write", description = """
		将文件写入本地文件系统。

		用法：
		- 如果提供的路径已有文件，此工具将覆盖现有文件。
		- 如果是现有文件，你必须先使用Read工具读取文件内容。如果你没有先读取文件，此工具将失败。
		- 始终优先编辑代码库中的现有文件。除非明确需要，否则不要创建新文件。
		- 不要主动创建文档文件（*.md）或README文件。仅在用户明确请求时才创建文档文件。
		- 仅在用户明确请求时使用表情符号。除非被要求，否则避免向文件写入表情符号。
		""")
	public String write(
		@ToolParam(description = "要写入的文件的绝对路径（必须是绝对路径，不是相对路径）") String filePath,
		@ToolParam(description = "要写入文件的内容") String content) { // @formatter:on

		try {
			content = content != null ? content : "";

			Path path = Paths.get(filePath);
			File file = path.toFile();

			File parentDir = file.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				if (!parentDir.mkdirs()) {
					return "错误：无法为以下路径创建父目录: " + filePath;
				}
			}

			boolean fileExists = file.exists();

			try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
				writer.write(content);
			}

			if (fileExists) {
				return String.format("成功覆盖文件: %s（%d字节）", filePath, content.length());
			}
			else {
				return String.format("成功创建文件: %s（%d字节）", filePath, content.length());
			}

		}
		catch (IOException e) {
			return "写入文件时出错: " + e.getMessage();
		}
		catch (Exception e) {
			return "错误: " + e.getMessage();
		}
	}

	// @formatter:off
	@Tool(name = "Edit", description = """
		在文件中执行精确的字符串替换。

		用法：
		- 在编辑之前，你必须至少在对话中使用过一次`Read`工具。如果你尝试在没有读取文件的情况下编辑，此工具将报错。
		- 编辑Read工具输出的文本时，确保保留行号前缀之后出现的精确缩进（制表符/空格）。行号前缀格式为：空格+行号+制表符。该制表符之后的所有内容是要匹配的实际文件内容。永远不要在old_string或new_string中包含行号前缀的任何部分。
		- 始终优先编辑代码库中的现有文件。除非明确需要，否则不要创建新文件。
		- 仅在用户明确请求时使用表情符号。除非被要求，否则避免向文件添加表情符号。
		- 如果`old_string`在文件中不唯一，编辑将失败。请提供更大的字符串和更多周围上下文使其唯一，或使用`replace_all`更改所有`old_string`的实例。
		- 使用`replace_all`在文件中替换和重命名字符串。如果你想要重命名变量，此参数很有用。
		""")
	public String edit(
		@ToolParam(description = "要修改的文件的绝对路径") String filePath,
		@ToolParam(description = "要替换的文本") String old_string,
		@ToolParam(description = "替换后的文本（必须与old_string不同）") String new_string,
		@ToolParam(description = "替换所有old_string的出现（默认false）", required = false) Boolean replace_all) { // @formatter:on

		try {
			File file = new File(filePath);

			if (!file.exists()) {
				return "错误：文件不存在: " + filePath;
			}

			if (file.isDirectory()) {
				return "错误：路径是目录，不是文件: " + filePath;
			}

			if (old_string.equals(new_string)) {
				return "错误：old_string和new_string必须不同";
			}

			String originalContent;
			try {
				originalContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);
			}
			catch (IOException e) {
				return "读取文件内容时出错: " + e.getMessage();
			}

			int occurrences = countOccurrences(originalContent, old_string);

			if (occurrences == 0) {
				return "错误：在文件中未找到old_string: " + filePath;
			}

			boolean replaceAll = Boolean.TRUE.equals(replace_all);

			if (!replaceAll && occurrences > 1) {
				return String.format(
						"错误：old_string在文件中出现%d次。请提供更大的字符串和更多周围上下文使其唯一，或使用replace_all=true更改所有实例。",
						occurrences);
			}

			String newContent;
			if (replaceAll) {
				newContent = replaceAll(originalContent, old_string, new_string);
			}
			else {
				newContent = replaceFirst(originalContent, old_string, new_string);
			}

			try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
				writer.write(newContent);
			}

			String snippet = generateEditSnippet(newContent, new_string);

			return String.format(
					"文件%s已更新。以下是对已编辑文件运行`cat -n`的结果片段:\n%s",
					filePath, snippet);

		}
		catch (IOException e) {
			return "编辑文件时出错: " + e.getMessage();
		}
	}

	private int countOccurrences(String text, String substring) {
		return ToolUtils.countOccurrences(text, substring);
	}

	private String replaceFirst(String text, String old_string, String new_string) {
		return ToolUtils.replaceFirst(text, old_string, new_string);
	}

	private String replaceAll(String text, String old_string, String new_string) {
		return ToolUtils.replaceAll(text, old_string, new_string);
	}

	private String generateEditSnippet(String fileContent, String newString) {
		return ToolUtils.generateEditSnippet(fileContent, newString);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		public FileSystemTools build() {
			return new FileSystemTools();
		}

	}

}
