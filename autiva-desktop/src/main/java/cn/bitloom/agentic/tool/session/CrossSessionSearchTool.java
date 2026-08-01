/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.bitloom.agentic.tool.session;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.model.ToolContext;
import cn.bitloom.agentic.session.EventFilter;
import cn.bitloom.agentic.session.EventFilter.MatchMode;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.event.AbstractEvent;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.util.JsonUtils;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;
import cn.bitloom.agentic.session.ISessionManager;

/**
 * 跨会话搜索工具，用于维护/分析智能体搜索用户所有会话的历史记录。
 *
 * <p>
 * {@link ConversationSearchTool} 从实时请求的 {@code ToolContext} 精确解析一个会话 —
 * 这是会话智能体回答"我们在<em>本次</em>对话中讨论了什么"的正确范围。
 * 本类则搜索<strong>属于一个用户的所有会话</strong>，这是后台维护智能体（如 Auto-Dream 的 Dreamer）
 * 挖掘跨越许多过去对话的历史信号所需要的。
 *
 * <p>
 * 与 {@code conversation_search} 不同，目标用户在构建时通过
 * {@link #builder(ISessionManager, String)} 绑定，而不是作为工具调用参数接受 —
 * 行为不当的提示无法让此工具扫描其他用户的会话，因为模型永远无法选择搜索哪个用户。
 *
 * <p>
 * 只读：此类没有追加/压缩/删除能力。
 *
 * @author Christian Tzolov
 * @since 2.0
 */
public class CrossSessionSearchTool extends AbstractTool<CrossSessionSearchTool.Input> {

	private static final Logger logger = LoggerFactory.getLogger(CrossSessionSearchTool.class);

	/**
	 * {@code query} 中接受的逗号分隔术语数量的上限。
	 * 每个术语成为自己的 {@code LIKE} 谓词（在 JDBC 支持的
	 * {@code ISessionManager} 上，成为自己的绑定 SQL 参数） —
	 * 如果没有上限，行为不当或对抗性调用者的非常长的逗号分隔 {@code query}
	 * 会无限增长生成的查询和参数列表。这本身不是安全边界
	 * （每个术语仍然安全绑定，从不连接到 SQL 文本），
	 * 只是对单个搜索请求允许大小的合理性限制。
	 */
	static final int MAX_QUERY_TERMS = 20;

	private final ISessionManager sessionService;
	private final String userId;
	private final int pageSize;

	/**
	 * 输入参数 record。
	 */
	public record Input(
		@ToolParam(description = "仅对你可见的深度内心独白。") String innerThought,
		@ToolParam(description = "不区分大小写的关键词。提供多个逗号分隔的关键词一次搜索多个术语（每次调用最多 20 个术语）。") String query,
		@ToolParam(description = "'any'（默认）或 'all' — 'query' 中多个逗号分隔关键词如何组合。") String matchMode,
		@ToolParam(description = "ISO-8601 时间戳（如 '2026-07-01T00:00:00Z'）；仅考虑此时间之后的事件。省略搜索完整历史。") String since,
		@ToolParam(description = "要检索的结果页（0 索引）。省略或使用 0 获取第一页。") Integer page
	) {}

	private CrossSessionSearchTool(ISessionManager sessionService, String userId, int pageSize) {
		super("cross_session_search",
			  "跨用户的所有会话（不仅是当前会话）搜索匹配给定条件的事件。适用于需要跨越许多过去对话的历史信号的维护/分析智能体。",
			  Input.class);
		this.sessionService = sessionService;
		this.userId = userId;
		this.pageSize = pageSize;
	}

	@Override
	public @NonNull ToolResult execute(@NonNull Input input, @Nullable ToolContext context) {
		int pageNumber = (input.page() != null) ? Math.max(0, input.page()) : 0;

		logger.debug("[cross_session_search] userId: {}, innerThought: {}, query: {}, matchMode: {}, since: {}, page: {}",
				this.userId, input.innerThought(), input.query(), input.matchMode(), input.since(), pageNumber);

		EventFilter filter = buildFilter(input.query(), input.matchMode(), input.since());

		List<Session> sessions = this.sessionService.findByUserId(this.userId);

		List<MessageEvent> allMatches = new ArrayList<>();
		for (Session session : sessions) {
			for (AbstractEvent event : this.sessionService.getEvents(session.id(), filter)) {
				if (event instanceof MessageEvent me && StringUtils.hasText(me.getMessage().getText())) {
					allMatches.add(me);
				}
			}
		}

		// 按实际 Instant 排序，而不是其 String 渲染。Instant.toString()
		// 在小数秒恰好为零时省略该组件，否则包含，因此两个不同长度的渲染时间戳
		// 不能可靠地按时间顺序比较（例如 "...:00.001Z" < "...:00Z" 按字典顺序，
		// 即使前者瞬间更晚）。
		allMatches.sort(Comparator.comparing(MessageEvent::getTimestamp));

		// 在相乘之前转换为 long，以便大的 `page` 不会静默溢出 int
		// 算术并产生负索引 — 镜像 JdbcSessionRepository.findEvents 中
		// SQL OFFSET 参数已使用的相同保护。
		long fromIndexLong = (long) pageNumber * this.pageSize;
		int fromIndex = (int) Math.min(fromIndexLong, allMatches.size());
		int toIndex = (int) Math.min(fromIndexLong + this.pageSize, allMatches.size());
		List<MessageEvent> pageResults = allMatches.subList(fromIndex, toIndex);

		if (pageResults.isEmpty()) {
			return ToolResult.success("未找到结果。");
		}

		List<Map<String, String>> jsonResults = pageResults.stream()
			.map(event -> Map.of(
				"sessionId", event.getSessionId(),
				"timestamp", event.getTimestamp().toString(),
				"type", event.getMessageType().getValue(),
				"text", event.getMessage().getText()
			))
			.toList();

		return ToolResult.success(JsonUtils.toJson(jsonResults));
	}

	private EventFilter buildFilter(String query, String matchMode, String since) {
		EventFilter.Builder builder = EventFilter.builder();

		if (StringUtils.hasText(query)) {
			List<String> terms = List.of(query.split(","))
				.stream()
				.map(String::trim)
				.filter(StringUtils::hasText)
				.toList();
			if (terms.isEmpty()) {
				// 例如 query = "," 或 ", " — 有可见字符所以不是空白，
				// 但拆分为零个可用术语。静默回退到"无关键词过滤器"
				// 会将狭窄搜索变为"返回此用户的所有内容"，
				// 因此明确拒绝它。
				throw new IllegalArgumentException(
						"查询 '" + query + "' 在 ',' 分割后不包含可用的搜索术语");
			}
			if (terms.size() > MAX_QUERY_TERMS) {
				throw new IllegalArgumentException("查询提供了 " + terms.size() + " 个逗号分隔术语，"
						+ "超过最大值 " + MAX_QUERY_TERMS + " — 缩小搜索范围而不是"
						+ "将多个术语组合到单次调用中");
			}
			if (terms.size() > 1) {
				MatchMode mode = "all".equalsIgnoreCase(matchMode) ? MatchMode.ALL : MatchMode.ANY;
				builder.keywords(terms).matchMode(mode);
			}
			else {
				builder.keyword(terms.get(0));
			}
		}

		if (StringUtils.hasText(since)) {
			try {
				builder.from(Instant.parse(since));
			}
			catch (DateTimeParseException ex) {
				throw new IllegalArgumentException(
						"since '" + since + "' 不是有效的 ISO-8601 时间戳（例如 '2026-07-01T00:00:00Z'）", ex);
			}
		}

		return builder.build();
	}

	public static Builder builder(ISessionManager sessionService, String userId) {
		return new Builder(sessionService, userId);
	}

	public static final class Builder {

		private final ISessionManager sessionService;
		private final String userId;
		private int pageSize = EventFilter.DEFAULT_PAGE_SIZE;

		private Builder(ISessionManager sessionService, String userId) {
			if (sessionService == null) {
				throw new IllegalArgumentException("sessionService must not be null");
			}
			if (!StringUtils.hasText(userId)) {
				throw new IllegalArgumentException("userId must not be empty");
			}
			this.sessionService = sessionService;
			this.userId = userId;
		}

		/**
		 * 每页返回的结果数。默认为 {@link EventFilter#DEFAULT_PAGE_SIZE}。
		 */
		public Builder pageSize(int pageSize) {
			if (pageSize <= 0) {
				throw new IllegalArgumentException("pageSize must be positive");
			}
			this.pageSize = pageSize;
			return this;
		}

		public CrossSessionSearchTool build() {
			return new CrossSessionSearchTool(this.sessionService, this.userId, this.pageSize);
		}
	}
}