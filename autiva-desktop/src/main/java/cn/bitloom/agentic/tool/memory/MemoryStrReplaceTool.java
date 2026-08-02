package cn.bitloom.agentic.tool.memory;

import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import cn.bitloom.agentic.memory.AgentMemoryStore;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;

/**
 * 记忆字符串替换工具，替换现有记忆文件中的精确字符串。
 */
public class MemoryStrReplaceTool extends AbstractTool<MemoryStrReplaceTool.Input> {

	private final AgentMemoryStore store;

	public record Input(
		@ToolParam(description = "要编辑的文件路径，相对于记忆根目录。") String path,
		@ToolParam(description = "要查找和替换的精确文本。必须在文件中恰好出现一次。") String oldStr,
		@ToolParam(description = "替换文本。使用空字符串删除匹配文本。") String newStr
	) {}

	private MemoryStrReplaceTool(AgentMemoryStore store) {
		super("MemoryStrReplace",
			  "替换现有记忆文件中的精确字符串。old_str 必须完全匹配且恰好出现一次。",
			  Input.class);
		this.store = store;
	}

	@Override
	public @NonNull ToolResult execute(@NonNull Input input, @Nullable ToolContext context) {
		try {
			if (!store.exists(input.path())) {
				return ToolResult.error("错误：文件不存在：" + input.path());
			}

			if (store.isDirectory(input.path())) {
				return ToolResult.error("错误：路径是目录，不是文件：" + input.path());
			}

			String content = store.readFile(input.path());
			int occurrences = countOccurrences(content, input.oldStr());

			if (occurrences == 0) {
				return ToolResult.error("错误：文件中未找到 old_str：" + input.path());
			}

			if (occurrences > 1) {
				return ToolResult.error(String.format(
						"错误：old_str 在文件中出现 %d 次。提供更多周围上下文使其唯一。", occurrences));
			}

			String replacement = input.newStr() != null ? input.newStr() : "";
			String updated = replaceFirst(content, input.oldStr(), replacement);

			store.writeFile(input.path(), updated);

			if (!StringUtils.hasText(replacement)) {
				return ToolResult.success(String.format("成功从 %s 中删除匹配文本。", input.path()));
			}

			String snippet = generateEditSnippet(updated, replacement);
			return ToolResult.success(String.format("成功编辑 %s。结果片段：\n%s", input.path(), snippet));
		}
		catch (SecurityException e) {
			return ToolResult.error("错误：" + e.getMessage());
		}
		catch (IOException e) {
			return ToolResult.error("编辑文件错误：" + e.getMessage());
		}
	}

	private int countOccurrences(String text, String substring) {
		int count = 0;
		int index = 0;
		while ((index = text.indexOf(substring, index)) != -1) {
			count++;
			index += substring.length();
		}
		return count;
	}

	private String replaceFirst(String text, String oldStr, String newStr) {
		int index = text.indexOf(oldStr);
		if (index == -1) {
			return text;
		}
		return text.substring(0, index) + newStr + text.substring(index + oldStr.length());
	}

	private String generateEditSnippet(String fileContent, String newStr) {
		String[] lines = fileContent.split("\n", -1);
		String[] newLines = newStr.split("\n", -1);

		int editStartLine = -1;
		int editEndLine = -1;

		for (int i = 0; i < lines.length; i++) {
			if (newLines.length > 0 && lines[i].contains(newLines[0])) {
				boolean matches = true;
				for (int j = 1; j < newLines.length && i + j < lines.length; j++) {
					if (!lines[i + j].contains(newLines[j])) {
						matches = false;
						break;
					}
				}
				if (matches) {
					editStartLine = i;
					editEndLine = i + newLines.length - 1;
					break;
				}
			}
		}

		if (editStartLine == -1) {
			editStartLine = 0;
			editEndLine = Math.min(10, lines.length - 1);
		}

		int startLine = Math.max(0, editStartLine - 5);
		int endLine = Math.min(lines.length - 1, editEndLine + 5);

		StringBuilder snippet = new StringBuilder();
		for (int i = startLine; i <= endLine; i++) {
			snippet.append(String.format("%6d→%s", i + 1, lines[i]));
			if (i < endLine) {
				snippet.append("\n");
			}
		}
		return snippet.toString();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private AgentMemoryStore store;

		private Builder() {
		}

		public Builder store(AgentMemoryStore store) {
			this.store = store;
			return this;
		}

		public MemoryStrReplaceTool build() {
			if (store == null) {
				throw new IllegalStateException("AgentMemoryStore 不能为空");
			}
			return new MemoryStrReplaceTool(store);
		}
	}
}
