package cn.bitloom.agentic.tool.memory;

import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.tool.ToolUtils;
import cn.bitloom.exception.SecurityViolationException;
import lombok.Getter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 用于在专用记忆目录中管理持久化记忆文件的工具。
 * 作用域限定在可配置的记忆目录中。
 *
 */
@Getter
public class AutoMemoryTools {

	private final Path memoriesDir;

	protected AutoMemoryTools(Path memoriesDir) {
		this.memoriesDir = memoriesDir.normalize();
	}

	@Tool(name = "MemoryView", description = """
		查看持久化记忆存储中文件的内容或列出目录的内容。

		用法：
		- 如果路径指向目录：列出两层深度的内容，显示文件大小。
		- 如果路径指向文件：返回带行号的文件内容。
		- 所有路径相对于记忆根目录。
		- 使用空路径或"/"来检查根目录，显示所有记忆文件。
		- 首先查看"MEMORY_INDEX.md" —— 它是始终加载的所有记忆条目索引，在读取或写入任何记忆之前应该先查阅。
		- 可选择提供行范围'start,end'来分页浏览大文件。

		何时使用（重要）：
		- 回答关于用户偏好、历史事件、待办事项的问题前 → 先查看记忆
		- 需要回忆之前的对话、决策或偏好时 → 搜索记忆
		- 不确定某个信息是否已记录时 → 查看记忆索引
		- 在创建新记忆前 → 先查看避免重复

		记忆文件结构：每个记忆文件使用YAML前置元数据：
		  ---
		  name: <短名称>
		  description: <一行描述，用于在未来对话中判断相关性>
		  type: <user | feedback | event | reference | routine | procedure>
		  confidence: <0.0-1.0，默认1.0>
		  created: <创建日期>
		  updated: <最后更新日期>
		  entities: [<关联实体列表>]
		  ---
		  <记忆内容>

		记忆类型：
		- user      —— 用户画像、偏好、性格、习惯
		- feedback  —— 用户指导（纠正和已验证的方法）
		- event     —— 重要事件、决策、里程碑
		- reference —— 外部系统、联系人、资源指针
		- routine   —— 例行程序、周期性任务
		- procedure —— 如何做某事的操作步骤
		""")
	public ToolResult memoryView(
		@ToolParam(description = "要查看的文件或目录路径，相对于记忆根目录。使用空字符串或'/'查看根目录。使用'MEMORY_INDEX.md'读取索引。") String path,
		@ToolParam(description = "查看文件时的可选行范围，格式为'start,end'（例如'1,50'）。对目录忽略。", required = false) String viewRange) { // @formatter:on

		try {
			Path target = resolveSafePath(path);

			if (!Files.exists(target)) {
				return ToolResult.error("路径不存在: " + path);
			}

			if (Files.isDirectory(target)) {
				String result = listDirectory(target, path);
				return ToolResult.builder().status(ToolResult.Status.SUCCESS)
						.message("目录内容: " + (path.isEmpty() ? "/" : path))
						.rawOutput(result)
						.build();
			}
			else {
				String result = readFile(target, viewRange);
				if (result.startsWith("错误")) {
					return ToolResult.error(result);
				}
				return ToolResult.builder().status(ToolResult.Status.SUCCESS)
						.message("查看文件: " + path)
						.rawOutput(result)
						.build();
			}
		}
		catch (SecurityException e) {
			return ToolResult.error(e.getMessage());
		}
		catch (IOException e) {
			return ToolResult.error("读取路径时出错: " + e.getMessage());
		}
	}

	@Tool(name = "MemoryCreate", description = """
		在持久化记忆存储中创建新文件。

		用法：
		- 所有路径相对于记忆根目录。
		- 文件必须不存在；使用MemoryStrReplace来更新现有文件。
		- 如果父目录不存在，会自动创建。
		- 保存记忆是一个两步过程：
		    第1步 —— 调用MemoryCreate写入带有以下前置元数据格式的记忆文件。
		    第2步 —— 调用MemoryStrReplace（或MemoryInsert）向MEMORY_INDEX.md添加指针行。
		            MEMORY_INDEX.md条目格式："- [标题](文件名.md) — 一行摘要（≤150字符）"
		- 始终先检查MEMORY_INDEX.md（通过MemoryView）以避免重复记忆。
		- 不要保存：常识性信息、临时状态、已过时的事件细节、重复信息。

		何时使用（重要）：
		- 了解到用户的个人信息、偏好、习惯时 → 保存为 user 类型
		- 用户纠正了你的行为或表达了反馈时 → 保存为 feedback 类型
		- 发生了重要事件、决策、里程碑时 → 保存为 event 类型
		- 了解到外部联系人、资源、系统信息时 → 保存为 reference 类型
		- 发现用户有规律性的行为模式时 → 保存为 routine 类型
		- 学会了完成某任务的方法或步骤时 → 保存为 procedure 类型

		主动保存记忆是好的行为，不要等到用户要求。

		记忆文件前置元数据格式：
		  ---
		  name: <短名称>
		  description: <一行描述，用于在未来对话中判断相关性>
		  type: <user | feedback | event | reference | routine | procedure>
		  confidence: <0.0-1.0，默认1.0>
		  created: <创建日期>
		  updated: <最后更新日期>
		  entities: [<关联实体列表>]
		  ---
		  <记忆内容>

		对于feedback/event类型，正文结构为：
		  <规则或事实>
		  **原因：** <理由 —— 过去的事件、约束或偏好>
		  **如何应用：** <何时触发此规则>

		对于procedure类型，正文结构为：
		  <目标>
		  **步骤：** <1. ... 2. ... 3. ...>
		  **注意事项：** <需要注意的地方>
		""")
	public ToolResult memoryCreate(
		@ToolParam(description = "新文件的路径，相对于记忆根目录（例如'feedback_testing.md'）。使用反映主题的描述性名称。") String path,
		@ToolParam(description = "完整的文件内容，包括YAML前置元数据块和记忆正文。") String fileText) {

		try {
			Path target = resolveSafePath(path);

			if (Files.exists(target)) {
				return ToolResult.error("文件已存在: " + path + "。请使用MemoryStrReplace修改现有文件。");
			}

			Path parent = target.getParent();
			if (parent != null && !Files.exists(parent)) {
				Files.createDirectories(parent);
			}

			Files.writeString(target, fileText != null ? fileText : "", StandardCharsets.UTF_8);

			return ToolResult.success("成功创建文件: " + path + "（" + (fileText != null ? fileText.length() : 0) + "字节）");
		}
		catch (SecurityException e) {
			return ToolResult.error(e.getMessage());
		}
		catch (IOException e) {
			return ToolResult.error("创建文件时出错: " + e.getMessage());
		}
	}

	@Tool(name = "MemoryStrReplace", description = """
		替换现有记忆文件中的精确字符串。

		用法：
		- 所有路径相对于记忆根目录。
		- old_str必须精确匹配（包括空白和换行符）且必须只出现一次。
		- 如果old_str出现多次，编辑将被拒绝 —— 请包含更多周围上下文来消除歧义。
		- new_str可以为空以删除匹配的文本。
		- 返回编辑位置周围带行号的文件片段。

		何时使用（重要）：
		- 发现记忆内容过时时 → 更新为正确信息
		- 用户纠正了之前的偏好或反馈时 → 更新对应记忆
		- 需要更新MEMORY_INDEX.md索引时 → 编辑索引条目
		- 记忆的描述或名称需要修改时 → 更新前置元数据

		常见用途：
		- 更新记忆文件中过时的记忆内容（更改正文、更新前置元数据描述）。
		- 当记忆文件被重命名或其描述更改时，更新MEMORY_INDEX.md索引。
		- 删除记忆文件时从MEMORY_INDEX.md中移除条目（使用MemoryDelete删除文件本身）。
		- 编辑记忆内容后保持`name`和`description`前置元数据字段同步。
		""")
	public ToolResult memoryStrReplace(
		@ToolParam(description = "要编辑的文件路径，相对于记忆根目录。使用'MEMORY_INDEX.md'更新索引。") String path,
		@ToolParam(description = "要查找和替换的精确文本。必须在文件中只出现一次。") String oldStr,
		@ToolParam(description = "替换文本。使用空字符串删除匹配的文本。") String newStr) {

		try {
			Path target = resolveSafePath(path);

			if (!Files.exists(target)) {
				return ToolResult.error("文件不存在: " + path);
			}

			if (Files.isDirectory(target)) {
				return ToolResult.error("路径是目录，不是文件: " + path);
			}

			String content = Files.readString(target, StandardCharsets.UTF_8);
			int occurrences = countOccurrences(content, oldStr);

			if (occurrences == 0) {
				return ToolResult.error("在文件中未找到old_str: " + path);
			}

			if (occurrences > 1) {
				return ToolResult.error(String.format(
						"old_str在文件中出现%d次。请提供更多周围上下文使其唯一。",
						occurrences));
			}

			String replacement = newStr != null ? newStr : "";
			String updated = replaceFirst(content, oldStr, replacement);

			Files.writeString(target, updated, StandardCharsets.UTF_8);

			if (!StringUtils.hasText(replacement)) {
				return ToolResult.success(String.format("成功从%s中删除匹配文本。", path));
			}
			String snippet = generateEditSnippet(updated, replacement);
			return ToolResult.builder().status(ToolResult.Status.SUCCESS)
					.message(String.format("成功编辑%s", path))
					.rawOutput(String.format("成功编辑%s。以下是结果片段:\n%s", path, snippet))
					.build();
		}
		catch (SecurityException e) {
			return ToolResult.error(e.getMessage());
		}
		catch (IOException e) {
			return ToolResult.error("编辑文件时出错: " + e.getMessage());
		}
	}

	@Tool(name = "MemoryInsert", description = """
		在现有记忆文件的指定行号处插入文本。

		用法：
		- 所有路径相对于记忆根目录。
		- insert_line是新文本插入到其后的行号（0表示在开头插入）。
		- 行号从1开始。提供等于总行数的insert_line将追加到末尾。
		- 插入的文本应以换行符结尾，以使其显示为单独的行。

		常见用途：
		- 创建记忆文件后向MEMORY_INDEX.md追加新的指针行（两步保存的第2步）。
		  MEMORY_INDEX.md条目格式："- [标题](文件名.md) — 一行摘要（≤150字符）"
		  先通过MemoryView读取MEMORY_INDEX.md获取当前行数，然后在最后一行追加。
		- 在记忆文件中插入新节而不替换现有内容。
		""")
	public ToolResult memoryInsert(
		@ToolParam(description = "要修改的文件路径，相对于记忆根目录。使用'MEMORY_INDEX.md'追加索引条目。") String path,
		@ToolParam(description = "在其后插入文本的行号。使用0在第一行之前插入。传入总行数以追加到末尾。") Integer insertLine,
		@ToolParam(description = "要插入的文本。对于MEMORY_INDEX.md条目使用：'- [标题](文件名.md) — 一行摘要'") String insertText) {

		try {
			Path target = resolveSafePath(path);

			if (!Files.exists(target)) {
				return ToolResult.error("文件不存在: " + path);
			}

			if (Files.isDirectory(target)) {
				return ToolResult.error("路径是目录，不是文件: " + path);
			}

			if (insertLine == null || insertLine < 0) {
				return ToolResult.error("insert_line必须是非负整数");
			}

			List<String> lines = Files.readAllLines(target, StandardCharsets.UTF_8);

			if (insertLine > lines.size()) {
				return ToolResult.error(String.format("insert_line %d超过文件长度%d行", insertLine, lines.size()));
			}

			String originalContent = Files.readString(target, StandardCharsets.UTF_8);
			boolean trailingNewline = originalContent.endsWith("\n");

			lines.add(insertLine, insertText != null ? insertText : "");

			String updated = String.join("\n", lines) + (trailingNewline ? "\n" : "");
			Files.writeString(target, updated, StandardCharsets.UTF_8);

			return ToolResult.success("成功在" + path + "的第" + insertLine + "行插入文本");
		}
		catch (SecurityException e) {
			return ToolResult.error(e.getMessage());
		}
		catch (IOException e) {
			return ToolResult.error("插入文件时出错: " + e.getMessage());
		}
	}

	@Tool(name = "MemoryDelete", description = """
		从记忆存储中删除文件或目录（及其所有内容）。

		用法：
		- 所有路径相对于记忆根目录。
		- 删除目录会递归删除其中的所有文件和子目录。
		- 此操作不可逆；请谨慎使用。
		- 记忆根目录本身不能被删除。
		- 删除记忆文件后，始终使用MemoryStrReplace从MEMORY_INDEX.md中移除相应的条目以保持索引准确。
		- 当记忆确认过时、错误或已被取代时使用 —— 不要只留下过时的条目。

		何时使用（重要）：
		- 记忆内容确认过时且不再相关时 → 删除过时记忆
		- 记忆内容有误且无法通过编辑修正时 → 删除错误记忆
		- 记忆被其他更好的记忆取代时 → 删除被取代的记忆
		""")
	public ToolResult memoryDelete(
		@ToolParam(description = "要删除的文件或目录路径，相对于记忆根目录。删除后记得也移除MEMORY_INDEX.md中的条目。") String path) {

		try {
			Path target = resolveSafePath(path);

			if (target.equals(this.memoriesDir)) {
				return ToolResult.error("不能删除记忆根目录。");
			}

			if (!Files.exists(target)) {
				return ToolResult.error("路径不存在: " + path);
			}

			if (Files.isDirectory(target)) {
				try (Stream<Path> walk = Files.walk(target)) {
					walk.sorted(Comparator.reverseOrder()).forEach(p -> {
						try {
							Files.delete(p);
						}
						catch (IOException e) {
							throw new RuntimeException("删除失败: " + p, e);
						}
					});
				}
				return ToolResult.success("成功删除目录: " + path);
			}
			else {
				Files.delete(target);
				return ToolResult.success("成功删除文件: " + path);
			}
		}
		catch (SecurityException e) {
			return ToolResult.error(e.getMessage());
		}
		catch (RuntimeException | IOException e) {
			return ToolResult.error("删除路径时出错: " + e.getMessage());
		}
    }

	@Tool(name = "MemoryRename", description = """
		在记忆存储中重命名或移动文件或目录。

		用法：
		- 两个路径都相对于记忆根目录。
		- 源路径必须存在；目标路径必须不存在。
		- 目标的父目录如果不存在会自动创建。
		- 可用于通过在子目录之间移动文件来重新组织记忆。
		- 重命名记忆文件后，使用MemoryStrReplace更新MEMORY_INDEX.md中的指针以保持索引链接正确。
		""")
	public ToolResult memoryRename(
		@ToolParam(description = "文件或目录的当前路径，相对于记忆根目录。") String oldPath,
		@ToolParam(description = "文件或目录的新路径，相对于记忆根目录。记得之后更新MEMORY_INDEX.md链接。") String newPath) {

		try {
			Path source = resolveSafePath(oldPath);
			Path destination = resolveSafePath(newPath);

			if (!Files.exists(source)) {
				return ToolResult.error("源路径不存在: " + oldPath);
			}

			if (Files.exists(destination)) {
				return ToolResult.error("目标路径已存在: " + newPath);
			}

			Path destParent = destination.getParent();
			if (destParent != null && !Files.exists(destParent)) {
				Files.createDirectories(destParent);
			}

			Files.move(source, destination);

			return ToolResult.success(String.format("成功将'%s'重命名为'%s'", oldPath, newPath));
		}
		catch (SecurityException e) {
			return ToolResult.error(e.getMessage());
		}
		catch (IOException e) {
			return ToolResult.error("重命名路径时出错: " + e.getMessage());
		}
	}

	private Path resolveSafePath(String relativePath) {
		if (!StringUtils.hasText(relativePath) || relativePath.equals("/")) {
			return this.memoriesDir;
		}
		Path userPath = Paths.get(relativePath);
		if (userPath.isAbsolute()) {
			throw SecurityViolationException.absolutePath(relativePath);
		}
		Path resolved = this.memoriesDir.resolve(userPath).normalize();
		if (!resolved.startsWith(this.memoriesDir)) {
			throw SecurityViolationException.pathTraversal(relativePath);
		}
		return resolved;
	}

	private String listDirectory(Path dir, String displayPath) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(displayPath.isEmpty() ? "/" : displayPath).append("的内容:\n\n");

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
								sb.append("    ").append(subName).append("（").append(size).append("字节）\n");
							}
						}
					}
				}
				else {
					long size = Files.size(entry);
					sb.append("  ").append(name).append("（").append(size).append("字节）\n");
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
					return "错误：view_range必须是'start,end'整数（例如'1,50'）";
				}
			}
			else {
				return "错误：view_range必须是'start,end'格式（例如'1,50'）";
			}
		}

		StringBuilder sb = new StringBuilder();
		sb.append(String.format("文件: %s\n第%d-%d行，共%d行\n\n", file.getFileName(), startLine, endLine,
				totalLines));

		for (int i = startLine - 1; i < endLine; i++) {
			sb.append(String.format("%6d\t%s\n", i + 1, allLines.get(i)));
		}

		return sb.toString();
	}

	private int countOccurrences(String text, String substring) {
		return ToolUtils.countOccurrences(text, substring);
	}

	private String replaceFirst(String text, String oldStr, String newStr) {
		return ToolUtils.replaceFirst(text, oldStr, newStr);
	}

	private String generateEditSnippet(String fileContent, String newStr) {
		return ToolUtils.generateEditSnippet(fileContent, newStr);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private Path memoriesDir = Paths.get("/memories");

		private Builder() {
		}

		/**
		 * 设置所有记忆文件存储的根目录。默认为{@code /memories}。
		 * @param memoriesDir 记忆根目录
		 * @return 此构建器
		 */
		public Builder memoriesDir(Path memoriesDir) {
			this.memoriesDir = memoriesDir;
			return this;
		}

		/**
		 * 使用字符串路径设置记忆文件存储的根目录。
		 * @param memoriesDir 记忆根目录字符串路径
		 * @return 此构建器
		 */
		public Builder memoriesDir(String memoriesDir) {
			this.memoriesDir = Paths.get(memoriesDir);
			return this;
		}

		public AutoMemoryTools build() {
			try {
				Files.createDirectories(memoriesDir);
			}
			catch (IOException e) {
				throw new IllegalStateException("创建记忆目录失败: " + memoriesDir, e);
			}
			return new AutoMemoryTools(memoriesDir);
		}

	}

}
