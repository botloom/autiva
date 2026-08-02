package cn.bitloom.agentic.tool.memory;

import java.io.IOException;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import cn.bitloom.agentic.memory.AgentMemoryStore;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;

/**
 * 记忆插入工具，在现有记忆文件的特定行号插入文本。
 */
public class MemoryInsertTool extends AbstractTool<MemoryInsertTool.Input> {

	private final AgentMemoryStore store;

	public record Input(
		@ToolParam(description = "要修改的文件路径，相对于记忆根目录。") String path,
		@ToolParam(description = "插入文本后的行号。使用 0 在第一行前插入。") Integer insertLine,
		@ToolParam(description = "要插入的文本。") String insertText
	) {}

	private MemoryInsertTool(AgentMemoryStore store) {
		super("MemoryInsert",
			  "在现有记忆文件的特定行号插入文本。行号从 1 开始，传递总行数在末尾追加。",
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

			if (input.insertLine() == null || input.insertLine() < 0) {
				return ToolResult.error("错误：insert_line 必须是非负整数");
			}

			String originalContent = store.readFile(input.path());
			List<String> lines = store.readLines(input.path());

			if (input.insertLine() > lines.size()) {
				return ToolResult.error(String.format("错误：insert_line %d 超过文件长度 %d 行",
						input.insertLine(), lines.size()));
			}

			boolean trailingNewline = originalContent.endsWith("\n");

			lines.add(input.insertLine(), input.insertText() != null ? input.insertText() : "");

			String updated = String.join("\n", lines) + (trailingNewline ? "\n" : "");
			store.writeFile(input.path(), updated);

			return ToolResult.success("成功在 " + input.path() + " 的第 " + input.insertLine() + " 行插入文本");
		}
		catch (SecurityException e) {
			return ToolResult.error("错误：" + e.getMessage());
		}
		catch (IOException e) {
			return ToolResult.error("插入文件错误：" + e.getMessage());
		}
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

		public MemoryInsertTool build() {
			if (store == null) {
				throw new IllegalStateException("AgentMemoryStore 不能为空");
			}
			return new MemoryInsertTool(store);
		}
	}
}
