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
import java.util.HashSet;
import java.util.List;
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
	 * 移除压缩后 compacted 列表中没有对应 assistant(toolCalls) 的孤儿 ToolResponseMessage。
	 *
	 * <p>
	 * 压缩策略把旧事件摘要成纯文本 synthetic assistant（没有 toolCalls），但如果
	 * activeWindow 开头残留了 ToolResponseMessage（对应的 assistant(toolCalls) 被归档/摘要了），
	 * 历史会出现不成对的 (纯文本 assistant) + (孤儿 toolResponse)，违反 LLM API 的
	 * 成对约束导致 400 报错。
	 *
	 * <p>
	 * 本方法线性扫描 compacted 列表，跟踪已见 assistant(toolCalls) 的 toolCall id，
	 * 遇到 ToolResponseMessage 时检查其 responses 的 id 是否都已被见过。
	 * 完全孤儿的 ToolResponseMessage 被移除；部分孤儿时只保留已见 id 对应的 responses。
	 *
	 * @param compacted 压缩后的事件列表（可能含孤儿 toolResponse）
	 * @return 过滤后的新列表，保证 ToolResponseMessage 都有对应 assistant(toolCalls)
	 */
	public static List<MessageEvent> dropOrphanToolResponses(List<MessageEvent> compacted) {
		Set<String> seenToolCallIds = new HashSet<>();
		List<MessageEvent> filtered = new ArrayList<>(compacted.size());
		for (MessageEvent event : compacted) {
			if (event.getMessage() instanceof AssistantMessage am && am.hasToolCalls()) {
				am.getToolCalls().forEach(tc -> seenToolCallIds.add(tc.id()));
				filtered.add(event);
			}
			else if (event.getMessage() instanceof ToolResponseMessage trm) {
				List<ToolResponseMessage.ToolResponse> valid = trm.getResponses().stream()
					.filter(r -> seenToolCallIds.contains(r.id()))
					.collect(Collectors.toList());
				if (valid.isEmpty()) {
					// 孤儿 toolResponse：对应的 assistant(toolCalls) 已被摘要/归档，丢弃
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
