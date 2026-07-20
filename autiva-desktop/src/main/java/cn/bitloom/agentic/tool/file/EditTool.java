package cn.bitloom.agentic.tool.file;

import cn.bitloom.agentic.diff.DiffService;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.tool.ToolUtils;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文件编辑工具，在文件中执行精确的字符串替换。
 */
@Slf4j
public class EditTool extends AbstractTool<EditTool.Input> {

	private static final String DESCRIPTION = """
			在文件中做精确字符串替换。必须先 Read 文件。old_string 必须精确匹配（包括缩进）。去除 Read 输出的行号前缀后再匹配。用 replace_all 替换全部出现。
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
	public @NonNull ToolResult execute(Input input, ToolContext context) {
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

			// 检测原始行尾风格并归一化为 LF 进行匹配
			// 解决 Windows CRLF 文件与 LLM 提供的 LF 行尾不匹配导致 indexOf 失败的问题
			boolean useCrlf = originalContent.contains("\r\n");
			String normalizedContent = useCrlf ? originalContent.replace("\r\n", "\n") : originalContent;
			String normalizedOld = old_string.replace("\r\n", "\n");
			String normalizedNew = new_string.replace("\r\n", "\n");

			int occurrences = ToolUtils.countOccurrences(normalizedContent, normalizedOld);

			if (occurrences == 0) {
				return ToolResult.error(String.format(
						"在文件中未找到old_string: %s。可能原因：" +
						"(1) 缩进字符不匹配（Tab/Space 混用）；" +
						"(2) old_string 误包含了 Read 输出的行号前缀（应为空格+行号+制表符之后的内容，不要包含前缀）；" +
						"(3) 字符串大小写或空白字符差异。建议使用 Grep 工具（outputMode=content, showLineNumbers=true）定位实际内容。",
						filePath));
			}

			boolean replaceAll = Boolean.TRUE.equals(replace_all);

			if (!replaceAll && occurrences > 1) {
				return ToolResult.error(String.format(
						"old_string在文件中出现%d次。请提供更大的字符串和更多周围上下文使其唯一，或使用replace_all=true更改所有实例。",
						occurrences));
			}

			String normalizedResult = replaceAll
					? ToolUtils.replaceAll(normalizedContent, normalizedOld, normalizedNew)
					: ToolUtils.replaceFirst(normalizedContent, normalizedOld, normalizedNew);

			// 还原原始行尾风格，保持文件行尾一致性
			String newContent = useCrlf ? normalizedResult.replace("\n", "\r\n") : normalizedResult;

			try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
					StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
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

			String snippet = ToolUtils.generateEditSnippet(normalizedResult, normalizedNew);
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
