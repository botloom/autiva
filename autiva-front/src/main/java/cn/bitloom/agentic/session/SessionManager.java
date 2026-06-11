package cn.bitloom.agentic.session;

import cn.bitloom.agentic.agent.AgentManager;
import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.message.MessageBus;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.exception.StorageException;
import cn.bitloom.store.Store;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * The type Session manager.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionManager {

    private final AgentManager agentManager;
    private final String METADATA_FILE = "metadata.json";
    private final String MESSAGES_FILE = "messages.json";
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /**
     * Init.
     */
    @PostConstruct
    public void init() {
        this.loadAllSessionMetadata();
    }

    /**
     * 从 workspace/{agentId}/sessions/ 加载所有会话
     */
    private void loadAllSessionMetadata() {
        Path workspaceDir = AppConstants.Base.WORKSPACE_DIR;
        if (!Files.exists(workspaceDir)) {
            return;
        }

        try (Stream<Path> agentDirs = Files.list(workspaceDir)) {
            agentDirs.filter(Files::isDirectory)
                    .forEach(agentDir -> {
                        Path sessionsDir = agentDir.resolve("sessions");
                        if (!Files.exists(sessionsDir) || !Files.isDirectory(sessionsDir)) {
                            return;
                        }
                        try (Stream<Path> sessionDirs = Files.list(sessionsDir)) {
                            sessionDirs.filter(Files::isDirectory)
                                    .forEach(sessionDir -> {
                                        Path metadataFile = sessionDir.resolve(this.METADATA_FILE);
                                        try {
                                            String metadata = Files.readString(metadataFile);
                                            Session session = JSON.parseObject(metadata, Session.class);
                                            sessions.put(session.getId(), session);
                                        } catch (IOException e) {
                                            log.error("加载会话失败: {}", sessionDir, e);
                                        }
                                    });
                        } catch (IOException e) {
                            log.error("列出 agent sessions 目录失败: {}", sessionsDir, e);
                        }
                    });
        } catch (IOException e) {
            log.error("加载会话列表失败", e);
        }
    }

    /**
     * 创建新的桌面端 Session（UUID 格式 ID）
     *
     * @param agentId  the agent id
     * @param source   the source
     * @param type     the type
     * @param respType the resp type
     * @param model    the model
     * @return the session
     */
    public Session create(String agentId, String source, SessionTypeEnum type, SessionRespTypeEnum respType, ModelTypeEnum model) {
        String sessionId = agentId + "-" + type + "-" + source + "-" + Store.userId.get() + "-" + System.currentTimeMillis();

        Session session = Session.builder()
                .id(sessionId)
                .agentId(agentId)
                .type(type)
                .respType(respType)
                .model(model)
                .source(source)
                .build();

        try {
            Path sessionDir = getSessionDir(session);
            Path metadataFile = sessionDir.resolve(this.METADATA_FILE);
            Files.writeString(metadataFile, JSON.toJSONString(session));
            Files.createFile(sessionDir.resolve(this.MESSAGES_FILE));
        } catch (IOException e) {
            throw StorageException.writeError("session-create", e);
        }

        this.sessions.put(sessionId, session);

        this.start(sessionId);

        return session;
    }

    /**
     * 激活会话：设置 EventBus、注入 Agent、启动消息循环、加载历史消息、恢复上下文
     * 仅在用户点击切换到某个 session 时调用
     *
     * @param sessionId the session id
     */
    public void start(String sessionId) {
        Session session = sessions.get(sessionId);
        if (Objects.isNull(session)) {
            return;
        }

        // 如果已经激活（有 EventBus），直接返回
        if (Objects.isNull(session.getMessageBus())) {
            session.setMessageBus(new MessageBus());
        }

        // 注入 Agent 并启动消息循环
        Agent agent = this.agentManager.getAgent(session.getAgentId());
        session.setAgent(agent);
        session.start(this);

        // 加载历史消息
        this.loadMessages(sessionId);

        // 恢复上下文
        this.loadContext(sessionId);
    }

    /**
     * 从 context/{sessionId}/agent_state.json 恢复会话上下文
     *
     * @param sessionId the session id
     */
    public void loadContext(String sessionId) {
        Session session = sessions.get(sessionId);

        try {
            Path stateFile = AppConstants.Base.WORKSPACE_DIR
                    .resolve(session.getAgentId())
                    .resolve("context")
                    .resolve(sessionId)
                    .resolve("agent_state.json");
            //todo
            if (!Files.exists(stateFile)) return;

            String content = Files.readString(stateFile);
            JSONObject state = JSON.parseObject(content);

            if (state.containsKey("memoryCursor")) {
                session.setMemoryCursor(state.getInteger("memoryCursor"));
            }

        } catch (IOException e) {
            log.warn("恢复会话上下文失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * Load messages.
     *
     * @param sessionId the session id
     */
    public void loadMessages(String sessionId) {
        Session session = sessions.get(sessionId);

        Path messagesFile = this.getSessionDir(session).resolve(this.MESSAGES_FILE);

        if (!Files.exists(messagesFile)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(messagesFile)) {
            List<Message> messages = new ArrayList<>();
            reader.lines().forEach(line -> {
                Message message = this.deserializeMessage(line);
                if (message != null) {
                    messages.add(message);
                }
            });
            session.setMessages(messages);
        } catch (IOException e) {
            log.error("加载会话消息失败: {}", sessionId, e);
        }

    }


    private Message deserializeMessage(String json) {
        try {
            JSONObject obj = JSON.parseObject(json);
            String messageTypeStr = obj.getString("messageType");
            if (messageTypeStr == null) {
                log.warn("消息缺少 messageType 字段: {}", json);
                return null;
            }
            MessageType messageType = MessageType.valueOf(messageTypeStr.toUpperCase());
            return switch (messageType) {
                case USER -> UserMessage.builder()
                        .text(obj.getString("text"))
                        .media(obj.getList("media", Media.class))
                        .metadata(obj.getObject("metadata", new TypeReference<Map<String, Object>>() {
                        }))
                        .build();
                case ASSISTANT -> AssistantMessage.builder()
                        .content(obj.getString("text"))
                        .media(obj.getList("media", Media.class))
                        .properties(obj.getObject("metadata", new TypeReference<Map<String, Object>>() {
                        }))
                        .toolCalls(obj.getList("toolCalls", AssistantMessage.ToolCall.class))
                        .build();
                case TOOL -> ToolResponseMessage.builder()
                        .responses(obj.getList("responses", ToolResponseMessage.ToolResponse.class))
                        .metadata(obj.getObject("metadata", new TypeReference<Map<String, Object>>() {
                        }))
                        .build();
                case SYSTEM -> SystemMessage.builder()
                        .text(obj.getString("text"))
                        .metadata(obj.getObject("metadata", new TypeReference<Map<String, Object>>() {
                        }))
                        .build();
            };
        } catch (Exception e) {
            log.warn("反序列化消息失败: {}, 错误: {}", json, e.getMessage());
            return null;
        }
    }

    /**
     * Gets or create.
     *
     * @param agentId  the agent id
     * @param userId   the user id
     * @param source   the source
     * @param type     the type
     * @param respType the resp type
     * @param model    the model
     * @return the or create
     */
    public Session getOrCreate(String agentId, String userId, String source, SessionTypeEnum type, SessionRespTypeEnum respType, ModelTypeEnum model) {
        try {
            String sessionId = agentId + "-" + type + "-" + source + "-" + userId;
            Session existing = this.sessions.get(sessionId);
            if (existing != null) {
                return existing;
            }

            Session session = Session.builder()
                    .id(sessionId)
                    .agentId(agentId)
                    .type(type)
                    .respType(respType)
                    .model(model)
                    .source(source)
                    .build();
            session.setMessageBus(new MessageBus());

            Path sessionDir = getSessionDir(session);
            Files.createDirectories(sessionDir);

            Path metadataFile = sessionDir.resolve(this.METADATA_FILE);
            if (!Files.exists(metadataFile)) {
                Files.createFile(metadataFile);
            }
            Files.writeString(metadataFile, JSON.toJSONString(session));

            this.sessions.put(sessionId, session);

            // 注入对应的 Agent 并启动消息处理循环
            if (this.agentManager != null) {
                injectAgent(session);
                session.start(this);
            }

            return session;
        } catch (IOException e) {
            throw StorageException.readError("session-init", e);
        }
    }

    /**
     * Gets by id.
     *
     * @param sessionId the session id
     * @return the by id
     */
    public Session getById(String sessionId) {
        return this.sessions.get(sessionId);
    }

    /**
     * Gets all user sessions.
     *
     * @return the all user sessions
     */
    public List<Session> getAllUserSessions() {
        return this.sessions.values().stream()
                .filter(s -> s.getParentId() == null)
                .toList();
    }

    /**
     * Gets desktop sessions.
     *
     * @return the desktop sessions
     */
    public List<Session> getDesktopSessions() {
        return this.sessions.values().stream()
                .filter(s -> s.getParentId() == null && "desktopApp".equals(s.getSource()))
                .sorted((a, b) -> Long.compare(
                        b.getUpdateAt() != null ? b.getUpdateAt() : b.getCreatedAt(),
                        a.getUpdateAt() != null ? a.getUpdateAt() : a.getCreatedAt()))
                .toList();
    }

    /**
     * Gets session last active time.
     *
     * @param sessionId the session id
     * @return the session last active time
     */
    public long getSessionLastActiveTime(String sessionId) {
        Session session = this.getById(sessionId);
        if (session == null) return 0L;
        return session.getUpdateAt() != null ? session.getUpdateAt() : session.getCreatedAt();
    }

    /**
     * Append message.
     *
     * @param sessionId the session id
     * @param messages  the messages
     */
    public void appendMessage(String sessionId, List<Message> messages) {
        Session targetSession = this.sessions.get(sessionId);
        if (targetSession == null) {
            return;
        }

        targetSession.setUpdateAt(System.currentTimeMillis());
        targetSession.getMessages().addAll(messages);

        for (Message message : messages) {
            if (message instanceof AssistantMessage assistantMessage) {
                if ("TOOL_CALLS".equals(assistantMessage.getMetadata().get("finishReason"))) {
                    if (targetSession.getMessageBus() != null) {
                        targetSession.getMessageBus().outBoxPublish(
                                AssistantMessage.builder()
                                        .content("")
                                        .properties(assistantMessage.getMetadata())
                                        .toolCalls(assistantMessage.getToolCalls())
                                        .build()
                        );
                    }
                }
            }
            if (message instanceof ToolResponseMessage toolResponseMessage) {
                if (targetSession.getMessageBus() != null) {
                    targetSession.getMessageBus().outBoxPublish(toolResponseMessage);
                }
            }
        }

        try {
            Path messagesFile = getSessionDir(targetSession).resolve("messages.jsonl");
            if (!Files.exists(messagesFile)) {
                Files.createFile(messagesFile);
            }
            StringBuilder sb = new StringBuilder();
            for (Message message : messages) {
                sb.append(JSON.toJSONString(message)).append("\n");
            }
            Files.writeString(messagesFile, sb.toString(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw StorageException.writeError("messages-append", e);
        }

    }

    /**
     * Fork session session.
     *
     * @param parentSessionId the parent session id
     * @param subagentName    the subagent name
     * @return the session
     */
    public Session forkSession(String parentSessionId, String subagentName) {
        String childSessionId = parentSessionId + "-" + subagentName + "-" + System.currentTimeMillis();
        Session parentSession = this.getById(parentSessionId);
        String agentId = parentSession != null ? parentSession.getAgentId() : "default";
        Session childSession = Session.builder()
                .id(childSessionId)
                .agentId(agentId)
                .type(parentSession != null ? parentSession.getType() : SessionTypeEnum.DM)
                .respType(SessionRespTypeEnum.STREAM)
                .source(parentSession != null ? parentSession.getSource() : "subagent")
                .parentId(parentSessionId)
                .build();
        childSession.setMessageBus(new MessageBus());

        if (parentSession != null && !parentSession.getMessages().isEmpty()) {
            List<Message> parentMessages = parentSession.getMessages();
            int lastUserMsgIndex = -1;
            for (int i = parentMessages.size() - 1; i >= 0; i--) {
                if (parentMessages.get(i).getMessageType() == MessageType.USER) {
                    lastUserMsgIndex = i;
                    break;
                }
            }
            if (lastUserMsgIndex >= 0) {
                List<Message> inherited = new ArrayList<>(parentMessages.subList(lastUserMsgIndex, parentMessages.size() - 1));
                childSession.getMessages().addAll(inherited);
            }
        }

        try {
            Path sessionDir = getSessionDir(childSession);
            Files.createDirectories(sessionDir);

            Path metadataFile = sessionDir.resolve(this.METADATA_FILE);
            if (!Files.exists(metadataFile)) {
                Files.createFile(metadataFile);
            }
            Files.writeString(metadataFile, JSON.toJSONString(childSession));

        } catch (IOException e) {
            log.error("子session持久化失败", e);
            throw StorageException.writeError("session-fork", e);
        }

        this.sessions.put(childSessionId, childSession);
        return childSession;
    }

    /**
     * Gets child sessions.
     *
     * @param parentSessionId the parent session id
     * @return the child sessions
     */
    public List<Session> getChildSessions(String parentSessionId) {
        return this.sessions.values().stream()
                .filter(s -> parentSessionId.equals(s.getParentId()))
                .toList();
    }

    /**
     * Clear session messages.
     *
     * @param sessionId the session id
     */
    public void clearSessionMessages(String sessionId) {
        try {
            Session session = this.getById(sessionId);
            if (session != null) {
                session.getMessages().clear();
                Path sessionPath = getSessionDir(session);
                Path messagesFile = sessionPath.resolve("messages.jsonl");
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
     * Delete session.
     *
     * @param sessionId the session id
     */
    public void deleteSession(String sessionId) {
        try {
            Session session = this.getById(sessionId);
            if (session != null) {
                // 停止消息处理循环
                session.stop();

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
     * Update cursor.
     *
     * @param sessionId   the session id
     * @param cursorField the cursor field
     * @param value       the value
     */
    public void updateCursor(String sessionId, String cursorField, int value) {
        Session session = this.getById(sessionId);
        if (session == null) return;

        if ("memoryCursor".equals(cursorField)) {
            session.setMemoryCursor(value);
            persistMetadata(session);
        }
    }

    /**
     * Update state.
     *
     * @param sessionId the session id
     * @param state     the state
     */
    public void updateState(String sessionId, SessionState state) {
        Session session = this.getById(sessionId);
        if (session == null) return;

        session.setState(state);
        persistMetadata(session);
    }

    private void persistMetadata(Session session) {
        Path metadataFile = getSessionDir(session).resolve(this.METADATA_FILE);
        try {
            Files.writeString(metadataFile, JSON.toJSONString(session));
        } catch (IOException e) {
            log.error("持久化metadata失败: sessionId={}", session.getId(), e);
        }
    }

    /**
     * Publish message.
     *
     * @param sessionId the session id
     * @param message   the message
     */
    public void publishMessage(String sessionId, Message message) {
        Session session = sessions.get(sessionId);
        if (session != null && session.getMessageBus() != null) {
            session.getMessageBus().inBoxPublish(message);
        }
    }

    /**
     * Stop session.
     *
     * @param sessionId the session id
     */
    public void stopSession(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session != null && session.getMessageBus() != null) {
            session.getMessageBus().stop();
        }
    }

    /**
     * Is session stop boolean.
     *
     * @param sessionId the session id
     * @return the boolean
     */
    public Boolean isStop(String sessionId) {
        Session session = sessions.get(sessionId);
        return session.isStop();
    }

    /**
     * 为 Session 注入对应的 Agent
     */
    private void injectAgent(Session session) {
        Agent agent = agentManager.getAgent(session.getAgentId());
        if (agent != null) {
            session.setAgent(agent);
        }
    }

    /**
     * 保存会话上下文快照到 context/{sessionId}/agent_state.json
     * 参考 AgentScope 的 Session 机制，将 AgentState 序列化到磁盘
     *
     * @param sessionId the session id
     */
    public void saveContext(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) return;

        try {
            Path contextDir = AppConstants.Base.agentContextDir(session.getAgentId(), sessionId);
            Files.createDirectories(contextDir);

            Path stateFile = contextDir.resolve("agent_state.json");
            JSONObject state = new JSONObject();
            state.put("sessionId", sessionId);
            state.put("agentId", session.getAgentId());
            state.put("memoryCursor", session.getMemoryCursor());
            state.put("messageCount", session.getMessages().size());
            state.put("savedAt", System.currentTimeMillis());

            Files.writeString(stateFile, state.toJSONString());
            log.debug("保存会话上下文: sessionId={}", sessionId);
        } catch (IOException e) {
            log.warn("保存会话上下文失败: sessionId={}", sessionId, e);
        }
    }

    private Path getSessionDir(Session session) {
        return AppConstants.Base.WORKSPACE_DIR.resolve(session.getAgentId()).resolve(session.getId());
    }

}
