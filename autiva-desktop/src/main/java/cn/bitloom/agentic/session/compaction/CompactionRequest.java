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

import java.util.List;

import org.springframework.ai.chat.messages.MessageType;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.event.MessageEvent;
import org.springframework.util.Assert;

/**
 * Contextual information provided to {@link CompactionTrigger} and
 * {@link CompactionStrategy} when evaluating whether compaction is needed.
 *
 * @param session the session being evaluated for compaction
 * @param events the session's current list of events, ordered from oldest to newest
 * @param currentEventCount total number of events in the session
 * @param currentTurnCount total number of turns in the session (a turn is a user message
 * plus all subsequent events up to the next user message)
 * @author Christian Tzolov
 * @since 2.0.0
 */
public record CompactionRequest(Session session, List<MessageEvent> events, int currentEventCount,
		int currentTurnCount) {

	/**
	 * Creates a {@code CompactionRequest} from the given session and its event list.
	 */
	public static CompactionRequest of(Session session, List<MessageEvent> events) {
		Assert.notNull(session, "session must not be null");
		Assert.notNull(events, "events must not be null");
		int eventCount = events.size();
		// Count only non-synthetic, non-archived, root-level (branch == null) USER messages.
		// Sub-agents in multi-agent sessions write USER messages attributed to their own
		// branch; counting those would inflate the turn count and cause premature
		// compaction of the root conversation.
		// Archived events are excluded because they represent history already compacted but
		// retained in the file for UI/search; counting them would keep the turn count
		// permanently above the threshold and re-trigger compaction on every turn.
		int turnCount = (int) events.stream()
			.filter(e -> !e.isSynthetic())
			.filter(e -> !e.isArchived())
			.filter(e -> e.getMessageType() == MessageType.USER)
			.filter(MessageEvent::isRootEvent)
			.count();
		return new CompactionRequest(session, events, eventCount, turnCount);
	}

}
