package cn.bitloom.agentic.agent;

import cn.bitloom.agentic.event.EventType;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.memory.JournalManager;
import cn.bitloom.agentic.session.MessageChannel;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.session.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AgentLifecycleHook {

    private static final String HEARTBEAT_OK = "HEARTBEAT_OK";
    private static final int ACK_MAX_CHARS = 300;
    private static final int MEMORY_CONSOLIDATE_THRESHOLD = 10;

    private final JournalManager journalManager;
    private final SessionManager sessionManager;

    public AgentLifecycleHook(JournalManager journalManager, SessionManager sessionManager) {
        this.journalManager = journalManager;
        this.sessionManager = sessionManager;
    }

    public void onSessionStart(String sessionId) {
        log.debug("会话开始: sessionId={}", sessionId);
    }

    public void onSessionEnd(String sessionId, List<AssistantMessage> messages, MessageChannel channel) {
        log.debug("会话结束: sessionId={}, channel={}, messages={}", sessionId, channel, messages != null ? messages.size() : 0);

        if (messages == null || messages.isEmpty()) {
            return;
        }

        if (channel != MessageChannel.USER) {
            return;
        }

        try {
            StringBuilder summary = new StringBuilder();
            for (AssistantMessage msg : messages) {
                String text = msg.getText();
                if (text != null && !text.isBlank()) {
                    String processed = stripHeartbeatOk(text);
                    if (!processed.isBlank()) {
                        summary.append(processed.stripTrailing()).append("\n");
                    }
                }
            }

            if (!summary.isEmpty()) {
                String truncated = summary.length() > 1000
                        ? summary.substring(0, 1000) + "..."
                        : summary.toString();
                journalManager.appendFromSession(sessionId, truncated);

                publishJournalEvent(sessionId, truncated);
            }

            publishMemoryConsolidateEvent(sessionId);
        }
        catch (Exception e) {
            log.error("会话结束处理失败: sessionId={}", sessionId, e);
        }
    }

    private void publishJournalEvent(String sessionId, String summary) {
        try {
            UserMessage journalMessage = UserMessage.builder()
                    .text(summary)
                    .metadata(Map.of("trigger", "journal", "sessionId", sessionId))
                    .build();
            EventBus.inBoxPublish(sessionId, journalMessage, EventType.JOURNAL, MessageChannel.JOURNAL);
            log.debug("[LifecycleHook] 发布日记事件: sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("[LifecycleHook] 发布日记事件失败: sessionId={}", sessionId, e);
        }
    }

    private void publishMemoryConsolidateEvent(String sessionId) {
        try {
            Session session = sessionManager.getById(sessionId);
            if (session == null) {
                return;
            }
            int cursor = session.getMemoryCursor() != null ? session.getMemoryCursor() : 0;
            int unprocessedCount = session.getChannelMessages(MessageChannel.USER).size() - cursor;
            if (unprocessedCount >= MEMORY_CONSOLIDATE_THRESHOLD) {
                UserMessage consolidateMessage = UserMessage.builder()
                        .text("[系统触发] 请整理以下会话的记忆，从第 " + cursor + " 条消息开始")
                        .metadata(Map.of("trigger", "memory_consolidate", "cursor", cursor))
                        .build();
                EventBus.inBoxPublish(sessionId, consolidateMessage, EventType.MEMORY_CONSOLIDATE, MessageChannel.MEMORY);
                log.debug("[LifecycleHook] 发布记忆整理事件: sessionId={}, cursor={}, unprocessed={}",
                        sessionId, cursor, unprocessedCount);
            }
        } catch (Exception e) {
            log.warn("[LifecycleHook] 发布记忆整理事件失败: sessionId={}", sessionId, e);
        }
    }

    private boolean isHeartbeatOkResponse(List<AssistantMessage> messages) {
        for (AssistantMessage msg : messages) {
            String text = msg.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            String stripped = text.strip();
            if (stripped.startsWith(HEARTBEAT_OK) || stripped.endsWith(HEARTBEAT_OK)) {
                String remaining = stripped.replace(HEARTBEAT_OK, "").strip();
                return remaining.length() <= ACK_MAX_CHARS;
            }
        }
        return false;
    }

    private String stripHeartbeatOk(String text) {
        String stripped = text.strip();
        if (stripped.startsWith(HEARTBEAT_OK)) {
            return stripped.substring(HEARTBEAT_OK.length()).strip();
        }
        if (stripped.endsWith(HEARTBEAT_OK)) {
            return stripped.substring(0, stripped.length() - HEARTBEAT_OK.length()).strip();
        }
        return text;
    }
}
