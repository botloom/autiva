package cn.bitloom.agentic.session;

import cn.bitloom.agentic.model.ModelTypeEnum;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Session 管理器统一接口。
 * <p>
 * 不同的实现提供不同的存储策略：
 * - FileSystemSessionManager：磁盘持久化，用于主会话
 * - InMemorySessionManager：纯内存，用于子智能体会话
 */
public interface ISessionManager {

    /**
     * 创建新的 Session
     *
     * @param agentId         智能体标识
     * @param parentSessionId 父会话ID（子 Session 关联父 Session，主会话传 null）
     * @param type            会话类型
     * @param respType        响应类型
     * @param model           模型类型
     * @return 新创建的 Session
     */
    Session create(String agentId, String parentSessionId, SessionTypeEnum type, SessionRespTypeEnum respType, ModelTypeEnum model);

    /**
     * 根据 ID 获取 Session
     */
    Session getById(String sessionId);

    /**
     * 移除 Session
     */
    void remove(String sessionId);

    /**
     * 存储消息到 Session
     */
    void store(String sessionId, List<Message> messages);
}
