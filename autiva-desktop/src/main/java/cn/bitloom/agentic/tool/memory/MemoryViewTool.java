package cn.bitloom.agentic.tool.memory;

import java.io.IOException;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import cn.bitloom.agentic.memory.AgentMemoryStore;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;

/**
 * 记忆查看工具，查看持久记忆存储中文件的内容或列出目录的内容。
 */
public class MemoryViewTool extends AbstractTool<MemoryViewTool.Input> {

	private final AgentMemoryStore store;

	public record Input(
		@ToolParam(description = "要查看的文件或目录路径，相对于记忆根目录。使用空字符串或 '/' 查看根目录。使用 'MEMORY.md' 读取索引。") String path,
		@ToolParam(description = "可选行范围，格式为 'start,end'（如 '1,50'），查看文件时使用。目录时忽略。") String viewRange
	) {}

	private MemoryViewTool(AgentMemoryStore store) {
		super("MemoryView",
			  "查看持久记忆存储中文件的内容或列出目录的内容。使用空路径或 '/' 检查根目录，显示所有记忆文件。",
			  Input.class);
		this.store = store;
	}

	@Override
	public @NonNull ToolResult execute(@NonNull Input input, @Nullable ToolContext context) {
		try {
			String path = StringUtils.hasText(input.path()) ? input.path() : "/";

			if (!store.exists(path)) {
				return ToolResult.error("错误：路径不存在：" + input.path());
			}

			if (store.isDirectory(path)) {
				return ToolResult.success(listDirectory(path, input.path()));
			}
			else {
				return ToolResult.success(readFile(path, input.viewRange()));
			}
		}
		catch (SecurityException e) {
			return ToolResult.error("错误：" + e.getMessage());
		}
		catch (IOException e) {
			return ToolResult.error("读取路径错误：" + e.getMessage());
		}
	}

	private String listDirectory(String path, String displayPath) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("目录内容 ").append(displayPath.isEmpty() ? "/" : displayPath).append(":\n\n");

		List<AgentMemoryStore.Entry> entries = store.list(path);
		for (AgentMemoryStore.Entry entry : entries) {
			if (entry.directory()) {
				sb.append("  ").append(entry.name()).append("/\n");
				// 二级展开
				String subPath = path.equals("/") ? entry.name() : path + "/" + entry.name();
				if (store.exists(subPath) && store.isDirectory(subPath)) {
					for (AgentMemoryStore.Entry sub : store.list(subPath)) {
						String prefix = sub.directory() ? "/" : "（" + sub.size() + " 字节）";
						sb.append("    ").append(sub.name()).append(prefix).append("\n");
					}
				}
			}
			else {
				sb.append("  ").append(entry.name()).append("（").append(entry.size()).append(" 字节）\n");
			}
		}
		return sb.toString();
	}

	private String readFile(String path, String viewRange) throws IOException {
		List<String> allLines = store.readLines(path);
		int totalLines = allLines.size();

		int startLine = 1;
		int endLine = totalLines;

		if (StringUtils.hasText(viewRange)) {
			String[] parts = viewRange.split(",");
			if (parts.length == 2) {
				try {
					startLine = Math.max(1, Integer.parseInt(parts[0].trim()));
					endLine = Math.min(totalLines, Integer.parseInt(parts[1].trim()));
				}
				catch (NumberFormatException e) {
					return "错误：view_range 必须是 'start,end' 整数（如 '1,50'）";
				}
			}
			else {
				return "错误：view_range 必须是 'start,end'（如 '1,50'）";
			}
		}

		StringBuilder sb = new StringBuilder();
		sb.append(String.format("文件：%s\n行 %d-%d 共 %d 行\n\n", path, startLine, endLine, totalLines));

		for (int i = startLine - 1; i < endLine; i++) {
			sb.append(String.format("%6d\t%s\n", i + 1, allLines.get(i)));
		}

		return sb.toString();
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

		public MemoryViewTool build() {
			if (store == null) {
				throw new IllegalStateException("AgentMemoryStore 不能为空");
			}
			return new MemoryViewTool(store);
		}
	}
}
