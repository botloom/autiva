package cn.bitloom.agentic.session;

import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.event.EventConverter;
import cn.bitloom.agentic.memory.InMemoryChatMemory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 Session 管理器，供子智能体会话使用。
 * <p>
 * 与 FileSystemSessionManager 不同：
 * - 纯内存存储，不持久化到磁盘
 * - 不启动 EventBus 消息循环（子 Session 由 TaskTool 直接驱动）
 * - 不注入 MemoryManager（子智能体不需要记忆整理）
 * - 提供 InMemoryChatMemory 供子智能体注册 ChatMemory
 */
@Slf4j
@Component
public class InMemorySessionManager implements ISessionManager {

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    /**
     * -- GETTER --
     *  获取子 Session 专用的 ChatMemory 实例
     */
    @Getter
    private final InMemoryChatMemory chatMemory = new InMemoryChatMemory(this);

    /**
     * 创建子 Session
     *
     * @param agentId         子智能体名称
     * @param parentSessionId 父会话ID（关联主会话）
     * @param type            会话类型（通常为 SUB）
     * @param respType        响应类型
     * @param model           模型类型
     * @return 新创建的子 Session
     */
    @Override
    public Session create(String agentId, String parentSessionId, SessionTypeEnum type, SessionRespTypeEnum respType, ModelTypeEnum model) {
        String sessionId = "sub-" + agentId + "-" + System.currentTimeMillis();

        Session session = Session.builder()
                .id(sessionId)
                .agentId(agentId)
                .parentId(parentSessionId)
                .sessionType(type)
                .respType(respType)
                .model(model)
                .build();

        sessions.put(sessionId, session);
        log.info("[InMemorySessionManager] 创建子 Session: sessionId={}, agentId={}, parentId={}",
                sessionId, agentId, parentSessionId);
        return session;
    }

    @Override
    public Session getById(String sessionId) {
        return sessions.get(sessionId);
    }

    @Override
    public void remove(String sessionId) {
        Session removed = sessions.remove(sessionId);
        if (removed != null) {
            chatMemory.clear(sessionId);
            log.info("[InMemorySessionManager] 移除子 Session: sessionId={}", sessionId);
        }
    }

    @Override
    public void store(String sessionId, List<Message> messages) {
        Session session = sessions.get(sessionId);
        for (Message message : messages) {
            if (message instanceof AssistantMessage assistantMessage) {
                if (assistantMessage.getMetadata().get("finishReason").equals("TOOL_CALLS")) {
                    EventBus.publishOut(EventConverter.fromMessage(sessionId, assistantMessage));
                }
            }
            // 发布 ToolResponseMessage 到 box（Spring AI 流式模式不自动发布）
            if (message instanceof ToolResponseMessage toolResponseMessage) {
                EventBus.publishOut(EventConverter.fromMessage(sessionId, toolResponseMessage));
            }
            session.getMessages().add(message);
        }
    }
}
