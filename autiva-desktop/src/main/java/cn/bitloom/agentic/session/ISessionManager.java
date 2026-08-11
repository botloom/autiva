package cn.bitloom.agentic.session;

import cn.bitloom.agentic.event.AbstractEvent;
import cn.bitloom.agentic.session.compaction.CompactionResult;
import cn.bitloom.agentic.session.compaction.CompactionStrategy;
import cn.bitloom.agentic.session.compaction.CompactionTrigger;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public interface ISessionManager {

    Session create(CreateSessionRequest request);
    Session getById(String sessionId);
    List<Session> findByUserId(String userId);
    void remove(String sessionId);

    void appendEvent(AbstractEvent event);
    List<AbstractEvent> getEvents(String sessionId, EventFilter filter);

    /**
     * 善后被中断的 toolCalls：检查 events.jsonl 末尾，若最后一条事件是
     * 含 toolCalls 的 assistant 消息（缺少对应 ToolResponseMessage），
     * 为每个 toolCall 补一条虚拟 ToolResponse（content 标记被用户中断）。
     * <p>
     * 这样保持历史成对完整，LLM 在下次调用时能感知"工具被用户中断"并自然续接，
     * 不会因不成对的 toolCalls 触发 400 报错。
     * <p>
     * 应用场景：
     * <ul>
     *   <li>pauseGeneration 中调用：清理本次中断产生的孤儿</li>
     *   <li>sendMessage 开头调用：兜底防 pause 后异步 after() 竞态写入的孤儿</li>
     * </ul>
     *
     * @param sessionId 会话 ID
     */
    void finalizeInterruptedToolCalls(String sessionId);

    default List<AbstractEvent> getEvents(String sessionId) {
        return getEvents(sessionId, EventFilter.all());
    }

    default List<Message> getMessages(String sessionId) {
        return getEvents(sessionId).stream()
                .filter(e -> e instanceof cn.bitloom.agentic.event.MessageEvent)
                .map(e -> ((cn.bitloom.agentic.event.MessageEvent) e).getMessage())
                .filter(Objects::nonNull)
                .toList();
    }

    CompactionResult compact(String sessionId, CompactionTrigger trigger, CompactionStrategy strategy);

    void persistSession(Session session);

    @Nullable
    Session loadMetadata(String sessionId);

    /**
     * 在 per-session 锁保护下执行 action，保证同一 session 的串行处理。
     * 子智能体使用独立 sessionId，与父 session 锁互不冲突。
     *
     * @param sessionId 会话 ID
     * @param action    要执行的动作
     * @param <T>       返回类型
     * @return action 的返回值
     */
    <T> T withLock(String sessionId, Supplier<T> action);
}
