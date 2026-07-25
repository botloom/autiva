package cn.bitloom.agentic.session;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentDefinitionManager;
import cn.bitloom.agentic.event.AbstractEvent;
import cn.bitloom.agentic.event.EventConverter;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.gene.GeneStore;
import cn.bitloom.agentic.evolve.inject.GeneInjector;
import cn.bitloom.agentic.memory.FileSystemChatMemory;
import cn.bitloom.agentic.memory.MemoryManager;
import cn.bitloom.agentic.memory.TurnBufferedChatMemory;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.tool.Toolkit;
import cn.bitloom.agentic.trace.TraceHook;
import cn.bitloom.agentic.verify.VerificationHook;
import cn.bitloom.agentic.verify.grader.LlmGrader;
import cn.bitloom.agentic.verify.grader.OutputGrader;
import cn.bitloom.agentic.verify.grader.ToolGrader;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.exception.StorageException;
import cn.bitloom.store.Store;
import cn.bitloom.util.JsonUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 会话管理器，支持持久化和事件存储。
 * <p>
 * Session 是唯一的状态源，包含所有会话元数据和运行时状态。
 * 持久化格式为 sessions/{sessionId}/metadata.json + events.jsonl。
 */
@Slf4j
@Component
public class FileSystemSessionManager implements ISessionManager {

    private final AgentDefinitionManager definitionManager;
    private final ModelFactory modelFactory;
    private final Toolkit toolkit;
    private final MemoryManager memoryManager;
    private final SkillManager skillManager;
    private final GeneStore geneStore;
    private final LlmGrader llmGrader;
    private final List<ToolGrader> toolGraders;
    private final List<OutputGrader> outputGraders;
    private final EvolveConfig evolveConfig;
    private final TraceHook traceHook;
    private final GeneInjector geneInjector;
    private static final int MAX_CACHED_SESSIONS = 5;

    /**
     * Session 内存缓存，使用 LRU 策略（保留最近 MAX_CACHED_SESSIONS 个）。
     * 超出上限时淘汰最久未访问的 Session：停止 SessionRunner（磁盘事件保留）。
     */
    private final Map<String, Session> sessions = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Session> eldest) {
                    if (size() > MAX_CACHED_SESSIONS) {
                        SessionRunner runner = runners.remove(eldest.getKey());
                        if (runner != null) {
                            runner.stop();
                        }
                        log.info("[LRU] 淘汰 Session: {}", eldest.getKey());
                        return true;
                    }
                    return false;
                }
            });
    private final Map<String, SessionRunner> runners = new ConcurrentHashMap<>();

    public FileSystemSessionManager(AgentDefinitionManager definitionManager,
                                    ModelFactory modelFactory,
                                    Toolkit toolkit,
                                    MemoryManager memoryManager,
                                    SkillManager skillManager,
                                    GeneStore geneStore,
                                    LlmGrader llmGrader,
                                    List<ToolGrader> toolGraders,
                                    List<OutputGrader> outputGraders,
                                    EvolveConfig evolveConfig,
                                    TraceHook traceHook,
                                    GeneInjector geneInjector) {
        this.definitionManager = definitionManager;
        this.modelFactory = modelFactory;
        this.toolkit = toolkit;
        this.memoryManager = memoryManager;
        this.skillManager = skillManager;
        this.geneStore = geneStore;
        this.llmGrader = llmGrader;
        this.toolGraders = toolGraders;
        this.outputGraders = outputGraders;
        this.evolveConfig = evolveConfig;
        this.traceHook = traceHook;
        this.geneInjector = geneInjector;
    }

    /**
     * Init.
     */
    @PostConstruct
    public void init() {
        this.loadAllSessions();
    }

    /**
     * 从 workspace/{agentId}/sessions/ 加载所有会话的 metadata.json
     */
    private void loadAllSessions() {
        try (Stream<Path> agentDirs = Files.list(AppConstants.Base.WORKSPACE_DIR)) {
            agentDirs.filter(Files::isDirectory)
                    .forEach(agentDir -> {
                        Path sessionsDir = AppConstants.MainAgent.sessionsDir(agentDir.getFileName().toString());
                        if (!Files.exists(sessionsDir)) return;
                        try (Stream<Path> sessionDirs = Files.list(sessionsDir)) {
                            sessionDirs.filter(Files::isDirectory)
                                    .forEach(sessionDir -> {
                                        String dirAgentId = agentDir.getFileName().toString();
                                        String dirSessionId = sessionDir.getFileName().toString();
                                        Path metadataFile = AppConstants.Session.metadataFile(dirAgentId, dirSessionId);
                                        try {
                                            if (!Files.exists(metadataFile)) return;
                                            String content = Files.readString(metadataFile);
                                            Session session = JsonUtils.fromJson(content, Session.class);
                                            if (session == null || session.getId() == null) return;
                                            sessions.put(session.getId(), session);
                                        } catch (IOException e) {
                                            log.error("加载会话状态失败: {}", sessionDir, e);
                                        }
                                    });
                        } catch (IOException e) {
                            log.error("列出 sessions 目录失败: {}", sessionsDir, e);
                        }
                    });
        } catch (IOException e) {
            log.error("加载会话列表失败", e);
        }
    }

    /**
     * 创建新的桌面端 Session（UUID 格式 ID）
     * 实现 ISessionManager 接口，parentSessionId 在主会话场景下为 null。
     */
    @Override
    public Session create(String agentId, String parentSessionId, SessionTypeEnum type, SessionRespTypeEnum respType, ModelTypeEnum model) {
        String sessionId = agentId + "-" + type + "-" + "desktopApp" + "-" + Store.userId.get() + "-" + System.currentTimeMillis();

        Session session = Session.builder()
                .id(sessionId)
                .agentId(agentId)
                .parentId(parentSessionId)
                .sessionType(type)
                .respType(respType)
                .source("desktopApp")
                .model(model)
                .build();

        initSessionDir(session);
        persistSession(session);
        this.sessions.put(sessionId, session);
        this.activate(sessionId);

        return session;
    }

    /**
     * 激活会话：per-session 构建 Agent + FileChatMemory，通过 SessionRunner 启动消息循环。
     * 幂等设计：已激活（runner 存在且未停止）则直接返回，不重复创建。
     * 按 sessionId 加锁，防止并发调用创建多个 SessionRunner。
     */
    @Override
    public void activate(String sessionId) {
        synchronized (sessionId.intern()) {
            // 已激活则跳过，不重复创建 SessionRunner
            SessionRunner existing = runners.get(sessionId);
            if (existing != null && !existing.isStopped()) {
                log.debug("[activate] session 已激活，跳过: {}", sessionId);
                return;
            }
            // 移除已停止的旧 runner
            runners.remove(sessionId);

            Session session = sessions.get(sessionId);

            // 从磁盘加载最新状态
            Session diskState = loadMetadata(sessionId);
            if (diskState != null) {
                syncPersistentFields(diskState, session);
            }

            // 创建 per-session FileChatMemory（本轮缓冲 + 批量 flush）
            FileSystemChatMemory chatMemory = new FileSystemChatMemory(this, session);

            // per-session 构建 Agent（对齐 netInsight，不再缓存 Agent 实例）
            Agent agent = this.buildAgent(session.getAgentId(), chatMemory);

            // 通过 SessionRunner 启动消息循环
            SessionRunner runner = new SessionRunner(session, agent, memoryManager, chatMemory, this);
            runners.put(sessionId, runner);
            runner.start();
        }
    }

    // ===== 事件化存储方法 =====

    /**
     * 持久化事件到 events.jsonl（只写入 persist=true 的事件）。
     * 每行格式：{"eventType":"MESSAGE", ...事件字段...}（由 @JsonTypeInfo 自动写入）
     */
    public void storeEvents(String sessionId, List<? extends AbstractEvent> events) {
        Session session = this.sessions.get(sessionId);
        if (session == null) return;
        try {
            Path eventsFile = AppConstants.Session.eventsFile(session.getAgentId(), sessionId);
            StringBuilder sb = new StringBuilder();
            for (AbstractEvent event : events) {
                if (!event.isPersist()) continue;
                // @JsonTypeInfo 自动写入 eventType 字段，无需手动添加 @type
                sb.append(JsonUtils.toJson(event)).append("\n");
            }
            if (!sb.isEmpty()) {
                Files.writeString(eventsFile, sb.toString(), StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            throw StorageException.writeError("events-append", e);
        }
    }

    /**
     * 从 events.jsonl 加载指定行号范围的事件。
     *
     * @param sessionId 会话ID
     * @param offset    起始行号（含，0-based）
     * @param count     加载条数（Integer.MAX_VALUE 表示全部）
     * @return 反序列化后的事件列表
     */
    public List<AbstractEvent> loadEvents(String sessionId, int offset, int count) {
        Session session = sessions.get(sessionId);
        if (session == null || count <= 0 || offset < 0) {
            return List.of();
        }
        Path eventsFile = AppConstants.Session.eventsFile(session.getAgentId(), sessionId);
        if (!Files.exists(eventsFile)) {
            return List.of();
        }
        try (BufferedReader reader = Files.newBufferedReader(eventsFile)) {
            List<AbstractEvent> events = new ArrayList<>();
            int index = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (index >= offset) {
                    AbstractEvent event = deserializeEvent(line);
                    if (event != null) {
                        events.add(event);
                    }
                    if (events.size() >= count) {
                        break;
                    }
                }
                index++;
            }
            return events;
        } catch (IOException e) {
            log.error("加载事件失败: sessionId={}, offset={}, count={}", sessionId, offset, count, e);
            return List.of();
        }
    }

    /**
     * 从 events.jsonl 加载 MessageEvent 并转为 Spring AI Message（供 LLM 上下文加载）。
     */
    public List<Message> loadEventsAsMessages(String sessionId, int offset, int count) {
        List<AbstractEvent> events = loadEvents(sessionId, offset, count);
        List<Message> messages = new ArrayList<>();
        for (AbstractEvent event : events) {
            if (event instanceof MessageEvent me) {
                messages.add(EventConverter.toMessage(me));
            }
        }
        return messages;
    }

    /**
     * 统计 events.jsonl 总行数。
     */
    public int countEvents(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) return 0;
        Path eventsFile = AppConstants.Session.eventsFile(session.getAgentId(), sessionId);
        if (!Files.exists(eventsFile)) return 0;
        try (BufferedReader reader = Files.newBufferedReader(eventsFile)) {
            int count = 0;
            while (reader.readLine() != null) count++;
            return count;
        } catch (IOException e) {
            log.error("统计事件数失败: sessionId={}", sessionId, e);
            return 0;
        }
    }

    /**
     * 删除 events.jsonl 最后一行（孤儿工具调用清理用）。
     */
    public void removeLastEventLine(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) return;
        Path eventsFile = AppConstants.Session.eventsFile(session.getAgentId(), sessionId);
        if (!Files.exists(eventsFile)) return;
        try {
            List<String> lines = Files.readAllLines(eventsFile);
            if (!lines.isEmpty()) {
                lines.removeLast();
                Files.writeString(eventsFile, lines.isEmpty() ? "" :
                        String.join("\n", lines) + "\n");
            }
        } catch (IOException e) {
            log.error("删除最后一行事件失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 反序列化单行事件 JSON，通过 eventType 字段自动还原子类类型（Jackson 多态）。
     */
    private AbstractEvent deserializeEvent(String line) {
        try {
            // Jackson 多态反序列化：根据 eventType 字段自动还原子类类型
            return JsonUtils.mapper().readValue(line, AbstractEvent.class);
        } catch (Exception e) {
            log.warn("反序列化事件失败: {}", line, e);
            return null;
        }
    }

    /**
     * Gets by id.
     */
    @Override
    public Session getById(String sessionId) {
        return this.sessions.get(sessionId);
    }

    /**
     * 停止会话的消息处理循环（通过 SessionRunner）
     */
    public void stopSession(String sessionId) {
        SessionRunner runner = runners.get(sessionId);
        if (runner != null) {
            runner.stop();
        }
    }


    /**
     * Gets desktop sessions.
     */
    public List<Session> getDesktopSessions() {
        synchronized (this.sessions) {
            return this.sessions.values().stream()
                    .filter(s -> "desktopApp".equals(s.getSource()))
                    .sorted((a, b) -> Long.compare(
                            b.getUpdateAt() != null ? b.getUpdateAt() : b.getCreatedAt(),
                            a.getUpdateAt() != null ? a.getUpdateAt() : a.getCreatedAt()))
                    .toList();
        }
    }

    /**
     * Gets session last active time.
     */
    public long getSessionLastActiveTime(String sessionId) {
        Session session = this.getById(sessionId);
        if (session == null) return 0L;
        return session.getUpdateAt() != null ? session.getUpdateAt() : session.getCreatedAt();
    }

    /**
     * 存储消息：转为事件持久化到 events.jsonl，发布 ToolResponseMessage 到 outBox。
     * TOOL_CALLS 事件由 SessionRunner 发布，此处不重复发布。
     */
    @Override
    public void store(String sessionId, List<Message> messages) {
        // 转为事件（persist=true），持久化到 events.jsonl
        List<MessageEvent> events = messages.stream()
                .map(m -> EventConverter.fromMessage(sessionId, m))
                .peek(e -> e.setPersist(true))
                .toList();
        storeEvents(sessionId, events);
    }

    /**
     * Clear session events.
     */
    public void clear(String sessionId) {
        try {
            Session session = this.getById(sessionId);
            if (session != null) {
                Path eventsFile = AppConstants.Session.eventsFile(session.getAgentId(), sessionId);
                if (Files.exists(eventsFile)) {
                    Files.writeString(eventsFile, "");
                }
                log.info("清空会话事件记录: {}", sessionId);
            }
        } catch (IOException e) {
            log.error("清空会话事件记录失败: {}", sessionId, e);
            throw StorageException.writeError("session-clear-" + sessionId, e);
        }
    }

    /**
     * 实现 ISessionManager 接口的 remove 方法，委托给 delete。
     */
    @Override
    public void remove(String sessionId) {
        delete(sessionId);
    }

    /**
     * Delete session.
     */
    public void delete(String sessionId) {
        try {
            Session session = this.getById(sessionId);
            if (session != null) {
                // 通过 SessionRunner 停止消息处理循环
                SessionRunner runner = runners.remove(sessionId);
                if (runner != null) {
                    runner.stop();
                }

                this.sessions.remove(sessionId);

                Path sessionPath = getSessionDir(session);
                if (Files.exists(sessionPath)) {
                    try (Stream<Path> walk = Files.walk(sessionPath)) {
                        walk.sorted(java.util.Comparator.reverseOrder())
                                .forEach(path -> {
                                    try {
                                        Files.delete(path);
                                    } catch (IOException e) {
                                        log.warn("删除文件失败: {}", path, e);
                                    }
                                });
                    }
                }

                log.info("删除会话: {}", sessionId);
            }
        } catch (IOException e) {
            log.error("删除会话失败: {}", sessionId, e);
            throw StorageException.writeError("session-delete-" + sessionId, e);
        }
    }

    /**
     * 局部更新 Session
     *
     * @param sessionId 会话标识
     * @param updater   更新函数
     */
    public void updateSession(String sessionId, Consumer<Session> updater) {
        Session session = this.getById(sessionId);
        if (session == null) return;

        updater.accept(session);
        persistSession(session);
    }

    // ===== 持久化方法 =====

    /**
     * 从磁盘加载 Session metadata
     */
    private Session loadMetadata(String sessionId) {
        String agentId = resolveAgentId(sessionId);
        Path metadataFile = AppConstants.Session.metadataFile(agentId, sessionId);
        if (!Files.exists(metadataFile)) {
            return null;
        }
        try {
            String content = Files.readString(metadataFile);
            return JsonUtils.fromJson(content, Session.class);
        } catch (IOException e) {
            log.warn("加载 Session metadata 失败: sessionId={}", sessionId, e);
            return null;
        }
    }

    /**
     * 初始化 Session 目录和事件文件
     */
    private void initSessionDir(Session session) {
        try {
            Path sessionDir = getSessionDir(session);
            Files.createDirectories(sessionDir);
            Files.createFile(AppConstants.Session.eventsFile(session.getAgentId(), session.getId()));
        } catch (IOException e) {
            throw StorageException.writeError("session-create", e);
        }
    }

    /**
     * per-session 构建 Agent 实例（对齐 netInsight，不再缓存 Agent）。
     * 每个 Session 拥有独立的 Agent + TurnBufferedChatMemory，避免共享状态。
     * 主智能体默认开启 memory 和 compact。
     *
     * @param agentId    智能体ID
     * @param chatMemory per-session TurnBufferedChatMemory（本轮缓冲 + flush 批量持久化）
     */
    private Agent buildAgent(String agentId, TurnBufferedChatMemory chatMemory) {
        AgentDefinition definition = definitionManager.getOrLoadMainDefinition(agentId);
        ChatModel chatModel = modelFactory.model(ModelTypeEnum.DEEPSEEK);

        // 记忆文件路径
        java.nio.file.Path memoryPath = AppConstants.MainAgent.memoryFile(agentId);

        // 构造 L2 校验 Hook（仅在 definition.verification().enabled() 时生效）
        VerificationHook verificationHook = new VerificationHook(
                geneStore, toolGraders, outputGraders, llmGrader, evolveConfig, traceHook);

        Agent agent = Agent.builder()
                .name(agentId)
                .definition(definition)
                .model(chatModel)
                .systemPrompt(definition.content())
                .tools(toolkit.buildToolCallbacks(definition))
                .hooks(List.of())
                .verificationHook(verificationHook)
                .traceHook(traceHook)
                .geneInjector(geneInjector)
                .memory(chatMemory)
                .compact(true)
                .skillManager(skillManager)
                .definitionManager(definitionManager)
                .memoryFilePath(memoryPath)
                .build();
        log.info("创建主智能体: agentId={} verification={}", agentId, definition.verification().enabled());
        return agent;
    }

    /**
     * 序列化 Session 到磁盘（public，供 TaskTool 创建 Fresh 会话时调用）
     */
    public void persistSession(Session session) {
        String agentId = session.getAgentId();
        if (agentId == null) {
            agentId = resolveAgentId(session.getId());
        }
        Path metadataFile = AppConstants.Session.metadataFile(agentId, session.getId());
        try {
            Files.createDirectories(metadataFile.getParent());
            Files.writeString(metadataFile, JsonUtils.toJson(session));
            log.debug("持久化 Session: sessionId={}", session.getId());
        } catch (IOException e) {
            log.warn("持久化 Session 失败: sessionId={}", session.getId(), e);
        }
    }

    /**
     * 从 sessionId 解析出 agentId
     * sessionId 格式：{agentId}-{type}-{source}-{userId}-{timestamp}
     */
    private String resolveAgentId(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return "default";
        }
        return sessionId.split("-")[0];
    }

    /**
     * 将磁盘上的持久化字段同步到内存 Session（保留瞬态字段）
     */
    private void syncPersistentFields(Session source, Session target) {
        target.setUserId(source.getUserId());
        target.setModel(source.getModel());
        target.setTitle(source.getTitle());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdateAt(source.getUpdateAt());
        target.setMessageCount(source.getMessageCount());
        target.setMemoryCursor(source.getMemoryCursor());
        target.setSummary(source.getSummary());
        target.setContextCapacity(source.getContextCapacity());
        target.setCompactionThreshold(source.getCompactionThreshold());
        target.setCurrentContextLength(source.getCurrentContextLength());
        target.setSessionType(source.getSessionType());
        target.setRespType(source.getRespType());
        target.setSource(source.getSource());
    }

    private Path getSessionDir(Session session) {
        return AppConstants.MainAgent.sessionsDir(session.getAgentId()).resolve(session.getId());
    }
}
