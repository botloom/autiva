package cn.bitloom.agentic.tool.memory;

import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import cn.bitloom.agentic.memory.AgentMemoryStore;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;

/**
 * 记忆创建工具，在持久记忆存储中创建新文件。
 */
public class MemoryCreateTool extends AbstractTool<MemoryCreateTool.Input> {

	private final AgentMemoryStore store;

	public record Input(
		@ToolParam(description = "新文件路径，相对于记忆根目录（如 'feedback_testing.md'）。") String path,
		@ToolParam(description = "完整文件内容，包括 YAML frontmatter 块和记忆正文。") String fileText
	) {}

	private MemoryCreateTool(AgentMemoryStore store) {
		super("MemoryCreate",
			  "在持久记忆存储中创建新文件。文件必须不存在；如果父目录不存在，自动创建。",
			  Input.class);
		this.store = store;
	}

	@Override
	public @NonNull ToolResult execute(@NonNull Input input, @Nullable ToolContext context) {
		try {
			if (store.exists(input.path())) {
				return ToolResult.error("错误：文件已存在：" + input.path() + "。使用 MemoryStrReplace 修改现有文件。");
			}

			store.createFile(input.path(), input.fileText() != null ? input.fileText() : "");

			return ToolResult.success("成功创建文件：" + input.path() +
					"（" + (input.fileText() != null ? input.fileText().length() : 0) + " 字节）");
		}
		catch (SecurityException e) {
			return ToolResult.error("错误：" + e.getMessage());
		}
		catch (IOException e) {
			return ToolResult.error("创建文件错误：" + e.getMessage());
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

		public MemoryCreateTool build() {
			if (store == null) {
				throw new IllegalStateException("AgentMemoryStore 不能为空");
			}
			return new MemoryCreateTool(store);
		}
	}
}
