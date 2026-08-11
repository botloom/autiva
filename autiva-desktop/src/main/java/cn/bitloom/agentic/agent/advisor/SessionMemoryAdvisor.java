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

package cn.bitloom.agentic.agent.advisor;

import cn.bitloom.agentic.event.AbstractEvent;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.session.*;
import cn.bitloom.agentic.session.compaction.CompactionStrategy;
import cn.bitloom.agentic.session.compaction.CompactionTrigger;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.MemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 会话记忆 Advisor，使用 {@link ISessionManager} 管理对话历史，支持可选的上下文压缩。
 *
 * <p>
 * 每次交互：
 * <ol>
 * <li>从会话中检索事件历史并追加到提示词消息中。</li>
 * <li>将当前用户消息追加到会话（如果通过配置的 {@link MessageFilter}）。</li>
 * <li>模型响应后，将助手消息追加到会话；被 {@link MessageFilter} 拒绝的消息被跳过。
 * 默认情况下，空助手消息（空白文本、无工具调用、无媒体）被过滤。</li>
 * <li>如果配置的触发器触发，可选触发上下文压缩。</li>
 * </ol>
 *
 * <p>
 * 会话通过 advisor 上下文中的 {@link #SESSION_ID_CONTEXT_KEY} 值标识。
 * 每个请求必须包含此键；缺少会抛出 {@link IllegalStateException} 以防止意外跨用户会话共享。
 *
 * <p>
 * <strong>并发压缩安全：</strong> 如果同一会话的两个请求并发完成，两个 {@code after()} 调用可能同时到达压缩步骤。
 * 压缩使用乐观 compare-and-swap 写入通过
 * {@link cn.bitloom.agentic.session.SessionRepository#compactEvents(String, java.util.List, java.util.List, long)}，
 * 因此只有第一个写入者成功；第二个检测到版本不匹配并静默跳过。
 * 没有压缩结果丢失或损坏。
 *
 * <p>
 * <strong>事件 ID 生成：</strong> 默认情况下，每个持久化事件获得新的随机 ID
 * （{@link SessionEventRequestIdGenerator#random()} /
 * {@link SessionEventResponseIdGenerator#random()}），因此重试追加总是新事件。
 * 使用确定性派生（例如内容寻址，或重用上游持久化层的幂等键）配置
 * {@link Builder#requestEventIdGenerator} / {@link Builder#responseEventIdGenerator}，
 * 通过 {@code SessionRepository.appendEvent} 的基于 ID 的重放契约使重试追加成为幂等无操作。
 *
 * @author Christian Tzolov
 * @since 2.0.0
 */
public final class SessionMemoryAdvisor implements BaseAdvisor, MemoryAdvisor {

	private static final Logger logger = LoggerFactory.getLogger(SessionMemoryAdvisor.class);

	/**
	 * 用于将 session ID 传递到 advisor 每个请求的上下文键。
	 * 等于 {@link org.springframework.ai.chat.memory.ChatMemory#CONVERSATION_ID}，
	 * 以便此 advisor 使用与 Spring AI 内存 API 其余部分相同的上下文键。
	 * 通过：{@code .advisors(a -> a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, "my-session-id"))}
	 */
	public static final String SESSION_ID_CONTEXT_KEY = ChatMemory.CONVERSATION_ID;

	/**
	 * 用于将 user ID 传递到 advisor 每个请求的上下文键。
	 * 通过：{@code .advisors(a -> a.param(SessionMemoryAdvisor.USER_ID_CONTEXT_KEY, "my-user-id"))}
	 */
	public static final String USER_ID_CONTEXT_KEY = "chat_memory_user_id";

	public static final String EVENT_FILTER_CONTEXT_KEY = "chat_memory_event_filter_id";

	/**
	 * 用于将 branch 传递到 advisor 每个请求的上下文键。
	 * 子智能体通过 branch 隔离事件：主智能体为 null（root），子智能体形如 "subagent.{name}"。
	 * 持久化事件时写入此 branch，检索历史时配合 {@link EventFilter#forBranch(String)} 过滤。
	 */
	public static final String BRANCH_CONTEXT_KEY = "chat_memory_branch";

	private final ISessionManager sessionService;

	private final String defaultUserId;

	private final int order;

	private final Scheduler scheduler;

	private final EventFilter eventFilter;

	private final MessageFilter messageFilter;

	private final SessionEventRequestIdGenerator requestEventIdGenerator;

	private final SessionEventResponseIdGenerator responseEventIdGenerator;

	@Nullable private final CompactionTrigger compactionTrigger;

	@Nullable private final CompactionStrategy compactionStrategy;

	private SessionMemoryAdvisor(ISessionManager sessionService, String defaultUserId, int order, Scheduler scheduler,
			EventFilter eventFilter, MessageFilter messageFilter, SessionEventRequestIdGenerator requestEventIdGenerator,
			SessionEventResponseIdGenerator responseEventIdGenerator, @Nullable CompactionTrigger compactionTrigger,
			@Nullable CompactionStrategy compactionStrategy) {
		this.sessionService = sessionService;
		this.defaultUserId = defaultUserId;
		this.order = order;
		this.scheduler = scheduler;
		this.eventFilter = eventFilter;
		this.messageFilter = messageFilter;
		this.requestEventIdGenerator = requestEventIdGenerator;
		this.responseEventIdGenerator = responseEventIdGenerator;
		this.compactionTrigger = compactionTrigger;
		this.compactionStrategy = compactionStrategy;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public @NonNull Scheduler getScheduler() {
		return this.scheduler;
	}

	@Override
	public @NonNull ChatClientRequest before(ChatClientRequest request, @NonNull AdvisorChain advisorChain) {

		// 0. 解析 session ID — 必须存在于请求上下文中
		String sessionId = getSessionId(request.context());

		// 1. 查找或创建会话。Session 对象缓存在请求上下文中，
		// 以便 after() 可以重用它并在配置压缩时跳过冗余的 findById() 存储往返
		Session session = this.sessionService.getById(sessionId);
		if (session == null) {
			String userId = getUserId(request.context());
			session = this.sessionService.create(CreateSessionRequest.builder().id(sessionId).userId(userId).build());
		}
		else {
			// 当调用者通过 USER_ID_CONTEXT_KEY 显式标识用户时强制所有权。
			// 当没有每个请求的用户 ID 时跳过，以便仅依赖 defaultUserId 的调用者不会中断
			Object userIdValue = request.context().get(USER_ID_CONTEXT_KEY);
			if (userIdValue instanceof String requestUserId && !requestUserId.isBlank()
					&& !requestUserId.equals(session.userId())) {
				throw new IllegalStateException(
						"Session '" + sessionId + "' does not belong to user '" + requestUserId + "'. Access denied.");
			}
		}

		// 2. 应用配置的过滤器检索历史（默认：所有事件）
		// 如果请求上下文包含 EventFilter，将其与 advisor 配置的过滤器合并，
		// 以便请求级参数覆盖 advisor 默认值
		EventFilter eventFilter = this.eventFilter;
		if (request.context().containsKey(EVENT_FILTER_CONTEXT_KEY)) {
			EventFilter requestEventFilter = (EventFilter) request.context().get(EVENT_FILTER_CONTEXT_KEY);
			if (requestEventFilter != null) {
				eventFilter = this.eventFilter.merge(requestEventFilter);
			}
		}

		// 始终从活动上下文窗口中排除已归档事件 — 它们被压缩出去，
		// 仅存在于 Recall Storage 搜索中。合并强制启用标志，
		// 无论配置或每个请求的过滤器如何
		eventFilter = eventFilter.merge(EventFilter.active());

		List<AbstractEvent> events = this.sessionService.getEvents(sessionId, eventFilter);
		List<Message> history = events.stream()
			.filter(e -> e instanceof MessageEvent)
			.map(e -> ((MessageEvent) e).getMessage())
			.filter(Objects::nonNull)
			.toList();

		List<Message> combined = new ArrayList<>(history);
		combined.addAll(request.prompt().getInstructions());

		// 3. 确保所有系统消息首先出现（保持相对顺序）。
		// 单次传递收集每个 SystemMessage，就地删除它们，
		// 然后作为块前置 — 因此埋在历史中的系统消息和当前请求中的第二个系统消息
		// 都最终在前端，而不是让第二个系统消息悬在列表中间
		List<Message> systemMessages = combined.stream().filter(SystemMessage.class::isInstance).toList();
		if (!systemMessages.isEmpty()) {
			combined.removeIf(SystemMessage.class::isInstance);
			combined.addAll(0, systemMessages);
		}

		// 4. 将当前用户消息追加到会话，受配置的消息过滤器约束。
		// 跳过仅影响持久化 — 输出提示词不受影响
		Message userMessage = request.prompt().getLastUserOrToolResponseMessage();
		if (shouldPersist(userMessage, sessionId)) {
			String branch = getBranch(request.context());
			MessageEvent.MessageEventBuilder eventBuilder = MessageEvent.builder()
				.id(this.requestEventIdGenerator.generate(request, userMessage))
				.sessionId(sessionId)
				.message(userMessage);
			if (branch != null) {
				eventBuilder.branch(branch);
			}
			this.sessionService.appendEvent(eventBuilder.build());
		}

		return request.mutate().prompt(request.prompt().mutate().messages(combined).build()).build();
	}

	@Override
	public @NonNull ChatClientResponse after(ChatClientResponse response, @NonNull AdvisorChain advisorChain) {
		String sessionId = getSessionId(response.context());
		String branch = getBranch(response.context());

		// 1. 将模型生成的助手消息追加到会话，受配置的消息过滤器约束。
		// 默认情况下排除无内容的消息 — 空白文本、无工具调用、无媒体
		if (response.chatResponse() != null) {
			response.chatResponse()
				.getResults()
				.stream()
				.map(g -> (Message) g.getOutput())
				.filter(msg -> shouldPersist(msg, sessionId))
				.forEach(msg -> {
					MessageEvent.MessageEventBuilder eventBuilder = MessageEvent.builder()
						.id(this.responseEventIdGenerator.generate(response, msg))
						.sessionId(sessionId)
						.message(msg);
					if (branch != null) {
						eventBuilder.branch(branch);
					}
					this.sessionService.appendEvent(eventBuilder.build());
				});
		}

		// 2. 同步压缩（如果配置）— 完整轮次（用户 + 助手）
		// 已在此时写入，因此没有竞争
		if (this.compactionTrigger != null && this.compactionStrategy != null) {
			this.sessionService.compact(sessionId, this.compactionTrigger, this.compactionStrategy);
		}

		return response;
	}

	@Override
	public @NonNull Flux<ChatClientResponse> adviseStream(@NonNull ChatClientRequest request, StreamAdvisorChain chain) {
		return Mono.just(request)
			.publishOn(this.scheduler)
			.map(r -> this.before(r, chain))
			.flatMapMany(chain::nextStream)
			// 重新固定到调度器，以便 after() 回调（执行同步会话写入和可选压缩）
			// 始终在配置的调度器上运行，而不是 LLM 流线程
			.publishOn(this.scheduler)
			.transform(flux -> new ChatClientMessageAggregator().aggregateChatClientResponse(flux,
					r -> this.after(r, chain)));
	}

	private String getSessionId(Map<String, @Nullable Object> context) {
		Object value = context.get(SESSION_ID_CONTEXT_KEY);
		if (value instanceof String s && !s.isBlank()) {
			return s;
		}
		throw new IllegalStateException(
				"No session ID found in advisor context. " + "Set SESSION_ID_CONTEXT_KEY on every request: "
						+ ".advisors(a -> a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, sessionId))");
	}

	private String getUserId(Map<String, @Nullable Object> context) {
		Object value = context.get(USER_ID_CONTEXT_KEY);
		return (value instanceof String s && !s.isBlank()) ? s : this.defaultUserId;
	}

	/**
	 * 从请求上下文读取 branch（可能为 null）。子智能体通过 branch 隔离事件。
	 */
	private String getBranch(Map<String, @Nullable Object> context) {
		Object value = context.get(BRANCH_CONTEXT_KEY);
		return (value instanceof String s && !s.isBlank()) ? s : null;
	}

	/**
	 * 返回消息是否应该持久化到会话，委托给配置的 {@link MessageFilter}。
	 * 被拒绝的消息被记录并且不在后续请求中重放。
	 */
	private boolean shouldPersist(Message message, String sessionId) {
		if (!this.messageFilter.shouldPersist(message)) {
			logger.debug("Skipping [{}] message for session [{}] — rejected by the configured MessageFilter",
					message.getMessageType(), sessionId);
			return false;
		}
		return true;
	}

	public static Builder builder(ISessionManager sessionService) {
		return new Builder(sessionService);
	}

	public static final class Builder {

		private final ISessionManager sessionService;

		private String defaultUserId = "default-user";

		// 比默认 ToolCallingAdvisor 优先级更高：before() 先运行，
		// after() 最后运行，因此工具结果在写入会话历史之前完全解析
		private int order = Ordered.HIGHEST_PRECEDENCE + 1000;

		private Scheduler scheduler = BaseAdvisor.DEFAULT_SCHEDULER;

		private EventFilter eventFilter = EventFilter.all();

		private MessageFilter messageFilter = MessageFilter.skipEmptyMessages();

		private SessionEventRequestIdGenerator requestEventIdGenerator = SessionEventRequestIdGenerator.random();

		private SessionEventResponseIdGenerator responseEventIdGenerator = SessionEventResponseIdGenerator.random();

		@Nullable private CompactionTrigger compactionTrigger;

		@Nullable private CompactionStrategy compactionStrategy;

		private Builder(ISessionManager sessionService) {
			Assert.notNull(sessionService, "sessionService must not be null");
			this.sessionService = sessionService;
		}

		public Builder defaultUserId(String defaultUserId) {
			this.defaultUserId = defaultUserId;
			return this;
		}

		public Builder order(int order) {
			this.order = order;
			return this;
		}

		public Builder scheduler(Scheduler scheduler) {
			this.scheduler = scheduler;
			return this;
		}

		/**
		 * 加载会话事件历史注入提示词时应用的过滤器。
		 * 默认为 {@link EventFilter#all()}（所有事件）。
		 * <p>
		 * 在多智能体场景中使用 {@link EventFilter#forBranch(String)}，
		 * 以便每个智能体仅看到自己分支和祖先的事件：
		 * <pre>{@code
		 * SessionMemoryAdvisor.builder(sessionService)
		 *     .eventFilter(EventFilter.forBranch("orch.researcher"))
		 *     .build();
		 * }</pre>
		 */
		public Builder eventFilter(EventFilter eventFilter) {
			Assert.notNull(eventFilter, "eventFilter must not be null");
			this.eventFilter = eventFilter;
			return this;
		}

		/**
		 * 在将会话记忆追加消息之前应用的过滤器 — 包括在 {@code before()} 中持久化的当前用户
		 * （或工具响应）消息和在 {@code after()} 中持久化的助手消息。
		 * 过滤器拒绝的消息不被持久化，因此不会在后续请求中重放。
		 * 输出提示词不受影响。默认为 {@link MessageFilter#skipEmptyMessages()}。
		 * <p>
		 * 注意：替换默认值会移除空助手消息保护（见 issue #19 — 某些模型拒绝作为历史重放的空消息）。
		 * 当仍需要时组合而不是替换：
		 * <pre>{@code
		 * SessionMemoryAdvisor.builder(sessionService)
		 *     .messageFilter(myFilter.and(MessageFilter.skipEmptyMessages()))
		 *     .build();
		 * }</pre>
		 */
		public Builder messageFilter(MessageFilter messageFilter) {
			Assert.notNull(messageFilter, "messageFilter must not be null");
			this.messageFilter = messageFilter;
			return this;
		}

		public Builder compactionTrigger(CompactionTrigger trigger) {
			this.compactionTrigger = trigger;
			return this;
		}

		public Builder compactionStrategy(CompactionStrategy strategy) {
			this.compactionStrategy = strategy;
			return this;
		}

		/**
		 * 覆盖 {@code before()} 中持久化的会话事件的 ID 派生方式
		 * （当前用户/工具响应消息）。默认为
		 * {@link SessionEventRequestIdGenerator#random()} — 每次调用生成新随机 ID，
		 * 即当前行为。提供确定性生成器使重试追加成为幂等无操作，而不是重复。
		 */
		public Builder requestEventIdGenerator(SessionEventRequestIdGenerator requestEventIdGenerator) {
			Assert.notNull(requestEventIdGenerator, "requestEventIdGenerator must not be null");
			this.requestEventIdGenerator = requestEventIdGenerator;
			return this;
		}

		/**
		 * 覆盖 {@code after()} 中持久化的每个会话事件的 ID 派生方式
		 * （助手回复消息）。默认为
		 * {@link SessionEventResponseIdGenerator#random()}。
		 */
		public Builder responseEventIdGenerator(SessionEventResponseIdGenerator responseEventIdGenerator) {
			Assert.notNull(responseEventIdGenerator, "responseEventIdGenerator must not be null");
			this.responseEventIdGenerator = responseEventIdGenerator;
			return this;
		}

		public SessionMemoryAdvisor build() {
			if ((this.compactionTrigger == null) != (this.compactionStrategy == null)) {
				throw new IllegalArgumentException(
						"compactionTrigger and compactionStrategy must be set together — set both or neither");
			}
			return new SessionMemoryAdvisor(this.sessionService, this.defaultUserId, this.order, this.scheduler,
					this.eventFilter, this.messageFilter, this.requestEventIdGenerator, this.responseEventIdGenerator,
					this.compactionTrigger, this.compactionStrategy);
		}

	}

}