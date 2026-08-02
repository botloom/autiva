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
 * 记忆重命名工具，在记忆存储内重命名或移动文件或目录。
 */
public class MemoryRenameTool extends AbstractTool<MemoryRenameTool.Input> {

	private final AgentMemoryStore store;

	public record Input(
		@ToolParam(description = "文件或目录的当前路径，相对于记忆根目录。") String oldPath,
		@ToolParam(description = "文件或目录的新路径，相对于记忆根目录。") String newPath
	) {}

	private MemoryRenameTool(AgentMemoryStore store) {
		super("MemoryRename",
			  "在记忆存储内重命名或移动文件或目录。源路径必须存在；目标路径必须不存在。",
			  Input.class);
		this.store = store;
	}

	@Override
	public @NonNull ToolResult execute(@NonNull Input input, @Nullable ToolContext context) {
		try {
			if (!store.exists(input.oldPath())) {
				return ToolResult.error("错误：源路径不存在：" + input.oldPath());
			}

			if (store.exists(input.newPath())) {
				return ToolResult.error("错误：目标路径已存在：" + input.newPath());
			}

			store.move(input.oldPath(), input.newPath());

			return ToolResult.success(String.format("成功将 '%s' 重命名为 '%s'", input.oldPath(), input.newPath()));
		}
		catch (SecurityException e) {
			return ToolResult.error("错误：" + e.getMessage());
		}
		catch (IOException e) {
			return ToolResult.error("重命名路径错误：" + e.getMessage());
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

		public MemoryRenameTool build() {
			if (store == null) {
				throw new IllegalStateException("AgentMemoryStore 不能为空");
			}
			return new MemoryRenameTool(store);
		}
	}
}
