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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import cn.bitloom.agentic.event.MessageEvent;

/**
 * Internal utilities shared by compaction strategies.
 *
 * @author Christian Tzolov
 * @since 2.0.0
 */
public final class CompactionUtils {

	private CompactionUtils() {
	}

	/**
	 * Renders a {@link MessageEvent} as a single line of text suitable for token
	 * estimation and LLM summarization prompts.
	 *
	 * <p>
	 * Handles all Spring AI message types:
	 * <ul>
	 * <li>Plain user / assistant / system messages → {@code "Role: text"}</li>
	 * <li>{@link AssistantMessage} with tool calls →
	 * {@code "Assistant [tool calls: name(args), ...]"}</li>
	 * <li>{@link ToolResponseMessage} →
	 * {@code "Tool [responses: name -> data, ...]"}</li>
	 * </ul>
	 * @param event the session event to format
	 * @return a non-null, non-empty string representing the event
	 */
	static String formatEvent(MessageEvent event) {
		String role = switch (event.getMessageType()) {
			case USER -> "User";
			case ASSISTANT -> "Assistant";
			case SYSTEM -> "System";
			case TOOL -> "Tool";
		};

		if (event.getMessage() instanceof AssistantMessage am && am.hasToolCalls()) {
			String calls = am.getToolCalls()
				.stream()
				.map(tc -> tc.name() + "(" + tc.arguments() + ")")
				.collect(Collectors.joining(", "));
			String text = am.getText();
			return (text != null && !text.isBlank()) ? role + ": " + text + " [tool calls: " + calls + "]"
					: role + " [tool calls: " + calls + "]";
		}

		if (event.getMessage() instanceof ToolResponseMessage trm) {
			String responses = trm.getResponses()
				.stream()
				.map(r -> r.name() + " -> " + r.responseData())
				.collect(Collectors.joining(", "));
			return role + " [responses: " + responses + "]";
		}

		String text = event.getMessage().getText();
		return role + ": " + (text != null ? text : "[no text content]");
	}

	/**
	 * Advances {@code rawCutIndex} forward until it points to a root-level (null-branch)
	 * {@link MessageType#USER} event, or to {@code real.size()} if no such event exists.
	 *
	 * <p>
	 * Compaction strategies compute a raw cut point (the index into the real-event list
	 * where the kept window would start) based on event counts or token budgets. That raw
	 * cut can land in the middle of a turn — for example at an assistant reply whose user
	 * message would be archived. Snapping to the nearest turn start guarantees that the
	 * kept window always begins at a complete turn, preserving conversation semantics.
	 * @param real the list of non-synthetic session events
	 * @param rawCutIndex the initial cut point; must be in {@code [0, real.size()]}
	 * @return the adjusted index pointing to the first root-level USER event at or after
	 * {@code rawCutIndex}, or {@code real.size()} if none exists
	 */
	static int snapToTurnStart(List<MessageEvent> real, int rawCutIndex) {
		int idx = rawCutIndex;
		while (idx < real.size()
				&& !(real.get(idx).isRootEvent() && real.get(idx).getMessageType() == MessageType.USER)) {
			idx++;
		}
		return idx;
	}

	/**
	 * 保证压缩后事件列表中 assistant(toolCalls) 与 ToolResponseMessage 成对出现。
	 *
	 * <p>
	 * 压缩按 token 截断时，cut 点可能落在同一轮 tool 交互的 assistant(toolCalls) 与
	 * ToolResponseMessage 之间，导致两种孤儿：
	 * <ol>
	 * <li><strong>孤儿 ToolResponseMessage</strong>：对应的 assistant(toolCalls) 被归档/摘要，
	 * 违反 LLM API 成对约束。处理方式：丢弃孤儿 responses（完全孤儿则丢弃整个事件）。</li>
	 * <li><strong>孤儿 assistant(toolCalls)</strong>：对应的 ToolResponseMessage 被归档。
	 * 处理方式：从 {@code archived} 中拉回对应的 ToolResponseMessage，紧插在 assistant 之后；
	 * 若 archived 中也没有（open tool call，ToolResponse 尚未产生），则保留 assistant 不动，
	 * 后续 ToolResponse 产生追加后自然成对。</li>
	 * </ol>
	 *
	 * @param compacted 压缩后的事件列表（可能含孤儿 toolCall 或 toolResponse）
	 * @param archived 被归档的事件列表（用于拉回孤儿 assistant(toolCalls) 对应的 ToolResponse）
	 * @return 过滤后的新列表，尽量保证 toolCall/toolResponse 成对
	 */
	public static List<MessageEvent> reconcileToolPairs(List<MessageEvent> compacted, List<MessageEvent> archived) {
		// 1. 收集 compacted 里的 toolCall ids 与 response ids
		Set<String> seenToolCallIds = new HashSet<>();
		Set<String> seenResponseIds = new HashSet<>();
		for (MessageEvent event : compacted) {
			if (event.getMessage() instanceof AssistantMessage am && am.hasToolCalls()) {
				am.getToolCalls().forEach(tc -> seenToolCallIds.add(tc.id()));
			}
			else if (event.getMessage() instanceof ToolResponseMessage trm) {
				trm.getResponses().forEach(r -> seenResponseIds.add(r.id()));
			}
		}

		// 2. 找出孤儿 toolCall ids：assistant(toolCalls) 在 compacted 但对应 ToolResponse 不在
		Set<String> orphanToolCallIds = new HashSet<>();
		for (String id : seenToolCallIds) {
			if (!seenResponseIds.contains(id)) {
				orphanToolCallIds.add(id);
			}
		}

		// 3. 从 archived 拉回孤儿 assistant(toolCalls) 对应的 ToolResponse
		Map<String, ToolResponseMessage.ToolResponse> reclaimedById = new HashMap<>();
		if (!orphanToolCallIds.isEmpty()) {
			for (MessageEvent event : archived) {
				if (event.getMessage() instanceof ToolResponseMessage trm) {
					for (ToolResponseMessage.ToolResponse r : trm.getResponses()) {
						if (orphanToolCallIds.contains(r.id())) {
							reclaimedById.put(r.id(), r);
						}
					}
				}
			}
		}

		// 4. 重建 compacted：遇到孤儿 assistant(toolCalls) 后插入拉回的 ToolResponse；
		//    遇到孤儿 ToolResponse 则丢弃或重建
		List<MessageEvent> filtered = new ArrayList<>(compacted.size() + reclaimedById.size());
		for (MessageEvent event : compacted) {
			if (event.getMessage() instanceof AssistantMessage am && am.hasToolCalls()) {
				filtered.add(event);
				// 检查是否有需要从 archived 拉回的 ToolResponse
				List<ToolResponseMessage.ToolResponse> toReclaim = am.getToolCalls()
					.stream()
					.map(tc -> reclaimedById.get(tc.id()))
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
				if (!toReclaim.isEmpty()) {
					// 紧插在 assistant(toolCalls) 之后，保证成对顺序
					filtered.add(MessageEvent.builder()
						.sessionId(event.getSessionId())
						.timestamp(event.getTimestamp())
						.branch(event.getBranch())
						.message(ToolResponseMessage.builder().responses(toReclaim).build())
						.metadata(event.getMetadata())
						.build());
				}
				// open tool call（archived 也没有对应 ToolResponse）：保留 assistant 不动，
				// 后续 ToolResponse 产生追加后自然成对
			}
			else if (event.getMessage() instanceof ToolResponseMessage trm) {
				List<ToolResponseMessage.ToolResponse> valid = trm.getResponses()
					.stream()
					.filter(r -> seenToolCallIds.contains(r.id()))
					.collect(Collectors.toList());
				if (valid.isEmpty()) {
					// 孤儿 toolResponse：对应的 assistant(toolCalls) 已被归档/摘要，丢弃
					continue;
				}
				if (valid.size() < trm.getResponses().size()) {
					// 部分孤儿：重建只含有效 responses 的 ToolResponseMessage
					filtered.add(MessageEvent.builder()
						.sessionId(event.getSessionId())
						.timestamp(event.getTimestamp())
						.branch(event.getBranch())
						.message(ToolResponseMessage.builder().responses(valid).build())
						.metadata(event.getMetadata())
						.build());
				}
				else {
					filtered.add(event);
				}
			}
			else {
				filtered.add(event);
			}
		}
		return filtered;
	}

}
