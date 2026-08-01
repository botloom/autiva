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

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.model.ToolContext;
import cn.bitloom.agentic.session.EventFilter;
import cn.bitloom.agentic.event.AbstractEvent;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.util.JsonUtils;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;
import cn.bitloom.agentic.session.ISessionManager;

/**
 * 对话搜索工具，用于搜索会话的对话历史（召回存储）。
 *
 * <p>
 * 镜像 MemGPT 的 {@code conversation_search} 工具：完整的逐字历史
 * 保留在会话事件日志中，始终可以通过关键词搜索，
 * 即使上下文压缩已从活动上下文窗口中删除了旧事件。
 *
 * @author Christian Tzolov
 * @since 2.0
 */
public class ConversationSearchTool extends AbstractTool<ConversationSearchTool.Input> {

	private static final Logger logger = LoggerFactory.getLogger(ConversationSearchTool.class);

	/**
	 * 用于从 {@link ToolContext} 解析 session ID 的上下文键。
	 */
	public static final String SESSION_ID_CONTEXT_KEY = "chat_memory_conversation_id";

	private final ISessionManager sessionService;
	private final int pageSize;

	/**
	 * 输入参数 record。
	 */
	public record Input(
		@ToolParam(description = "仅对你可见的深度内心独白。") String innerThought,
		@ToolParam(description = "要在对话历史中搜索的关键词。") String query,
		@ToolParam(description = "要检索的结果页（0 索引）。省略或使用 0 获取第一页。") Integer page
	) {}

	private ConversationSearchTool(ISessionManager sessionService, int pageSize) {
		super("conversation_search",
			  "使用不区分大小写的关键词匹配搜索完整的先前对话历史。返回按时间顺序分页的结果。",
			  Input.class);
		this.sessionService = sessionService;
		this.pageSize = pageSize;
	}

	@Override
	public @NonNull ToolResult execute(@NonNull Input input, @Nullable ToolContext toolContext) {
		int pageNumber = (input.page() != null) ? Math.max(0, input.page()) : 0;

		logger.debug("[conversation_search] innerThought: {}, query: {}, page: {}",
				input.innerThought(), input.query(), pageNumber);

		String sessionId = resolveSessionId(toolContext);

		List<AbstractEvent> events = this.sessionService.getEvents(sessionId,
				EventFilter.keywordSearch(input.query(), pageNumber, this.pageSize));

		List<Map<String, String>> results = events.stream()
			.filter(e -> e instanceof MessageEvent)
			.map(e -> (MessageEvent) e)
			.filter(e -> StringUtils.hasText(e.getMessage().getText()))
			.map(e -> Map.of(
				"timestamp", e.getTimestamp().toString(),
				"type", e.getMessageType().getValue(),
				"text", e.getMessage().getText()
			))
			.toList();

		if (results.isEmpty()) {
			return ToolResult.success("未找到结果。");
		}

		return ToolResult.success(JsonUtils.toJson(results));
	}

	private String resolveSessionId(ToolContext toolContext) {
		if (toolContext == null || toolContext.getContext() == null) {
			logger.warn("[conversation_search] ToolContext 为 null — 回退到 session ID 'default'。");
			return "default";
		}

		Object sessionIdValue = toolContext.getContext().get(SESSION_ID_CONTEXT_KEY);
		if (sessionIdValue instanceof String s && !s.isBlank()) {
			return s;
		}

		logger.warn("[conversation_search] ToolContext 中未找到 '{}' — 回退到 session ID 'default'。",
				SESSION_ID_CONTEXT_KEY);
		return "default";
	}

	public static Builder builder(ISessionManager sessionService) {
		return new Builder(sessionService);
	}

	public static final class Builder {

		private final ISessionManager sessionService;
		private int pageSize = EventFilter.DEFAULT_PAGE_SIZE;

		private Builder(ISessionManager sessionService) {
			if (sessionService == null) {
				throw new IllegalArgumentException("sessionService must not be null");
			}
			this.sessionService = sessionService;
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

		public ConversationSearchTool build() {
			return new ConversationSearchTool(this.sessionService, this.pageSize);
		}
	}
}