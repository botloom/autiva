package cn.bitloom.agentic.tool.file;

import cn.bitloom.agentic.diff.DiffService;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.tool.ToolUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文件编辑工具，在文件中执行精确的字符串替换。
 */
@Slf4j
public class EditTool extends AbstractTool<EditTool.Input> {

	private static final String DESCRIPTION = """
			在文件中执行精确的字符串替换。

			用法：
			- 在编辑之前，你必须至少在对话中使用过一次`Read`工具。如果你尝试在没有读取文件的情况下编辑，此工具将报错。
			- 编辑Read工具输出的文本时，确保保留行号前缀之后出现的精确缩进（制表符/空格）。行号前缀格式为：空格+行号+制表符。该制表符之后的所有内容是要匹配的实际文件内容。永远不要在old_string或new_string中包含行号前缀的任何部分。
			- 始终优先编辑代码库中的现有文件。除非明确需要，否则不要创建新文件。
			- 仅在用户明确请求时使用表情符号。除非被要求，否则避免向文件添加表情符号。
			- 如果`old_string`在文件中不唯一，编辑将失败。请提供更大的字符串和更多周围上下文使其唯一，或使用`replace_all`更改所有`old_string`的实例。
			- 使用`replace_all`在文件中替换和重命名字符串。如果你想要重命名变量，此参数很有用。
			""";

	private final DiffService diffService;

	private EditTool(DiffService diffService) {
		super("Edit", DESCRIPTION, Input.class);
		this.diffService = diffService;
	}

	public record Input(
		@ToolParam(description = "要修改的文件的绝对路径") String filePath,
		@ToolParam(description = "要替换的文本") String old_string,
		@ToolParam(description = "替换后的文本（必须与old_string不同）") String new_string,
		@ToolParam(description = "替换所有old_string的出现（默认false）", required = false) Boolean replace_all
	) {}

	@Override
	public ToolResult execute(Input input, ToolContext context) {
		String filePath = input.filePath();
		String old_string = input.old_string();
		String new_string = input.new_string();
		Boolean replace_all = input.replace_all();

		try {
			File file = new File(filePath);
			Path path = file.toPath();

			if (!file.exists()) {
				return ToolResult.error("文件不存在: " + filePath);
			}

			if (file.isDirectory()) {
				return ToolResult.error("路径是目录，不是文件: " + filePath);
			}

			if (old_string.equals(new_string)) {
				return ToolResult.error("old_string和new_string必须不同");
			}

			String originalContent;
			try {
				originalContent = Files.readString(path, StandardCharsets.UTF_8);
			}
			catch (IOException e) {
				return ToolResult.error("读取文件内容时出错: " + e.getMessage());
			}

			int occurrences = ToolUtils.countOccurrences(originalContent, old_string);

			if (occurrences == 0) {
				return ToolResult.error("在文件中未找到old_string: " + filePath);
			}

			boolean replaceAll = Boolean.TRUE.equals(replace_all);

			if (!replaceAll && occurrences > 1) {
				return ToolResult.error(String.format(
						"old_string在文件中出现%d次。请提供更大的字符串和更多周围上下文使其唯一，或使用replace_all=true更改所有实例。",
						occurrences));
			}

			String newContent = replaceAll
					? ToolUtils.replaceAll(originalContent, old_string, new_string)
					: ToolUtils.replaceFirst(originalContent, old_string, new_string);

			try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
				writer.write(newContent);
			}

			// 写入后生成 diff（非阻塞，失败不影响写入结果）
			if (diffService != null) {
				try {
					diffService.generateDiff(path, originalContent, newContent);
				} catch (Exception e) {
					log.warn("生成 Diff 失败（不影响写入）: {}", filePath, e);
				}
			}

			String snippet = ToolUtils.generateEditSnippet(newContent, new_string);
			String rawOutput = String.format(
					"文件%s已更新。以下是对已编辑文件运行`cat -n`的结果片段:\n%s",
					filePath, snippet);

			Map<String, Object> data = new LinkedHashMap<>();
			data.put("file", filePath);
			data.put("occurrences", occurrences);
			data.put("replace_all", replaceAll);

			return ToolResult.builder()
					.status(ToolResult.Status.SUCCESS)
					.message("已更新 " + filePath)
					.data(data)
					.rawOutput(rawOutput)
					.build();

		}
		catch (IOException e) {
			return ToolResult.error("编辑文件时出错: " + e.getMessage());
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private DiffService diffService;

		public Builder diffService(DiffService diffService) {
			this.diffService = diffService;
			return this;
		}

		public EditTool build() {
			return new EditTool(diffService);
		}
	}

}
