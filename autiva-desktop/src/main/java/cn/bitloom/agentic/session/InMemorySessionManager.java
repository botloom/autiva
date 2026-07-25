package cn.bitloom.agentic.session;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentDefinitionManager;
import cn.bitloom.agentic.agent.AgentKind;
import cn.bitloom.agentic.memory.InMemoryChatMemory;
import cn.bitloom.agentic.memory.TurnBufferedChatMemory;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.tool.Toolkit;
import cn.bitloom.exception.AgentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 Session 管理器，供子智能体会话使用。
 * <p>
 * 与 FileSystemSessionManager 不同：
 * - 纯内存存储，不持久化到磁盘
 * - 启动 SessionRunner 消息循环（与主会话对齐，子智能体也走 EventBus 模式）
 * - 不注入 MemoryManager（子智能体不需要记忆整理，传 null）
 * - per-session 创建 InMemoryChatMemory + Agent（对齐 FileSystemSessionManager 的 per-session 模式）
 * - 自管理 messageStore，不依赖 Session.messages（已移除）
 */
@Slf4j
@Component
public class InMemorySessionManager implements ISessionManager {

    private final AgentDefinitionManager definitionManager;
    private final ModelFactory modelFactory;
    private final Toolkit toolkit;

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, SessionRunner> runners = new ConcurrentHashMap<>();

    public InMemorySessionManager(AgentDefinitionManager definitionManager,
                                  ModelFactory modelFactory,
                                  @Lazy Toolkit toolkit) {
        this.definitionManager = definitionManager;
        this.modelFactory = modelFactory;
        this.toolkit = toolkit;
    }

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
            // 停止对应的 SessionRunner
            SessionRunner runner = runners.remove(sessionId);
            if (runner != null) {
                runner.stop();
            }
            log.info("[InMemorySessionManager] 移除子 Session: sessionId={}", sessionId);
        }
    }

    /**
     * 激活子会话：per-session 创建 InMemoryChatMemory + Agent + SessionRunner，启动消息循环。
     * <p>
     * 幂等设计：已激活（runner 存在且未停止）则直接返回，不重复创建。
     * 按 sessionId 加锁，防止并发调用创建多个 SessionRunner。
     * 与 FileSystemSessionManager.activate 对齐，差异：
     * - 不从磁盘加载状态（纯内存）
     * - MemoryManager 传 null（子智能体不压缩）
     * - per-session InMemoryChatMemory（每个子智能体独立实例，不共享）
     */
    @Override
    public void activate(String sessionId) {
        synchronized (sessionId.intern()) {
            SessionRunner existing = runners.get(sessionId);
            if (existing != null && !existing.isStopped()) {
                log.debug("[InMemorySessionManager][activate] 子 session 已激活，跳过: {}", sessionId);
                return;
            }
            runners.remove(sessionId);

            Session session = sessions.get(sessionId);
            if (session == null) {
                log.warn("[InMemorySessionManager][activate] session 不存在: {}", sessionId);
                return;
            }

            // per-session InMemoryChatMemory（不共享）
            InMemoryChatMemory chatMemory = new InMemoryChatMemory();

            // per-session 构建 Agent
            Agent agent = buildAgent(session.getAgentId(), chatMemory);

            SessionRunner runner = new SessionRunner(session, agent, null, chatMemory, this);
            runners.put(sessionId, runner);
            runner.start();
            log.info("[InMemorySessionManager][activate] 子 session 已激活: sessionId={}", sessionId);
        }
    }

    /**
     * 获取指定子会话的 SessionRunner（供 TaskTool 拿 resultFuture 同步等待结果）。
     */
    public SessionRunner getRunner(String sessionId) {
        return runners.get(sessionId);
    }

    /**
     * 持久化 Session 元数据：子会话场景为 no-op（纯内存）。
     */
    @Override
    public void persistSession(Session session) {
        // no-op：子会话不持久化
    }

    /**
     * per-session 构建子智能体 Agent 实例。
     * 与 FileSystemSessionManager.buildAgent 对齐，差异：
     * - 使用 getDefinition（子智能体定义，非主智能体）
     * - 不注入 VerificationHook/GeneInjector/TraceHook/SkillManager（子智能体不需要 L2 校验/Gene/追踪/技能）
     * - 不开启 compact（子智能体不压缩上下文）
     * - 不需要 memoryFilePath（子智能体无 memory.md）
     *
     * @param agentId    子智能体ID
     * @param chatMemory per-session InMemoryChatMemory
     */
    private Agent buildAgent(String agentId, TurnBufferedChatMemory chatMemory) {
        AgentDefinition definition = definitionManager.getDefinition(agentId);
        if (definition == null) {
            throw AgentException.subagentNotFound(agentId);
        }
        if (definition.kind() != AgentKind.SUBAGENT) {
            throw AgentException.subagentNotFound(agentId);
        }
        ChatModel chatModel = modelFactory.model(ModelTypeEnum.DEEPSEEK);

        Agent agent = Agent.builder()
                .name(agentId)
                .definition(definition)
                .model(chatModel)
                .systemPrompt(definition.content())
                .tools(toolkit.buildToolCallbacks(definition))
                .hooks(List.of())
                .memory(chatMemory)
                .build();
        log.info("[InMemorySessionManager] 创建子智能体: agentId={}", agentId);
        return agent;
    }

    @Override
    public void store(String sessionId, List<Message> messages) {

    }

}
