package cn.bitloom.agentic.session;

import cn.bitloom.agentic.agent.AgentIdentityEnum;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.config.ConfigManager;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.exception.StorageException;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionManager {

    private final String METADATA_FILE = "metadata.json";
    private final ConfigManager configManager;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger subagentIdCounter = new AtomicInteger(0);

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

    private Path getChannelFilePath(String sessionId, MessageChannel channel) {
        return AppConstants.Base.SESSION_DIR.resolve(sessionId).resolve(channel.name() + ".jsonl");
    }

    private void loadAllSessions() {
        try (Stream<Path> dirs = Files.list(AppConstants.Base.SESSION_DIR)) {
            dirs.filter(Files::isDirectory)
                    .forEach(sessionDir -> {
                        Path metadataFile = sessionDir.resolve(this.METADATA_FILE);
                        try {
                            String metadata = Files.readString(metadataFile);
                            Session session = JSON.parseObject(metadata, Session.class);
                            for (MessageChannel channel : MessageChannel.values()) {
                                Path channelFile = getChannelFilePath(session.getId(), channel);
                                if (Files.exists(channelFile)) {
                                    try (BufferedReader reader = Files.newBufferedReader(channelFile)) {
                                        ArrayList<Message> messages = new ArrayList<>();
                                        reader.lines().forEach(line -> {
                                            Message message = deserializeMessage(line);
                                            if (message != null) {
                                                messages.add(message);
                                            }
                                        });
                                        session.getChannelMessages(channel).addAll(messages);
                                    }
                                }
                            }
                            sessions.put(session.getId(), session);
                            if (session.getState() != SessionState.IDLE) {
                                session.setState(SessionState.IDLE);
                                persistMetadata(session);
                            }
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
                case TOOL -> ToolResponseMessage.builder()
                        .responses(obj.getList("responses", ToolResponseMessage.ToolResponse.class))
                        .metadata(obj.getObject("metadata", new TypeReference<Map<String, Object>>() {}))
                        .build();
                case SYSTEM -> SystemMessage.builder()
                        .text(obj.getString("text"))
                        .metadata(obj.getObject("metadata", new TypeReference<Map<String, Object>>() {}))
                        .build();
                default -> null;
            };
        } catch (Exception e) {
            log.warn("反序列化消息失败: {}, 错误: {}", json, e.getMessage());
            return null;
        }
    }

    public Session getOrCreate(AgentIdentityEnum agentId, String source, SessionTypeEnum type, SessionRespTypeEnum respType, ModelTypeEnum model, String target) {
        try {
            String sessionId = agentId + "-" + type + "-" + source + "-" + target;
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
                    .target(target)
                    .createdAt(System.currentTimeMillis())
                    .build();

            Files.createDirectories(AppConstants.Base.SESSION_DIR.resolve(sessionId));

            Path metadataFile = AppConstants.Base.SESSION_DIR.resolve(sessionId).resolve(this.METADATA_FILE);
            if (!Files.exists(metadataFile)) {
                Files.createFile(metadataFile);
            }
            Files.writeString(metadataFile, JSON.toJSONString(session));

            Path userChannelFile = getChannelFilePath(sessionId, MessageChannel.USER);
            if (!Files.exists(userChannelFile)) {
                Files.createFile(userChannelFile);
            }

            this.sessions.put(sessionId, session);

            return session;
        } catch (IOException e) {
            throw StorageException.readError("session-init", e);
        }
    }

    public Session getById(String sessionId) {
        return this.sessions.get(sessionId);
    }

    public List<Session> getByTarget(String target) {
        ArrayList<Session> subSessionList = new ArrayList<>();
        this.sessions.values().forEach(session -> {
            if (session.getTarget().equals(target)) {
                subSessionList.add(session);
            }
        });
        return subSessionList;
    }

    public List<Session> getAllUserSessions() {
        return this.sessions.values().stream()
                .filter(s ->  s.getParentId() == null)
                .toList();
    }

    public List<Session> getDesktopSessions() {
        return this.sessions.values().stream()
                .filter(s -> s.getParentId() == null && "desktopApp".equals(s.getSource()))
                .sorted((a, b) -> {
                    long timeA = getSessionLastActiveTime(a.getId());
                    long timeB = getSessionLastActiveTime(b.getId());
                    return Long.compare(timeB, timeA);
                })
                .toList();
    }

    public long getSessionLastActiveTime(String sessionId) {
        try {
            Path userFile = getChannelFilePath(sessionId, MessageChannel.USER);
            if (Files.exists(userFile)) {
                return Files.getLastModifiedTime(userFile).toMillis();
            }
        } catch (IOException e) {
            log.warn("获取session最后活跃时间失败: {}", sessionId, e);
        }
        Session session = this.getById(sessionId);
        return session != null ? session.getCreatedAt() : 0L;
    }

    public void appendMessage(String sessionId, MessageChannel channel, List<Message> messages) {
        List<String> sessionIdList = new ArrayList<>();
        SessionIsolationEnum isolation = configManager.getIsolation();
        switch (isolation) {
            case PER_PEER:
                Session session = this.getById(sessionId);
                if (session == null) return;
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
            Session targetSession = this.sessions.get(id);
            if (targetSession == null) {
                continue;
            }
            targetSession.getChannelMessages(channel).addAll(messages);

            if (channel.shouldPublishToOutBox()) {
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
                }
            }

            try {
                Path channelFile = getChannelFilePath(id, channel);
                if (!Files.exists(channelFile)) {
                    Files.createFile(channelFile);
                }
                for (Message message : messages) {
                    Files.writeString(channelFile, JSON.toJSONString(message), StandardOpenOption.APPEND);
                    Files.writeString(channelFile, "\n", StandardOpenOption.APPEND);
                }
            } catch (IOException e) {
                throw StorageException.writeError("messages-append", e);
            }
        }
    }

    public Session forkSession(String parentSessionId, String subagentName) {
        String childSessionId = parentSessionId + "_" + subagentIdCounter.getAndIncrement();
        Session parentSession = this.getById(parentSessionId);
        Session childSession = Session.builder()
                .id(childSessionId)
                .agentId(parentSession != null ? parentSession.getAgentId() : AgentIdentityEnum.MAIN)
                .type(parentSession != null ? parentSession.getType() : SessionTypeEnum.DM)
                .respType(SessionRespTypeEnum.STREAM)
                .source(parentSession != null ? parentSession.getSource() : "subagent")
                .target(subagentName)
                .parentId(parentSessionId)
                .build();

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
            Files.createDirectories(AppConstants.Base.SESSION_DIR.resolve(childSessionId));

            Path metadataFile = AppConstants.Base.SESSION_DIR.resolve(childSessionId).resolve(this.METADATA_FILE);
            if (!Files.exists(metadataFile)) {
                Files.createFile(metadataFile);
            }
            Files.writeString(metadataFile, JSON.toJSONString(childSession));

            Path userChannelFile = getChannelFilePath(childSessionId, MessageChannel.USER);
            if (!Files.exists(userChannelFile)) {
                Files.createFile(userChannelFile);
            }

        } catch (IOException e) {
            log.error("子session持久化化失败", e);
            throw StorageException.writeError("session-fork", e);
        }

        this.sessions.put(childSessionId, childSession);
        return childSession;
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
                session.getAllChannelMessages().clear();
                Path sessionPath = AppConstants.Base.SESSION_DIR.resolve(sessionId);
                for (MessageChannel channel : MessageChannel.values()) {
                    Path channelFile = sessionPath.resolve(channel.name() + ".jsonl");
                    if (Files.exists(channelFile)) {
                        Files.writeString(channelFile, "");
                    }
                }
                log.info("清空会话消息记录: {}", sessionId);
            }
        } catch (IOException e) {
            log.error("清空会话消息记录失败: {}", sessionId, e);
            throw StorageException.writeError("session-clear-" + sessionId, e);
        }
    }

    public void deleteSession(String sessionId) {
        try {
            Session session = this.getById(sessionId);
            if (session != null) {
                this.sessions.remove(sessionId);
                Path sessionPath = AppConstants.Base.SESSION_DIR.resolve(sessionId);
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

    public void updateCursor(String sessionId, String cursorField, int value) {
        Session session = this.getById(sessionId);
        if (session == null) return;

        switch (cursorField) {
            case "memoryCursor" -> session.setMemoryCursor(value);
            case "journalCursor" -> session.setJournalCursor(value);
        }

        persistMetadata(session);
    }

    public void updateState(String sessionId, SessionState state) {
        Session session = this.getById(sessionId);
        if (session == null) return;

        session.setState(state);
        persistMetadata(session);
    }

    private void persistMetadata(Session session) {
        Path metadataFile = AppConstants.Base.SESSION_DIR.resolve(session.getId()).resolve(this.METADATA_FILE);
        try {
            Files.writeString(metadataFile, JSON.toJSONString(session));
        } catch (IOException e) {
            log.error("持久化metadata失败: sessionId={}", session.getId(), e);
        }
    }

}
