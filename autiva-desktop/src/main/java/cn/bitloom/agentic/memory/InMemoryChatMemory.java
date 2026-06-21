package cn.bitloom.agentic.memory;

import cn.bitloom.agentic.session.InMemorySessionManager;
import cn.bitloom.agentic.session.Session;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Objects;

/**
 * 纯内存 ChatMemory 实现，供子智能体 Session 使用。
 * <p>
 * 与 CompactChatMemory 一致：委托 InMemorySessionManager.store() 进行消息存储和事件发布。
 * 不做游标感知（子智能体不需要上下文压缩）和孤儿消息检查（子智能体生命周期短）。
 */
@Slf4j
public class InMemoryChatMemory implements ChatMemory {

    private final InMemorySessionManager inMemorySessionManager;

    public InMemoryChatMemory(InMemorySessionManager inMemorySessionManager) {
        this.inMemorySessionManager = inMemorySessionManager;
    }

    @Override
    public void add(@NonNull String sessionId, @NonNull List<Message> messages) {
        if (messages.isEmpty()) {
            return;
        }
        inMemorySessionManager.store(sessionId, messages);
    }

    @NonNull
    @Override
    public List<Message> get(@NonNull String sessionId) {
        Session session = inMemorySessionManager.getById(sessionId);
        if (Objects.isNull(session)) {
            return List.of();
        }
        return session.getMessages();
    }

    @Override
    public void clear(@NonNull String sessionId) {
        Session session = inMemorySessionManager.getById(sessionId);
        if (session != null) {
            session.getMessages().clear();
        }
    }
}
