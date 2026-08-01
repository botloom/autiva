package cn.bitloom.agentic.session;

import cn.bitloom.agentic.event.AbstractEvent;
import cn.bitloom.agentic.session.compaction.CompactionResult;
import cn.bitloom.agentic.session.compaction.CompactionStrategy;
import cn.bitloom.agentic.session.compaction.CompactionTrigger;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.Message;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public interface ISessionManager {

    Session create(CreateSessionRequest request);
    Session getById(String sessionId);
    List<Session> findByUserId(String userId);
    void remove(String sessionId);
    int deleteExpiredSessions(Instant before);

    void appendEvent(AbstractEvent event);
    List<AbstractEvent> getEvents(String sessionId, EventFilter filter);

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

    void flush(String sessionId);

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
