package cn.bitloom.agentic.tool.memory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;

/**
 * 记忆查看工具，查看持久记忆存储中文件的内容或列出目录的内容。
 *
 * @author Christian Tzolov
 */
public class MemoryViewTool extends AbstractTool<MemoryViewTool.Input> {

	private final Path memoriesDir;

	/**
	 * 输入参数 record。
	 */
	public record Input(
		@ToolParam(description = "要查看的文件或目录路径，相对于记忆根目录。使用空字符串或 '/' 查看根目录。使用 'MEMORY.md' 读取索引。") String path,
		@ToolParam(description = "可选行范围，格式为 'start,end'（如 '1,50'），查看文件时使用。目录时忽略。") String viewRange
	) {}

	private MemoryViewTool(Path memoriesDir) {
		super("MemoryView",
			  "查看持久记忆存储中文件的内容或列出目录的内容。使用空路径或 '/' 检查根目录，显示所有记忆文件。",
			  Input.class);
		this.memoriesDir = memoriesDir.normalize();
	}

	@Override
	public @NonNull ToolResult execute(@NonNull Input input, @Nullable ToolContext context) {
		try {
			Path target = resolveSafePath(input.path());

			if (!Files.exists(target)) {
				return ToolResult.error("错误：路径不存在：" + input.path());
			}

			if (Files.isDirectory(target)) {
				return ToolResult.success(listDirectory(target, input.path()));
			}
			else {
				return ToolResult.success(readFile(target, input.viewRange()));
			}
		}
		catch (SecurityException e) {
			return ToolResult.error("错误：" + e.getMessage());
		}
		catch (IOException e) {
			return ToolResult.error("读取路径错误：" + e.getMessage());
		}
	}

	private Path resolveSafePath(String relativePath) {
		if (!StringUtils.hasText(relativePath) || relativePath.equals("/")) {
			return this.memoriesDir;
		}
		Path userPath = Paths.get(relativePath);
		if (userPath.isAbsolute()) {
			throw new SecurityException("不允许绝对路径：'" + relativePath + "'");
		}
		Path resolved = this.memoriesDir.resolve(userPath).normalize();
		if (!resolved.startsWith(this.memoriesDir)) {
			throw new SecurityException("检测到路径遍历尝试：'" + relativePath + "' 逃离记忆目录");
		}
		return resolved;
	}

	private String listDirectory(Path dir, String displayPath) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("目录内容 ").append(displayPath.isEmpty() ? "/" : displayPath).append(":\n\n");

		try (Stream<Path> level1 = Files.list(dir)) {
			List<Path> entries = level1.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
			for (Path entry : entries) {
				String name = entry.getFileName().toString();
				if (Files.isDirectory(entry)) {
					sb.append("  ").append(name).append("/\n");
					try (Stream<Path> level2 = Files.list(entry)) {
						List<Path> subEntries = level2
							.sorted(Comparator.comparing(p -> p.getFileName().toString()))
							.toList();
						for (Path sub : subEntries) {
							String subName = sub.getFileName().toString();
							if (Files.isDirectory(sub)) {
								sb.append("    ").append(subName).append("/\n");
							}
							else {
								long size = Files.size(sub);
								sb.append("    ").append(subName).append("（").append(size).append(" 字节）\n");
							}
						}
					}
				}
				else {
					long size = Files.size(entry);
					sb.append("  ").append(name).append("（").append(size).append(" 字节）\n");
				}
			}
		}
		return sb.toString();
	}

	private String readFile(Path file, String viewRange) throws IOException {
		List<String> allLines = Files.readAllLines(file, StandardCharsets.UTF_8);
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
		sb.append(String.format("文件：%s\n行 %d-%d 共 %d 行\n\n", file.getFileName(), startLine, endLine, totalLines));

		for (int i = startLine - 1; i < endLine; i++) {
			sb.append(String.format("%6d\t%s\n", i + 1, allLines.get(i)));
		}

		return sb.toString();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private Path memoriesDir = Paths.get("/memories");

		private Builder() {
		}

		public Builder memoriesDir(Path memoriesDir) {
			this.memoriesDir = memoriesDir;
			return this;
		}

		public Builder memoriesDir(String memoriesDir) {
			this.memoriesDir = memoriesDir != null ? Paths.get(memoriesDir) : Paths.get("/memories");
			return this;
		}

		public MemoryViewTool build() {
			try {
				Files.createDirectories(memoriesDir);
			}
			catch (IOException e) {
				throw new IllegalStateException("创建记忆目录失败：" + memoriesDir, e);
			}
			return new MemoryViewTool(memoriesDir);
		}
	}
}