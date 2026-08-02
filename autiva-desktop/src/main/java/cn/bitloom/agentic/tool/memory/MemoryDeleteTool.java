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
 * 记忆删除工具，从记忆存储中删除文件或目录。
 */
public class MemoryDeleteTool extends AbstractTool<MemoryDeleteTool.Input> {

	private final AgentMemoryStore store;

	public record Input(
		@ToolParam(description = "要删除的文件或目录路径，相对于记忆根目录。") String path
	) {}

	private MemoryDeleteTool(AgentMemoryStore store) {
		super("MemoryDelete",
			  "从记忆存储中删除文件或目录（及其所有内容）。此操作不可逆。",
			  Input.class);
		this.store = store;
	}

	@Override
	public @NonNull ToolResult execute(@NonNull Input input, @Nullable ToolContext context) {
		try {
			if (!store.exists(input.path())) {
				return ToolResult.error("错误：路径不存在：" + input.path());
			}

			store.delete(input.path());
			return ToolResult.success("成功删除：" + input.path());
		}
		catch (SecurityException e) {
			return ToolResult.error("错误：" + e.getMessage());
		}
		catch (RuntimeException e) {
			return ToolResult.error("删除路径错误：" + e.getMessage());
		}
		catch (IOException e) {
			return ToolResult.error("删除路径错误：" + e.getMessage());
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

		public MemoryDeleteTool build() {
			if (store == null) {
				throw new IllegalStateException("AgentMemoryStore 不能为空");
			}
			return new MemoryDeleteTool(store);
		}
	}
}
