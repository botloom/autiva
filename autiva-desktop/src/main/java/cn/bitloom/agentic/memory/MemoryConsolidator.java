package cn.bitloom.agentic.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;

import org.springframework.ai.chat.client.ChatClientRequest;

/**
 * 记忆自动整理器（对标 learn-claude-code s09 consolidation）。
 *
 * <p>当记忆文件数达到阈值时，异步执行一次整理：
 * <ol>
 *   <li>快照当前全部记忆文件与 MEMORY.md 到内存</li>
 *   <li>轻量模型生成整理后列表（合并语义重复、删除过时条目、重组命名）</li>
 *   <li>写新文件 → 删除不在新列表中的旧文件 → 从 frontmatter 重建 MEMORY.md 索引</li>
 *   <li>任何步骤失败：从快照恢复全部原文件</li>
 * </ol>
 *
 * <p>同时提供 {@link #triggerWhen(AgentMemoryStore, int)} 给
 * {@code AutoMemoryToolsAdvisor.memoryConsolidationTrigger} 接上真实条件（文件数 ≥ 阈值）。
 */
public final class MemoryConsolidator {

	private static final Logger logger = LoggerFactory.getLogger(MemoryConsolidator.class);

	public static final String MEMORY_INDEX_FILE = "MEMORY.md";

	public static final int DEFAULT_THRESHOLD = 10;

	/** 整理防重入标志（同一 store 同时只允许一次整理） */
	private static final AtomicBoolean consolidating = new AtomicBoolean(false);

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private MemoryConsolidator() {
	}

	/**
	 * 给 AutoMemoryToolsAdvisor 的整合触发器：记忆文件数 ≥ threshold 时返回 true
	 * （注入 reminder 文本，提示 LLM 记忆正在整理/可整理）。
	 */
	public static BiPredicate<ChatClientRequest, Instant> triggerWhen(AgentMemoryStore store, int threshold) {
		return (request, instant) -> {
			try {
				return countMemoryFiles(store) >= threshold;
			}
			catch (Exception e) {
				return false;
			}
		};
	}

	/**
	 * 条件整理：文件数 ≥ {@code threshold} 时执行 {@link #consolidate}，否则直接返回。
	 */
	public static void maybeConsolidate(AgentMemoryStore store, ChatClient chatClient, int threshold) {
		try {
			if (countMemoryFiles(store) < threshold) {
				return;
			}
		}
		catch (Exception e) {
			logger.debug("[MemoryConsolidator] 统计记忆文件数失败: {}", e.getMessage());
			return;
		}
		consolidate(store, chatClient);
	}

	/**
	 * 执行一次整理（快照 → LLM 重组 → 原子替换 → 失败恢复）。
	 */
	public static void consolidate(AgentMemoryStore store, ChatClient chatClient) {
		if (!consolidating.compareAndSet(false, true)) {
			logger.debug("[MemoryConsolidator] 整理进行中，跳过本次触发");
			return;
		}
		try {
			Map<String, String> snapshot = snapshot(store);
			if (snapshot.isEmpty()) {
				return;
			}
			List<MemoryFile> consolidated = generatePlan(chatClient, snapshot);
			if (consolidated.isEmpty()) {
				logger.info("[MemoryConsolidator] 模型判定无需整理");
				return;
			}
			apply(store, snapshot, consolidated);
			logger.info("[MemoryConsolidator] 整理完成: {} 个文件 → {} 个文件", snapshot.size() - 1, consolidated.size());
		}
		catch (Exception e) {
			logger.warn("[MemoryConsolidator] 整理失败: {}", e.getMessage());
		}
		finally {
			consolidating.set(false);
		}
	}

	private static int countMemoryFiles(AgentMemoryStore store) throws IOException {
		return (int) store.list("").stream()
			.filter(e -> !e.directory() && e.name().endsWith(".md") && !e.name().equals(MEMORY_INDEX_FILE))
			.count();
	}

	/** 快照：所有 .md 文件（含 MEMORY.md）的相对路径 → 内容 */
	private static Map<String, String> snapshot(AgentMemoryStore store) throws IOException {
		Map<String, String> snapshot = new LinkedHashMap<>();
		for (AgentMemoryStore.Entry entry : store.list("")) {
			if (entry.directory() || !entry.name().endsWith(".md")) {
				continue;
			}
			snapshot.put(entry.name(), store.readFile(entry.name()));
		}
		return snapshot;
	}

	private record MemoryFile(String path, String content) {
	}

	/**
	 * LLM 生成整理后文件列表。输入全部记忆（不含 MEMORY.md 索引），
	 * 输出 JSON 数组 [{path, content}]，content 为完整文件文本（含 frontmatter）。
	 */
	private static List<MemoryFile> generatePlan(ChatClient chatClient, Map<String, String> snapshot) {
		StringBuilder input = new StringBuilder();
		snapshot.forEach((path, content) -> {
			if (MEMORY_INDEX_FILE.equals(path)) {
				return;
			}
			input.append("<文件 path=\"").append(path).append("\">\n").append(content).append("\n</文件>\n\n");
		});

		String prompt = """
				你是长期记忆整理器。请整理下列记忆文件：合并语义重复的条目、删除过时或无保留价值的条目、
				保持每个文件一个主题。保留所有仍然有效的信息，不要丢失内容。

				%s
				规则：
				1. 输出整理后的完整文件列表，每个文件保持 YAML frontmatter（name/description/type）+ 正文格式
				2. 文件名使用小写加下划线（如 user_preferences.md）
			 3. 与输入完全相同、无需任何改动时返回 []
				4. 只输出 JSON 数组 [{"path":"xxx.md","content":"完整文件内容"}]，不要任何其它内容
				""".formatted(input);

		String content = chatClient.prompt().user(prompt).call().content();
		try {
			int start = content.indexOf('[');
			int end = content.lastIndexOf(']');
			if (start < 0 || end <= start) {
				return List.of();
			}
			List<MemoryFile> files = OBJECT_MAPPER.readValue(content.substring(start, end + 1),
					new TypeReference<List<MemoryFile>>() {
					});
			return files.stream()
				.filter(f -> f.path() != null && f.path().endsWith(".md") && !f.path().equals(MEMORY_INDEX_FILE)
						&& f.content() != null && !f.content().isBlank())
				.toList();
		}
		catch (Exception e) {
			logger.warn("[MemoryConsolidator] 解析整理结果失败: {}", e.getMessage());
			return List.of();
		}
	}

	/** 应用整理结果；任何失败从快照恢复 */
	private static void apply(AgentMemoryStore store, Map<String, String> snapshot, List<MemoryFile> consolidated) {
		try {
			// 1. 写全部新文件
			for (MemoryFile file : consolidated) {
				store.writeFile(file.path(), file.content());
			}
			// 2. 删除不在新列表中的旧文件（不含 MEMORY.md，稍后重建）
			List<String> newPaths = consolidated.stream().map(MemoryFile::path).toList();
			for (String oldPath : snapshot.keySet()) {
				if (!oldPath.equals(MEMORY_INDEX_FILE) && !newPaths.contains(oldPath)) {
					store.delete(oldPath);
				}
			}
			// 3. 从 frontmatter 重建 MEMORY.md
			store.writeFile(MEMORY_INDEX_FILE, rebuildIndex(consolidated));
		}
		catch (Exception e) {
			logger.warn("[MemoryConsolidator] 应用整理结果失败，从快照恢复: {}", e.getMessage());
			restore(store, snapshot);
		}
	}

	/** 从快照恢复：重写快照中的所有文件，删除快照外的整理残留 */
	private static void restore(AgentMemoryStore store, Map<String, String> snapshot) {
		try {
			for (Map.Entry<String, String> entry : snapshot.entrySet()) {
				store.writeFile(entry.getKey(), entry.getValue());
			}
			for (AgentMemoryStore.Entry entry : store.list("")) {
				if (entry.name().endsWith(".md") && !snapshot.containsKey(entry.name())) {
					store.delete(entry.name());
				}
			}
			logger.info("[MemoryConsolidator] 快照恢复完成（{} 个文件）", snapshot.size());
		}
		catch (Exception e) {
			logger.error("[MemoryConsolidator] 快照恢复失败，记忆目录可能不完整: {}", e.getMessage());
		}
	}

	/**
	 * 从整理后文件的 frontmatter 重建 MEMORY.md 索引。
	 * 索引行格式与 MemoryInsert 工具约定一致：- [标题](filename.md) — 一行钩子
	 */
	private static String rebuildIndex(List<MemoryFile> files) {
		StringBuilder sb = new StringBuilder();
		sb.append("# 记忆\n\n");
		sb.append("<!-- 使用 memory_save 创建新记忆文件，memory_insert 向本文件添加索引条目 -->\n");
		sb.append("<!-- 格式：- [标题](filename.md) — 一行钩子（≤150字符） -->\n");
		for (MemoryFile file : files) {
			String name = frontmatterValue(file.content(), "name");
			String description = frontmatterValue(file.content(), "description");
			String title = name != null ? name : file.path().replace(".md", "");
			String hook = description != null ? description : "";
			sb.append("- [").append(title).append("](").append(file.path()).append(") — ").append(hook).append('\n');
		}
		return sb.toString();
	}

	/** 从 YAML frontmatter 中提取字段值（简单行匹配，不引入完整 YAML 解析） */
	static String frontmatterValue(String content, String field) {
		String[] lines = content.split("\n", 20);
		boolean inFrontmatter = false;
		for (String line : lines) {
			if (line.trim().equals("---")) {
				if (inFrontmatter) {
					break;
				}
				inFrontmatter = true;
				continue;
			}
			if (inFrontmatter && line.startsWith(field + ":")) {
				String value = line.substring(field.length() + 1).trim();
				// 去掉可能的引号
				if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
					value = value.substring(1, value.length() - 1);
				}
				return value;
			}
		}
		return null;
	}

	/** 供 MemoryExtractionHook 复用：构建与工具写入一致的记忆文件文本 */
	public static String buildMemoryFileContent(String name, String description, String type, String body) {
		return """
				---
				name: %s
				description: %s
				type: %s
				---

				%s
				""".formatted(name, description, type, body);
	}

	/** 供 MemoryExtractionHook 复用：构建索引行 */
	public static String buildIndexLine(String title, String filename, String hook) {
		return "- [" + title + "](" + filename + ") — " + hook;
	}
}
