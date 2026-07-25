package cn.bitloom.agentic.memory;

import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.event.EventConverter;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.agentic.session.Session;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 会话级 ChatMemory 实现（per-session 实例，非 Spring Bean）。
 * <p>
 * 对齐 netInsight 的 DbChatMemory 设计，存储介质改为文件系统。由
 * FileSystemSessionManager.activate() 为每个 Session 手动创建，负责：
 * <ul>
 *   <li>缓冲本轮对话消息到内存（不立即写文件），解决并行任务消息混乱问题</li>
 *   <li>本轮对话结束后由 SessionRunner 调用 flush() 批量持久化到 events.jsonl</li>
 *   <li>get() 实时查文件历史 + 合并本轮缓冲区</li>
 *   <li>实时广播 ToolCalls/ToolResponse 事件到 EventBus.publishOut（前端实时显示）</li>
 * </ul>
 * 解决 tool_call/tool 不对称问题：本轮异常则整轮不入库，缓冲区被清空。
 */
@Slf4j
public class FileSystemChatMemory implements TurnBufferedChatMemory {

    private final FileSystemSessionManager sessionManager;
    private final Session session;

    /** 本轮对话缓冲区（未持久化的消息） */
    private final List<Message> currentTurnBuffer = new ArrayList<>();

    /** 当前轮次 messageId，由 SessionRunner 在每轮开始时设置 */
    @Setter
    private String currentMessageId;

    public FileSystemChatMemory(FileSystemSessionManager sessionManager, Session session) {
        this.sessionManager = sessionManager;
        this.session = session;
    }

    /**
     * 缓冲消息到内存，不立即写文件。
     * 同时实时广播 ToolCalls/ToolResponse 事件（前端实时显示）。
     * 清理前一条孤儿工具调用消息（边界检查）。
     */
    @Override
    public void add(@NonNull String sessionId, @NonNull List<Message> messages) {
        if (messages.isEmpty()) {
            return;
        }
        if (!Objects.equals(sessionId, session.getId())) {
            return;
        }

        /*
         * 清理前一条孤儿工具调用消息：若上一条消息是含 tool_calls 的 AssistantMessage，
         * 且本次追加的不是 ToolResponseMessage，说明工具调用已无响应（异常中断），需要移除。
         * DeepSeek 等模型严格要求 AssistantMessage(tool_calls) 后必须紧跟 ToolResponseMessage。
         */
        if (messages.stream().noneMatch(msg -> msg instanceof ToolResponseMessage)) {
            if (!currentTurnBuffer.isEmpty()) {
                Message lastMsg = currentTurnBuffer.getLast();
                if (lastMsg instanceof AssistantMessage lastAssistant && !lastAssistant.getToolCalls().isEmpty()) {
                    currentTurnBuffer.removeLast();
                }
            }
        }

        // 缓冲 + 实时广播
        for (Message message : messages) {
            currentTurnBuffer.add(message);
            broadcastToolEvent(message);
        }
    }

    /**
     * 获取消息：文件历史（游标后） + 本轮缓冲区。
     * MessageChatMemoryAdvisor 在 Agent 推理时调用此方法获取历史消息。
     */
    @NonNull
    @Override
    public List<Message> get(@NonNull String sessionId) {
        if (!Objects.equals(sessionId, session.getId())) {
            return List.of();
        }
        List<Message> history = getHistoryFromFile();
        List<Message> result = new ArrayList<>(history);
        result.addAll(currentTurnBuffer);
        return result;
    }

    /**
     * 本轮对话结束时批量持久化到 events.jsonl。
     * 由 SessionRunner 在 doOnComplete/onErrorResume/BLOCK 返回后调用。
     * 每条消息带上当前 messageId，便于按轮次区分。
     */
    @Override
    public void flush() {
        if (currentTurnBuffer.isEmpty()) {
            return;
        }
        String sessionId = session.getId();
        try {
            List<MessageEvent> events = new ArrayList<>();
            for (Message message : currentTurnBuffer) {
                MessageEvent msgEvent = EventConverter.fromMessage(sessionId, message);
                msgEvent.setMessageId(currentMessageId);
                events.add(msgEvent);
            }
            sessionManager.storeEvents(sessionId, events);
            log.info("[FileChatMemory] flush 完成: sessionId={}, messageId={}, count={}",
                    sessionId, currentMessageId, events.size());
        } catch (Exception e) {
            log.error("[FileChatMemory] flush 失败: sessionId={}, messageId={}",
                    sessionId, currentMessageId, e);
        } finally {
            currentTurnBuffer.clear();
        }
    }

    @Override
    public void clear(@NonNull String sessionId) {
        currentTurnBuffer.clear();
        sessionManager.clear(sessionId);
    }

    /** 只查文件历史消息（不含缓冲区），供压缩使用 */
    @Override
    public List<Message> getHistoryFromFile() {
        return sessionManager.loadEventsAsMessages(
                session.getId(), session.getMemoryCursor(), Integer.MAX_VALUE);
    }

    /** 文件历史消息总数（供压缩推进游标用） */
    @Override
    public int countFileMessages() {
        return sessionManager.countEvents(session.getId());
    }

    // ===== 私有方法 =====

    /**
     * 实时广播 ToolCalls/ToolResponse 事件到 EventBus.publishOut（前端实时显示）。
     */
    private void broadcastToolEvent(Message message) {
        String sessionId = session.getId();
        if (message instanceof AssistantMessage am) {
            Object finishReason = am.getMetadata().get("finishReason");
            if (finishReason != null && "TOOL_CALLS".equals(finishReason.toString())) {
                MessageEvent event = EventConverter.fromMessage(sessionId, am);
                event.setMessageId(currentMessageId);
                EventBus.publishOut(event);
            }
        } else if (message instanceof ToolResponseMessage trm) {
            MessageEvent event = EventConverter.fromMessage(sessionId, trm);
            event.setMessageId(currentMessageId);
            EventBus.publishOut(event);
        }
    }
}
