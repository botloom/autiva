package cn.bitloom.agentic.hook;

import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.memory.AgentMemoryStore;
import cn.bitloom.agentic.memory.MemoryConsolidator;
import cn.bitloom.agentic.session.EventFilter;
import cn.bitloom.agentic.session.ISessionManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 回合结束记忆提取 Hook（对标 learn-claude-code s09 memory extraction）。
 *
 * <p>每轮对话结束后异步执行（不阻塞 UI 与下一轮）：轻量模型审查本轮对话
 * （用户消息 + 助手回复 + 关键工具结果），产出候选记忆
 * {@code {name, description, type, content, scope}}，经写入前校验后落盘并追加 MEMORY.md 索引。
 *
 * <p>写入前校验（should_store）：
 * <ul>
 *   <li>scope != persistent 拒绝（current_task 类临时信息不进长期记忆）</li>
 *   <li>name/description/content 字段不完整拒绝</li>
 *   <li>与 MEMORY.md 现有条目语义重复拒绝（轻量模型二次判定）</li>
 *   <li>"本次不要创建文件"类临时指令拒绝</li>
 * </ul>
 *
 * <p>频率控制：每轮都审查但设置最短间隔（默认 5 分钟）防抖；
 * 提取完成后触发 {@link MemoryConsolidator#maybeConsolidate} 检查自动整理条件。
 */
public final class MemoryExtractionHook implements IAgentHook {

	private static final Logger logger = LoggerFactory.getLogger(MemoryExtractionHook.class);

	public static final long DEFAULT_MIN_INTERVAL_MILLIS = 5 * 60 * 1000L;

	/** 提交给模型的单轮对话最大事件数 */
	static final int MAX_ROUND_EVENTS = 40;

	/** 单条记忆正文最大字符数 */
	static final int MAX_CONTENT_CHARS = 2000;

	private static final Set<String> VALID_TYPES = Set.of("user", "feedback", "project", "reference");

	/** 防抖：per 记忆根目录（一个 store 一个根）的上次提取时间 */
	private static final Map<String, Long> LAST_EXTRACTION_AT = new ConcurrentHashMap<>();

	private final ISessionManager sessionManager;

	private final AgentMemoryStore memoryStore;

	private final ChatClient chatClient;

	private final long minIntervalMillis;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private MemoryExtractionHook(ISessionManager sessionManager, AgentMemoryStore memoryStore, ChatClient chatClient,
			long minIntervalMillis) {
		this.sessionManager = sessionManager;
		this.memoryStore = memoryStore;
		this.chatClient = chatClient;
		this.minIntervalMillis = minIntervalMillis;
	}

	@Override
	public String name() {
		return "MemoryExtraction";
	}

	@Override
	public int order() {
		return 40;
	}

	@Override
	public void afterConversationRound(RuntimeContext ctx) {
		String sessionId = ctx.getSessionId();
		if (sessionId == null) {
			return;
		}
		// 防抖
		String storeKey = memoryStore.toString();
		long now = System.currentTimeMillis();
		Long last = LAST_EXTRACTION_AT.get(storeKey);
		if (last != null && now - last < minIntervalMillis) {
			return;
		}
		LAST_EXTRACTION_AT.put(storeKey, now);

		// 异步执行，不阻塞回合结束回调
		CompletableFuture.runAsync(() -> {
			try {
				extract(sessionId);
			}
			catch (Exception e) {
				logger.debug("[MemoryExtraction] 提取失败，跳过: {}", e.getMessage());
			}
			// 提取后检查自动整理条件
			try {
				MemoryConsolidator.maybeConsolidate(memoryStore, chatClient, MemoryConsolidator.DEFAULT_THRESHOLD);
			}
			catch (Exception e) {
				logger.debug("[MemoryExtraction] 自动整理检查失败: {}", e.getMessage());
			}
		});
	}

	/**
	 * 提取主流程：读本轮对话 → LLM 产出候选 → 逐条校验 → 落盘 + 更新索引。
	 */
	private void extract(String sessionId) {
		List<MessageEvent> roundEvents = readCurrentRound(sessionId);
		if (roundEvents.isEmpty()) {
			return;
		}
		List<Candidate> candidates = generateCandidates(formatRound(roundEvents));
		if (candidates.isEmpty()) {
			return;
		}
		int stored = 0;
		for (Candidate candidate : candidates) {
			if (!shouldStore(candidate)) {
				continue;
			}
			try {
				store(candidate);
				stored++;
			}
			catch (Exception e) {
				logger.debug("[MemoryExtraction] 写入记忆 {} 失败: {}", candidate.name(), e.getMessage());
			}
		}
		if (stored > 0) {
			logger.info("[MemoryExtraction] 本轮提取并保存 {} 条长期记忆（候选 {} 条）", stored, candidates.size());
		}
	}

	/**
	 * 读取本轮对话：最后一条用户消息（root 可见）及其后的所有事件。
	 */
	private List<MessageEvent> readCurrentRound(String sessionId) {
		// EventFilter.active() 的 branch == null 语义：仅 root 事件（主智能体视角）
		List<cn.bitloom.agentic.event.AbstractEvent> all = sessionManager.getEvents(sessionId,
				EventFilter.active());
		int lastUserIdx = -1;
		for (int i = all.size() - 1; i >= 0; i--) {
			if (all.get(i) instanceof MessageEvent me && me.isUserMessage()) {
				lastUserIdx = i;
				break;
			}
		}
		if (lastUserIdx < 0) {
			return List.of();
		}
		List<MessageEvent> round = new ArrayList<>();
		for (int i = lastUserIdx; i < all.size() && round.size() < MAX_ROUND_EVENTS; i++) {
			if (all.get(i) instanceof MessageEvent me) {
				round.add(me);
			}
		}
		return round;
	}

	private String formatRound(List<MessageEvent> events) {
		StringBuilder sb = new StringBuilder();
		for (MessageEvent event : events) {
			String role = switch (event.getMessageType()) {
				case USER -> "用户";
				case ASSISTANT -> "助手";
				case TOOL -> "工具结果";
				default -> null;
			};
			if (role == null) {
				continue;
			}
			String text = event.getText();
			if (event.isToolResponse() && event.getResponses() != null) {
				text = event.getResponses()
					.stream()
					.map(r -> r.name() + ": " + (r.responseData() != null ? r.responseData() : ""))
					.reduce("", (a, b) -> a + b);
			}
			if (text == null || text.isBlank()) {
				continue;
			}
			sb.append(role).append(": ").append(truncate(text, 3000)).append('\n');
		}
		return sb.toString();
	}

	private record Candidate(String name, String description, String type, String content, String scope) {
	}

	/**
	 * LLM 产出候选记忆列表。
	 */
	private List<Candidate> generateCandidates(String roundText) {
		String index = readIndex();
		String prompt = """
				你是长期记忆提取器。审查下列对话回合，提取<b>值得长期保存</b>的信息作为记忆。

				<本轮对话>
				%s
				</本轮对话>

				<现有记忆索引（避免重复）>
				%s
				</现有记忆索引>

				规则：
				1. 只提取跨对话仍然有效的信息：用户偏好/背景（type=user）、用户反馈的规则/纠正（type=feedback）、
				   项目事实/技术栈（type=project）、常用参考信息（type=reference）
				2. 不保存：代码内容、一次性任务细节、"本次不要创建文件"类临时指令、已在现有索引中的重复信息
				3. scope 取值：persistent（跨对话长期有效）或 current_task（仅本次任务有效）
				4. 没有值得保存的就返回 []
				5. 只输出 JSON 数组
				[{"name":"简短标识","description":"一行描述","type":"user|feedback|project|reference","content":"记忆正文","scope":"persistent|current_task"}]
				不要任何其它内容
				""".formatted(truncate(roundText, 24000), index != null ? truncate(index, 4000) : "（空）");

		String content = chatClient.prompt().user(prompt).call().content();
		try {
			int start = content.indexOf('[');
			int end = content.lastIndexOf(']');
			if (start < 0 || end <= start) {
				return List.of();
			}
			List<Candidate> candidates = objectMapper.readValue(content.substring(start, end + 1),
					new TypeReference<List<Candidate>>() {
					});
			return candidates != null ? candidates : List.of();
		}
		catch (Exception e) {
			logger.debug("[MemoryExtraction] 解析候选失败: {}", e.getMessage());
			return List.of();
		}
	}

	private String readIndex() {
		try {
			return memoryStore.exists(MemoryConsolidator.MEMORY_INDEX_FILE)
					? memoryStore.readFile(MemoryConsolidator.MEMORY_INDEX_FILE)
					: null;
		}
		catch (Exception e) {
			return null;
		}
	}

	/**
	 * 写入前校验（should_store）。
	 */
	private boolean shouldStore(Candidate candidate) {
		// 1. 字段完整性 + scope 校验
		if (candidate == null || isBlank(candidate.name()) || isBlank(candidate.description())
				|| isBlank(candidate.content()) || candidate.content().length() > MAX_CONTENT_CHARS * 2) {
			return false;
		}
		if (!"persistent".equals(candidate.scope())) {
			return false;
		}
		if (!VALID_TYPES.contains(candidate.type())) {
			return false;
		}
		String filename = filename(candidate);
		// 2. 文件名冲突拒绝
		try {
			if (memoryStore.exists(filename)) {
				return false;
			}
		}
		catch (Exception e) {
			return false;
		}
		// 3. 语义重复拒绝（轻量模型二次判定）
		return !isSemanticallyDuplicate(candidate);
	}

	/**
	 * 轻量模型二次判定：候选与现有索引条目是否语义重复。
	 */
	private boolean isSemanticallyDuplicate(Candidate candidate) {
		String index = readIndex();
		if (index == null || !index.lines().anyMatch(l -> l.startsWith("- ["))) {
			return false;
		}
		try {
			String prompt = """
					判断新记忆是否与现有记忆索引中的任一条目语义重复（同一主题、同一规则或同一事实）。

					<新记忆>
					name: %s
					description: %s
					</新记忆>

					<现有记忆索引>
					%s
					</现有记忆索引>

					只输出 true（重复）或 false（不重复），不要任何其它内容。
					""".formatted(candidate.name(), candidate.description(), truncate(index, 4000));
			String content = chatClient.prompt().user(prompt).call().content();
			return content != null && content.trim().toLowerCase().startsWith("true");
		}
		catch (Exception e) {
			// 判定失败按不重复处理（宁多存不漏存，后续整理会合并）
			return false;
		}
	}

	/**
	 * 落盘：写记忆文件（与 MemoryCreate 工具同一格式）+ 追加 MEMORY.md 索引行。
	 */
	private void store(Candidate candidate) throws Exception {
		String filename = filename(candidate);
		String fileContent = MemoryConsolidator.buildMemoryFileContent(candidate.name(), candidate.description(),
				candidate.type(), truncate(candidate.content(), MAX_CONTENT_CHARS));
		memoryStore.createFile(filename, fileContent);

		String indexLine = MemoryConsolidator.buildIndexLine(candidate.name(), filename, candidate.description());
		try {
			List<String> lines = memoryStore.exists(MemoryConsolidator.MEMORY_INDEX_FILE)
					? new ArrayList<>(memoryStore.readLines(MemoryConsolidator.MEMORY_INDEX_FILE))
					: new ArrayList<>(List.of("# 记忆", "", "<!-- 格式：- [标题](filename.md) — 一行钩子（≤150字符） -->"));
			lines.add(indexLine);
			memoryStore.writeFile(MemoryConsolidator.MEMORY_INDEX_FILE, String.join("\n", lines));
		}
		catch (Exception e) {
			// 索引更新失败不影响记忆文件本身
			logger.debug("[MemoryExtraction] 更新 MEMORY.md 索引失败: {}", e.getMessage());
		}
	}

	private String filename(Candidate candidate) {
		String name = candidate.name().trim().toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "_");
		return name.endsWith(".md") ? name : name + ".md";
	}

	private boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	private String truncate(String text, int maxChars) {
		if (text == null) {
			return "";
		}
		return text.length() <= maxChars ? text : text.substring(0, maxChars) + "\n...(已截断)";
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private ISessionManager sessionManager;

		private AgentMemoryStore memoryStore;

		private ChatClient chatClient;

		private long minIntervalMillis = DEFAULT_MIN_INTERVAL_MILLIS;

		private Builder() {
		}

		public Builder sessionManager(ISessionManager sessionManager) {
			this.sessionManager = sessionManager;
			return this;
		}

		public Builder memoryStore(AgentMemoryStore memoryStore) {
			this.memoryStore = memoryStore;
			return this;
		}

		public Builder chatClient(ChatClient chatClient) {
			this.chatClient = chatClient;
			return this;
		}

		public Builder minIntervalMillis(long minIntervalMillis) {
			this.minIntervalMillis = minIntervalMillis;
			return this;
		}

		public MemoryExtractionHook build() {
			if (sessionManager == null || memoryStore == null || chatClient == null) {
				throw new IllegalStateException("sessionManager/memoryStore/chatClient 均不可为空");
			}
			return new MemoryExtractionHook(sessionManager, memoryStore, chatClient, minIntervalMillis);
		}
	}
}
