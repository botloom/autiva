package cn.bitloom.agentic.memory;

import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.event.EventConverter;
import cn.bitloom.agentic.event.MessageEvent;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 子智能体专用 ChatMemory（内存版，不持久化到文件）。
 * <p>
 * 对齐 netInsight 的 InMemoryChatMemory 设计。
 * 内部维护 messageCache 替代委托 InMemorySessionManager，add 时实时广播 ToolCalls/ToolResponse 事件。
 * 实现 TurnBufferedChatMemory 接口以兼容 SessionRunner 的轮次缓冲协议：
 * - setCurrentMessageId/flush：由 SessionRunner 调用，子智能体场景下为 no-op（消息已实时广播）
 * - getHistoryFromFile/countFileMessages：返回空/0（子智能体无文件历史，靠 messageCache 即可）
 */
@Slf4j
public class InMemoryChatMemory implements TurnBufferedChatMemory {

    private final ConcurrentHashMap<String, List<Message>> messageCache = new ConcurrentHashMap<>();

    @Setter
    private String currentMessageId;

    @Override
    public void add(@NonNull String sessionId, @NonNull List<Message> messages) {
        if (messages.isEmpty()) {
            return;
        }
        messageCache.computeIfAbsent(sessionId, k -> new ArrayList<>()).addAll(messages);
        // 广播 ToolCalls/ToolResponse 事件（前端实时显示）
        broadcastToolEvents(sessionId, messages);
    }

    @NonNull
    @Override
    public List<Message> get(@NonNull String sessionId) {
        List<Message> messages = messageCache.get(sessionId);
        return Objects.isNull(messages) ? List.of() : messages;
    }

    @Override
    public void clear(@NonNull String sessionId) {
        messageCache.remove(sessionId);
    }

    /**
     * 子智能体消息已实时广播且纯内存存储，flush 为 no-op。
     * 仅为兼容 TurnBufferedChatMemory 协议（SessionRunner.safeFlush 调用）。
     */
    @Override
    public void flush() {
        // no-op
    }

    /**
     * 子智能体无文件历史，返回空列表。
     */
    @Override
    public List<Message> getHistoryFromFile() {
        return List.of();
    }

    /**
     * 子智能体无文件历史，返回 0。
     */
    @Override
    public int countFileMessages() {
        return 0;
    }

    /**
     * 广播 ToolCalls/ToolResponse 事件到 EventBus.publishOut（前端实时显示）。
     */
    private void broadcastToolEvents(String sessionId, List<Message> messages) {
        for (Message message : messages) {
            if (message instanceof AssistantMessage am) {
                Object finishReason = am.getMetadata().get("finishReason");
                if (finishReason != null && "TOOL_CALLS".equals(finishReason.toString())) {
                    MessageEvent event = EventConverter.fromMessage(sessionId, am);
                    event.setMessageId(currentMessageId);
                    EventBus.publishOut(event);
                }
            }
            if (message instanceof ToolResponseMessage trm) {
                MessageEvent event = EventConverter.fromMessage(sessionId, trm);
                event.setMessageId(currentMessageId);
                EventBus.publishOut(event);
            }
        }
    }
}
