package cn.bitloom.agentic.session;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentDefinitionManager;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.event.EventConverter;
import cn.bitloom.agentic.memory.CompactChatMemory;
import cn.bitloom.agentic.memory.MemoryManager;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.tool.Toolkit;
import cn.bitloom.agentic.util.MessageUtil;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.exception.StorageException;
import cn.bitloom.store.Store;
import cn.bitloom.util.JsonUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 会话管理器，支持持久化和消息存储。
 * <p>
 * Session 是唯一的状态源，包含所有会话元数据和运行时状态。
 * 持久化格式为 sessions/{sessionId}/metadata.json + messages.jsonl。
 */
@Slf4j
@Component
public class FileSystemSessionManager implements ISessionManager {

    private final AgentDefinitionManager definitionManager;
    private final ModelFactory modelFactory;
    private final Toolkit toolkit;
    private final CompactChatMemory chatMemory;
    private final MemoryManager memoryManager;
    private final SkillManager skillManager;
    private static final int MAX_CACHED_SESSIONS = 5;
    private static final int RECENT_MESSAGES_COUNT = 50;

    /**
     * Session 内存缓存，使用 LRU 策略（保留最近 MAX_CACHED_SESSIONS 个）。
     * 超出上限时淘汰最久未访问的 Session：停止 SessionRunner + 清理内存消息（磁盘保留）。
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
                        eldest.getValue().setMessages(new ArrayList<>());
                        eldest.getValue().setMemoryBaseOffset(eldest.getValue().getMemoryCursor());
                        log.info("[LRU] 淘汰 Session: {}", eldest.getKey());
                        return true;
                    }
                    return false;
                }
            });
    private final Map<String, Agent> agentCache = new ConcurrentHashMap<>();
    private final Map<String, SessionRunner> runners = new ConcurrentHashMap<>();

    public FileSystemSessionManager(AgentDefinitionManager definitionManager,
                                    ModelFactory modelFactory,
                                    Toolkit toolkit,
                                    @Lazy CompactChatMemory chatMemory,
                                    MemoryManager memoryManager,
                                    SkillManager skillManager) {
        this.definitionManager = definitionManager;
        this.modelFactory = modelFactory;
        this.toolkit = toolkit;
        this.chatMemory = chatMemory;
        this.memoryManager = memoryManager;
        this.skillManager = skillManager;
    }

    /**
     * Init.
     */
    @PostConstruct
    public void init() {
        this.loadAllSessions();
        // 预加载 default 主智能体
        this.getOrCreateAgent(AppConstants.Agents.DEFAULT_AGENT_NAME);
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
     * 激活会话：注入 Agent、加载历史消息、通过 SessionRunner 启动消息循环
     * 仅在用户点击切换到某个 session 时调用。
     * 只加载最近 RECENT_MESSAGES_COUNT 条消息到内存（环形缓冲区），避免全量加载导致内存膨胀。
     */
    public void activate(String sessionId) {
        Session session = sessions.get(sessionId);

        // 注入 Agent
        Agent agent = this.getOrCreateAgent(session.getAgentId());
        session.setAgent(agent);
        // 注入 MemoryManager（供 SessionRunner 处理 MemoryEvent 时调用）
        session.setMemoryManager(memoryManager);

        // 从磁盘加载最新状态
        Session diskState = loadMetadata(sessionId);
        if (diskState != null) {
            // 将磁盘上的持久化字段同步到内存 Session（保留瞬态字段）
            syncPersistentFields(diskState, session);
        }

        // 设置内存偏移量：内存从游标后开始加载
        session.setMemoryBaseOffset(session.getMemoryCursor());

        // 只加载最近 N 条消息（环形缓冲区，不全量加载）
        Path messagesFile = AppConstants.Session.messagesFile(session.getAgentId(), sessionId);
        List<Message> messages = loadRecentMessages(messagesFile, RECENT_MESSAGES_COUNT);
        session.setMessages(messages);

        // 通过 SessionRunner 启动消息循环
        SessionRunner runner = new SessionRunner(session);
        runners.put(sessionId, runner);
        runner.start();
    }

    /**
     * 高效加载文件最后 N 行并反序列化为 Message 列表。
     * 使用环形缓冲区（ArrayDeque），内存只保留最后 N 行，避免全量加载。
     */
    private List<Message> loadRecentMessages(Path file, int count) {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            ArrayDeque<String> lastLines = new ArrayDeque<>(count + 1);
            String line;
            while ((line = reader.readLine()) != null) {
                lastLines.addLast(line);
                if (lastLines.size() > count) {
                    lastLines.removeFirst();
                }
            }
            List<Message> messages = new ArrayList<>(lastLines.size());
            for (String l : lastLines) {
                Message msg = MessageUtil.deserializeMessage(l);
                if (msg != null) {
                    messages.add(msg);
                }
            }
            return messages;
        } catch (IOException e) {
            log.error("加载最近消息失败: {}", file, e);
            return new ArrayList<>();
        }
    }

    /**
     * 从磁盘 messages.jsonl 加载指定行号范围的消息（供"加载更多历史"使用）。
     *
     * @param sessionId 会话ID
     * @param offset    起始行号（含）
     * @param count     加载条数
     * @return 反序列化后的消息列表
     */
    public List<Message> loadMessagesRange(String sessionId, int offset, int count) {
        Session session = sessions.get(sessionId);
        if (session == null || count <= 0 || offset < 0) {
            return List.of();
        }
        Path messagesFile = AppConstants.Session.messagesFile(session.getAgentId(), sessionId);
        if (!Files.exists(messagesFile)) {
            return List.of();
        }
        try (BufferedReader reader = Files.newBufferedReader(messagesFile)) {
            List<Message> messages = new ArrayList<>(count);
            int index = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (index >= offset) {
                    Message msg = MessageUtil.deserializeMessage(line);
                    if (msg != null) {
                        messages.add(msg);
                    }
                    if (messages.size() >= count) {
                        break;
                    }
                }
                index++;
            }
            return messages;
        } catch (IOException e) {
            log.error("加载消息范围失败: sessionId={}, offset={}, count={}", sessionId, offset, count, e);
            return List.of();
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
     * Append message.
     */
    @Override
    public void store(String sessionId, List<Message> messages) {
        Session session = this.sessions.get(sessionId);
        try {
            Path messagesFile = AppConstants.Session.messagesFile(session.getAgentId(), sessionId);
            StringBuilder sb = new StringBuilder();
            for (Message message : messages) {
                if (message instanceof AssistantMessage assistantMessage) {
                    if (assistantMessage.getMetadata().get("finishReason").toString().equals("TOOL_CALLS")) {
                        EventBus.publishOut(EventConverter.fromMessage(sessionId, assistantMessage));
                    }
                }
                // 发布 ToolResponseMessage 到 box（Spring AI 流式模式不自动发布）
                if (message instanceof ToolResponseMessage toolResponseMessage) {
                    EventBus.publishOut(EventConverter.fromMessage(sessionId, toolResponseMessage));
                }
                session.getMessages().add(message);
                sb.append(JsonUtils.toJson(message)).append("\n");
            }
            Files.writeString(messagesFile, sb.toString(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw StorageException.writeError("messages-append", e);
        }
    }

    /**
     * Clear session messages.
     */
    public void clear(String sessionId) {
        try {
            Session session = this.getById(sessionId);
            if (session != null) {
                session.getMessages().clear();
                Path messagesFile = AppConstants.Session.messagesFile(session.getAgentId(), sessionId);
                if (Files.exists(messagesFile)) {
                    Files.writeString(messagesFile, "");
                }
                log.info("清空会话消息记录: {}", sessionId);
            }
        } catch (IOException e) {
            log.error("清空会话消息记录失败: {}", sessionId, e);
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
     * Update state.
     */
    public void updateState(String sessionId, SessionState sessionState) {
        Session session = this.getById(sessionId);
        if (session == null) return;

        session.setSessionState(sessionState);
        persistSession(session);
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
     * 初始化 Session 目录和消息文件
     */
    private void initSessionDir(Session session) {
        try {
            Path sessionDir = getSessionDir(session);
            Files.createDirectories(sessionDir);
            Files.createFile(AppConstants.Session.messagesFile(session.getAgentId(), session.getId()));
        } catch (IOException e) {
            throw StorageException.writeError("session-create", e);
        }
    }

    /**
     * 获取或懒加载创建 Agent 实例（主智能体）。
     * Agent 实例按 agentId 缓存，首次访问时构建。
     * 主智能体默认开启 memory 和 compact。
     */
    private Agent getOrCreateAgent(String agentId) {
        return agentCache.computeIfAbsent(agentId, id -> {
            AgentDefinition definition = definitionManager.getOrLoadMainDefinition(id);
            ChatModel chatModel = modelFactory.model(ModelTypeEnum.DEEPSEEK);

            // 记忆文件路径
            java.nio.file.Path memoryPath = AppConstants.MainAgent.memoryFile(id);

            Agent agent = Agent.builder()
                    .name(id)
                    .definition(definition)
                    .model(chatModel)
                    .systemPrompt(definition.content())
                    .tools(toolkit.buildToolCallbacks(definition))
                    .hooks(List.of())
                    .memory(chatMemory)
                    .compact(true)
                    .skillManager(skillManager)
                    .definitionManager(definitionManager)
                    .memoryFilePath(memoryPath)
                    .build();
            log.info("创建主智能体: agentId={}", id);
            return agent;
        });
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
            session.setSavedAt(System.currentTimeMillis());
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
        target.setSessionState(source.getSessionState());
        target.setMessageCount(source.getMessageCount());
        target.setMemoryCursor(source.getMemoryCursor());
        target.setSummary(source.getSummary());
        target.setContextCapacity(source.getContextCapacity());
        target.setCompactionThreshold(source.getCompactionThreshold());
        target.setCurrentContextLength(source.getCurrentContextLength());
        target.setSavedAt(source.getSavedAt());
        target.setShutdownInterrupted(source.isShutdownInterrupted());
        target.setSessionType(source.getSessionType());
        target.setRespType(source.getRespType());
        target.setSource(source.getSource());
    }

    private Path getSessionDir(Session session) {
        return AppConstants.MainAgent.sessionsDir(session.getAgentId()).resolve(session.getId());
    }
}
