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

package cn.bitloom.agentic.session.compaction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import cn.bitloom.agentic.event.MessageEvent;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.util.Assert;

/**
 * 由 LLM 驱动的压缩策略，采用滑动窗口方式将较早的会话事件总结为
 * 一条合成的 user+assistant 轮次。
 *
 * <h3>算法</h3>
 * <ol>
 * <li>将合成的总结事件与真实的会话事件分离。</li>
 * <li>保留最后 {@code maxEventsToKeep} 条真实事件不变（即<em>活动窗口</em>），
 * 并将截断点回退到最近的轮次边界，以确保不会保留半个未完成的轮次。</li>
 * <li>活动窗口之前的所有事件——以及此前任何合成的总结——共同构成
 * <em>待总结事件</em>。</li>
 * <li>通过一次 LLM 调用将其压缩为滚动式总结，可选地包含活动窗口中最后
 * {@code overlapSize} 条事件以保证连续性。</li>
 * <li>结果作为<em>合成的总结轮次</em>放置：一对合成事件
 * [{@code USER} 影子提示，{@code ASSISTANT} 总结]，其后跟随活动窗口。
 * 这借鉴了 OpenAI Agents SDK 的做法，确保会话始终保持连贯的
 * user↔assistant 交替。</li>
 * </ol>
 *
 * <h3>递归 / 滚动行为</h3> 上一次压缩轮次产生的任何合成总结，都会作为上下文
 * 输入给 LLM 以生成新的总结。这意味着每条总结都是在前一条的基础上<em>累加</em>
 * 而成，而非从头开始，从而形成一个压缩上下文的滚动窗口。
 *
 * <h3>无操作条件</h3> 如果根（非分支）真实事件的数量不超过
 * {@code maxEventsToKeep}，则不进行 LLM 调用，事件原样返回。
 *
 * @author Christian Tzolov
 * @since 2.0.0
 * @see SlidingWindowCompactionStrategy
 */
public final class RecursiveSummarizationCompactionStrategy implements CompactionStrategy {

	private static final Logger logger = LoggerFactory.getLogger(RecursiveSummarizationCompactionStrategy.class);

	/** 压缩后保留的最近真实事件的默认数量。 */
	public static final int DEFAULT_MAX_EVENTS_TO_KEEP = 10;

	/**
	 * 为保证连续性而提供给总结提示的活动窗口事件的默认数量。
	 */
	public static final int DEFAULT_OVERLAP_SIZE = 2;

	private static final String STRATEGY_NAME = "recursive-summarization";

	/**
	 * 用于开启每条总结轮次的合成用户消息，参照 OpenAI
	 * Agents SDK 的影子提示模式。
	 */
	public static final String DEFAULT_SUMMARY_SHADOW_PROMPT = "请总结我们到目前为止的对话。";

	private static final String DEFAULT_SYSTEM_PROMPT = """
			你是一个会话总结器。你的任务是针对提供的会话历史生成一份简要总结。\
			该总结将取代上下文窗口中的原始事件，必须保留继续连贯对话所需的所有关键信息。

			准则：
			- 保留关键事实、决策、用户偏好以及重要结果。
			- 记录尚未解决或有待处理的问题或待办事项。
			- 使用第三人称叙述（"用户问……"、"助手解释了……"）。
			- 力求简洁但完整，省略冗余与重复内容。
			- 若提供了之前的总结，应自然地进行整合——不要逐字重复。
			""";

	private final ChatClient chatClient;

	private final int maxEventsToKeep;

	private final int overlapSize;

	private final String systemPrompt;

	private final String shadowPrompt;

	private final TokenCountEstimator tokenCountEstimator;

	@Nullable private final Consumer<CompactionRequest> onSummarizationFailure;

	private final Function<MessageEvent, String> eventFormatter;

	private RecursiveSummarizationCompactionStrategy(ChatClient chatClient, int maxEventsToKeep, int overlapSize,
			String systemPrompt, String shadowPrompt, TokenCountEstimator tokenCountEstimator,
			@Nullable Consumer<CompactionRequest> onSummarizationFailure,
			Function<MessageEvent, String> eventFormatter) {
		Assert.notNull(chatClient, "chatClient 不能为空");
		Assert.isTrue(maxEventsToKeep > 0, "maxEventsToKeep 必须大于 0");
		Assert.isTrue(overlapSize >= 0, "overlapSize 必须 >= 0");
		Assert.isTrue(overlapSize < maxEventsToKeep, "overlapSize 必须小于 maxEventsToKeep");
		Assert.hasText(systemPrompt, "systemPrompt 不能为空");
		Assert.hasText(shadowPrompt, "shadowPrompt 不能为空");
		Assert.notNull(tokenCountEstimator, "tokenCountEstimator 不能为空");
		Assert.notNull(eventFormatter, "eventFormatter 不能为空");
		this.chatClient = chatClient;
		this.maxEventsToKeep = maxEventsToKeep;
		this.overlapSize = overlapSize;
		this.systemPrompt = systemPrompt;
		this.shadowPrompt = shadowPrompt;
		this.tokenCountEstimator = tokenCountEstimator;
		this.onSummarizationFailure = onSummarizationFailure;
		this.eventFormatter = eventFormatter;
	}

	@Override
	public CompactionResult compact(CompactionRequest context) {

		Assert.notNull(context, "context 不能为空");
		Assert.notNull(context.session(), "session 不能为空");

		List<MessageEvent> events = context.events();

		List<MessageEvent> syntheticEvents = events.stream().filter(MessageEvent::isSynthetic).toList();
		List<MessageEvent> realEvents = events.stream().filter(e -> !e.isSynthetic()).toList();

		// 仅统计根（非分支）真实事件。来自子代理会话的分支事件
		// 随其外层根轮次一并打包，不会占用 maxEventsToKeep 的预算。
		long rootEventCount = realEvents.stream().filter(MessageEvent::isRootEvent).count();

		if (rootEventCount <= this.maxEventsToKeep) {
			// 无需压缩——原样返回
			return new CompactionResult(events, List.of(), 0);
		}

		// 在 realEvents 中查找最后一个待归档根事件之后的位置。
		long rootEventsToArchive = rootEventCount - this.maxEventsToKeep;
		int rawCutIndex = 0;
		long rootSeen = 0;
		for (int i = 0; i < realEvents.size(); i++) {
			if (realEvents.get(i).isRootEvent()) {
				rootSeen++;
				if (rootSeen == rootEventsToArchive) {
					rawCutIndex = i + 1;
					break;
				}
			}
		}

		// 向前对齐到最近的根级轮次起点（USER 消息），使活动窗口
		// 始终从轮次边界开始，不会出现半个未完成的轮次。
		// 子代理的 USER 消息（branch != null）会被跳过——它们属于轮次内部。
		int cutIndex = CompactionUtils.snapToTurnStart(realEvents, rawCutIndex);

		// 拆分真实事件：归档较旧的事件，保留最新的窗口
		List<MessageEvent> toArchive = realEvents.subList(0, cutIndex);
		List<MessageEvent> activeWindow = realEvents.subList(cutIndex, realEvents.size());

		// 重叠：活动窗口中的前 `overlapSize` 条事件也会提供给
		// 总结提示，以便 LLM 获得连续性上下文
		List<MessageEvent> overlapEvents = activeWindow.subList(0, Math.min(this.overlapSize, activeWindow.size()));

		// 为 LLM 构建用户提示
		String userPrompt = buildSummarizationPrompt(syntheticEvents, toArchive, overlapEvents);

		// 调用 LLM
		String summary = this.chatClient.prompt().system(this.systemPrompt).user(userPrompt).call().content();

		if (summary == null || summary.isBlank()) {
			logger.warn(
					"RecursiveSummarizationCompactionStrategy：LLM 为会话 '{}' 返回了空或空白的总结。"
							+ "已跳过压缩——事件历史保持不变。",
					context.session().id());
			if (this.onSummarizationFailure != null) {
				this.onSummarizationFailure.accept(context);
			}
			return new CompactionResult(events, List.of(), 0);
		}

		// 构建压缩后的事件列表：合成的总结轮次（user + assistant）+
		// 活动窗口。这两个事件组成的轮次借鉴了 OpenAI Agents SDK 影子提示
		// 模式，使模型始终看到连贯的 user↔assistant 交替。
		// 两个事件共享相同的时间戳，从而被视为一个原子对。
		String sessionId = context.session().id();
		long now = System.currentTimeMillis();
		Map<String, Object> summaryMetadata = new HashMap<>();
		summaryMetadata.put(MessageEvent.METADATA_SYNTHETIC, true);
		List<MessageEvent> summaryTurn = List.of(
				MessageEvent.builder()
					.sessionId(sessionId)
					.timestamp(now)
					.message(new UserMessage(this.shadowPrompt))
					.metadata(new HashMap<>(summaryMetadata))
					.build(),
				MessageEvent.builder()
					.sessionId(sessionId)
					.timestamp(now)
					.message(new AssistantMessage(summary))
					.metadata(new HashMap<>(summaryMetadata))
					.build());

		List<MessageEvent> compacted = new ArrayList<>();
		compacted.addAll(summaryTurn);
		compacted.addAll(activeWindow);

		// 归档 = 仅指那些被总结并移除的真实事件。
		// 此前生成的合成总结会被上面的新 summaryTurn 隐式替换，
		// 因此不包含在 archivedEvents 中。这保持了 archivedEvents 的语义
		// 与其他策略一致——它们只报告从会话中移除的真实事件。
		List<MessageEvent> archived = new ArrayList<>(toArchive);

		int tokensArchived = toArchive.stream()
			.mapToInt(e -> this.tokenCountEstimator.estimate(this.eventFormatter.apply(e)))
			.sum();

		return new CompactionResult(compacted, archived, tokensArchived);
	}

	/**
	 * 构建面向用户的总结提示，包括：
	 * <ol>
	 * <li>此前任何合成的总结（递归上下文）。</li>
	 * <li>待归档的事件（要总结的内容）。</li>
	 * <li>来自活动窗口的重叠事件（用于保证连续性）。</li>
	 * </ol>
	 */
	private String buildSummarizationPrompt(List<MessageEvent> priorSummaries, List<MessageEvent> eventsToSummarize,
			List<MessageEvent> overlapEvents) {

		StringBuilder prompt = new StringBuilder();

		if (!priorSummaries.isEmpty()) {
			prompt.append("=== 之前的总结 ===\n");
			// 排除合成的 USER 影子提示——它们只是结构性占位符，
			// 并非总结内容。仅包含文本承载了实际压缩历史的 ASSISTANT（及遗留 SYSTEM）事件。
			priorSummaries.stream()
				.filter(e -> e.getMessageType() != MessageType.USER)
				.forEach(e -> prompt.append(e.getMessage().getText()).append("\n"));
			prompt.append("\n");
		}

		prompt.append("=== 待总结的对话 ===\n");
		eventsToSummarize.forEach(e -> prompt.append(this.eventFormatter.apply(e)).append("\n"));

		if (!overlapEvents.isEmpty()) {
			prompt.append("\n=== 即将到来的上下文（无需总结——仅供保证连续性） ===\n");
			overlapEvents.forEach(e -> prompt.append(this.eventFormatter.apply(e)).append("\n"));
		}

		prompt.append("\n请现在写出总结：");
		return prompt.toString();
	}

	public static String formatEvent(MessageEvent event) {
		return CompactionUtils.formatEvent(event);
	}

	// --- Builder ---

	public static Builder builder(ChatClient chatClient) {
		return new Builder(chatClient);
	}

	public static final class Builder {

		private final ChatClient chatClient;

		private int maxEventsToKeep = DEFAULT_MAX_EVENTS_TO_KEEP;

		private int overlapSize = DEFAULT_OVERLAP_SIZE;

		private String systemPrompt = DEFAULT_SYSTEM_PROMPT;

		private String shadowPrompt = DEFAULT_SUMMARY_SHADOW_PROMPT;

		private TokenCountEstimator tokenCountEstimator = new JTokkitTokenCountEstimator();

		@Nullable private Consumer<CompactionRequest> onSummarizationFailure;

		private Function<MessageEvent, String> eventFormatter = RecursiveSummarizationCompactionStrategy::formatEvent;

		private Builder(ChatClient chatClient) {
			Assert.notNull(chatClient, "chatClient 不能为空");
			this.chatClient = chatClient;
		}

		/**
		 * 压缩后完整保留的最近真实事件数量。较早的事件会被
		 * 总结。默认值：{@value #DEFAULT_MAX_EVENTS_TO_KEEP}。
		 */
		public Builder maxEventsToKeep(int maxEventsToKeep) {
			Assert.isTrue(maxEventsToKeep > 0, "maxEventsToKeep 必须大于 0");
			this.maxEventsToKeep = maxEventsToKeep;
			return this;
		}

		/**
		 * 为了连续性而包含在总结提示中的活动窗口事件数量。默认值：{@value #DEFAULT_OVERLAP_SIZE}。
		 */
		public Builder overlapSize(int overlapSize) {
			Assert.isTrue(overlapSize >= 0, "overlapSize 必须 >= 0");
			this.overlapSize = overlapSize;
			return this;
		}

		/**
		 * 替换发送给总结 LLM 的默认系统提示。
		 */
		public Builder systemPrompt(String systemPrompt) {
			Assert.hasText(systemPrompt, "systemPrompt 不能为空");
			this.systemPrompt = systemPrompt;
			return this;
		}

		/**
		 * 替换开启每条总结轮次的合成 {@code USER} 消息（即
		 * "影子提示"）。默认值为 {@link #DEFAULT_SUMMARY_SHADOW_PROMPT}。面向
		 * 多语言应用或需要不同框架的领域特定代理时可覆盖。
		 */
		public Builder shadowPrompt(String shadowPrompt) {
			Assert.hasText(shadowPrompt, "shadowPrompt 不能为空");
			this.shadowPrompt = shadowPrompt;
			return this;
		}

		/**
		 * 覆盖用于计算 {@code tokensEstimatedSaved} 的 token 估算器。
		 * 默认值为 {@link JTokkitTokenCountEstimator}。
		 */
		public Builder tokenCountEstimator(TokenCountEstimator tokenCountEstimator) {
			Assert.notNull(tokenCountEstimator, "tokenCountEstimator 不能为空");
			this.tokenCountEstimator = tokenCountEstimator;
			return this;
		}

		/**
		 * 注册一个可选回调，在 LLM 返回空或空白总结时被调用。
		 * 回调会接收到触发本次总结尝试的 {@link CompactionRequest}，
		 * 使调用方能够访问会话及其事件。
		 * <p>
		 * 无论是否设置此回调，失败时始终会输出一条
		 * {@link org.slf4j.Logger#warn warn} 级别的日志。
		 */
		public Builder onSummarizationFailure(Consumer<CompactionRequest> onSummarizationFailure) {
			Assert.notNull(onSummarizationFailure, "onSummarizationFailure 不能为空");
			this.onSummarizationFailure = onSummarizationFailure;
			return this;
		}

		/**
		 * 覆盖用于将 {@link MessageEvent} 渲染为总结提示及 token 计数中
		 * 一行文本的函数。默认使用内置格式化器，可处理纯文本、工具调用和工具响应。
		 */
		public Builder eventFormatter(Function<MessageEvent, String> eventFormatter) {
			Assert.notNull(eventFormatter, "eventFormatter 不能为空");
			this.eventFormatter = eventFormatter;
			return this;
		}

		public RecursiveSummarizationCompactionStrategy build() {
			if (this.overlapSize >= this.maxEventsToKeep) {
				throw new IllegalArgumentException("overlapSize (" + this.overlapSize
						+ ") 必须小于 maxEventsToKeep (" + this.maxEventsToKeep + ")");
			}
			return new RecursiveSummarizationCompactionStrategy(this.chatClient, this.maxEventsToKeep, this.overlapSize,
					this.systemPrompt, this.shadowPrompt, this.tokenCountEstimator, this.onSummarizationFailure,
					this.eventFormatter);
		}

	}

}
