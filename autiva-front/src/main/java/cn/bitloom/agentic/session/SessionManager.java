package cn.bitloom.agentic.session;

import cn.bitloom.agentic.agent.AgentIdentityEnum;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.config.ConfigManager;
import cn.bitloom.constant.AppConstants;
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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * The type Session manager.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionManager {

    private final String METADATA_FILE = "metadata.json";
    private final String MESSAGES_FILE = "messages.jsonl";
    private final ConfigManager configManager;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger subagentIdCounter = new AtomicInteger(0);

    /**
     * Init.
     */
    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(AppConstants.Base.SESSION_DIR);
            this.loadAllSessions();
            this.recoverSubagentCounter();
        } catch (IOException e) {
            log.error("初始化SessionManager失败", e);
        }
    }

    private void recoverSubagentCounter() {
        int maxCounter = this.sessions.values().stream()
                .filter(s -> s.getParentId() != null)
                .mapToInt(s -> {
                    String id = s.getId();
                    int lastUnderscore = id.lastIndexOf('_');
                    if (lastUnderscore >= 0) {
                        try {
                            return Integer.parseInt(id.substring(lastUnderscore + 1));
                        } catch (NumberFormatException e) {
                            return -1;
                        }
                    }
                    return -1;
                })
                .filter(c -> c >= 0)
                .max()
                .orElse(-1);
        if (maxCounter >= 0) {
            this.subagentIdCounter.set(maxCounter + 1);
            log.info("恢复子智能体计数器: {}", this.subagentIdCounter.get());
        }
    }

    private void loadAllSessions() {
        try (Stream<Path> dirs = Files.list(AppConstants.Base.SESSION_DIR)) {
            dirs.filter(Files::isDirectory)
                    .forEach(sessionDir -> {
                        Path metadataFile = sessionDir.resolve(this.METADATA_FILE);
                        Path messagesFile = sessionDir.resolve(this.MESSAGES_FILE);
                        try {
                            String metadata = Files.readString(metadataFile);
                            Session session = JSON.parseObject(metadata, Session.class);
                            try (BufferedReader reader = Files.newBufferedReader(messagesFile)) {
                                ArrayList<Message> messages = new ArrayList<>();
                                reader.lines().forEach(line -> {
                                    Message message = deserializeMessage(line);
                                    if (message != null) {
                                        messages.add(message);
                                    }
                                });
                                session.setMessages(messages);
                            }
                            sessions.put(session.getId(), session);
                            log.debug("加载会话: {}", session.getId());
                        } catch (IOException e) {
                            log.error("加载会话失败: {}", sessionDir, e);
                        }
                    });
        } catch (IOException e) {
            log.error("加载会话列表失败", e);
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
                case TOOL -> JSON.parseObject(json, ToolResponseMessage.class);
                default -> null;
            };
        } catch (Exception e) {
            log.warn("反序列化消息失败: {}, 错误: {}", json, e.getMessage());
            return null;
        }
    }

    /**
     * Gets or create.
     *
     * @param source   the source
     * @param type     the type
     * @param respType the resp type
     * @param target   the target
     * @return the or create
     */
    public Session getOrCreate(String source, SessionTypeEnum type, SessionRespTypeEnum respType, String target) {
        try {
            Optional<String> sessionIdOption = this.sessions.keySet().stream()
                    .filter(sessionId -> sessionId.contains(type + "-" + source + "-" + target))
                    .findAny();
            if (sessionIdOption.isPresent()) {
                return sessions.get(sessionIdOption.get());
            } else {
                String sessionId = AgentIdentityEnum.MAIN + "-" + type + "-" + source + "-" + target;
                Session session = Session.builder()
                        .id(sessionId)
                        .agentId(AgentIdentityEnum.MAIN)
                        .type(type)
                        .respType(respType)
                        .source(source)
                        .target(target)
                        .build();

                Files.createDirectories(AppConstants.Base.SESSION_DIR.resolve(sessionId));

                Path metadataFile = AppConstants.Base.SESSION_DIR.resolve(sessionId).resolve(this.METADATA_FILE);
                if (!Files.exists(metadataFile)) {
                    Files.createFile(metadataFile);
                }
                Files.writeString(metadataFile, JSON.toJSONString(session));

                Path messagesFile = AppConstants.Base.SESSION_DIR.resolve(sessionId).resolve(this.MESSAGES_FILE);
                if (!Files.exists(messagesFile)) {
                    Files.createFile(messagesFile);
                }

                this.sessions.put(sessionId, session);
                return session;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
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
     * Gets by target.
     *
     * @param target the target
     * @return the by target
     */
    public List<Session> getByTarget(String target) {
        ArrayList<Session> subSessionList = new ArrayList<>();
        this.sessions.values().forEach(session -> {
            if (session.getTarget().equals(target)) {
                subSessionList.add(session);
            }
        });
        return subSessionList;
    }

    /**
     * Append message.
     *
     * @param sessionId the session id
     * @param messages  the messages
     */
    public void appendMessage(String sessionId, List<Message> messages) {
        List<String> sessionIdList = new ArrayList<>();
        SessionIsolationEnum isolation = configManager.getIsolation();
        switch (isolation) {
            case PER_PEER:
                Session session = this.getById(sessionId);
                List<Session> sessions = this.getByTarget(session.getTarget());
                sessions.stream().map(Session::getId).forEach(sessionIdList::add);
                break;
            case PER_CHANNEL_PEER:
                sessionIdList.add(sessionId);
                break;
            default:
                return;
        }
        for (String id : sessionIdList) {
            try {
                this.sessions.get(id).getMessages().addAll(messages);
                Path sessionPath = AppConstants.Base.SESSION_DIR.resolve(id);
                Path messagesFile = sessionPath.resolve(this.MESSAGES_FILE);
                for (Message message : messages) {
                    if (message instanceof AssistantMessage assistantMessage) {
                        if (assistantMessage.getMetadata().get("finishReason").equals("TOOL_CALLS")) {
                            EventBus.outBoxPublish(
                                    id,
                                    AssistantMessage.builder()
                                            .content("")
                                            .properties(assistantMessage.getMetadata())
                                            .toolCalls(assistantMessage.getToolCalls())
                                            .build()
                            );
                        }
                    }
                    if (message instanceof ToolResponseMessage toolResponseMessage) {
                        EventBus.outBoxPublish(id, toolResponseMessage);
                    }
                    Files.writeString(messagesFile, JSON.toJSONString(message), StandardOpenOption.APPEND);
                    Files.writeString(messagesFile, "\n", StandardOpenOption.APPEND);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Clear session messages.
     *
     * @param sessionId the session id
     */
    public Session forkSession(String parentSessionId, String subagentType) {
        String childSessionId = parentSessionId + "_" + subagentIdCounter.getAndIncrement();
        Session parentSession = this.getById(parentSessionId);
        Session childSession = Session.builder()
                .id(childSessionId)
                .agentId(parentSession != null ? parentSession.getAgentId() : AgentIdentityEnum.MAIN)
                .type(parentSession != null ? parentSession.getType() : SessionTypeEnum.DM)
                .respType(SessionRespTypeEnum.STREAM)
                .source(parentSession != null ? parentSession.getSource() : "subagent")
                .target(subagentType)
                .parentId(parentSessionId)
                .build();

        try {
            Path sessionDir = AppConstants.Base.SESSION_DIR.resolve(childSessionId);
            Files.createDirectories(sessionDir);
            Path metadataFile = sessionDir.resolve(this.METADATA_FILE);
            if (!Files.exists(metadataFile)) {
                Files.createFile(metadataFile);
            }
            Files.writeString(metadataFile, JSON.toJSONString(childSession));
            Path messagesFile = sessionDir.resolve(this.MESSAGES_FILE);
            if (!Files.exists(messagesFile)) {
                Files.createFile(messagesFile);
            }
            this.sessions.put(childSessionId, childSession);
            log.info("Fork子会话: {} -> {}", parentSessionId, childSessionId);
            return childSession;
        } catch (IOException e) {
            throw new RuntimeException("Fork子会话失败: " + childSessionId, e);
        }
    }

    public List<Session> getChildSessions(String parentSessionId) {
        return this.sessions.values().stream()
                .filter(s -> parentSessionId.equals(s.getParentId()))
                .toList();
    }

    public void clearSessionMessages(String sessionId) {
        try {
            Session session = this.getById(sessionId);
            if (session != null) {
                // 清空内存中的消息列表
                session.getMessages().clear();
                // 清空消息文件
                Path sessionPath = AppConstants.Base.SESSION_DIR.resolve(sessionId);
                Path messagesFile = sessionPath.resolve(this.MESSAGES_FILE);
                Files.writeString(messagesFile, "");
                log.info("清空会话消息记录: {}", sessionId);
            }
        } catch (IOException e) {
            log.error("清空会话消息记录失败: {}", sessionId, e);
            throw new RuntimeException(e);
        }
    }

}
