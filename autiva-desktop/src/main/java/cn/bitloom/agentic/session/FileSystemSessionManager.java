package cn.bitloom.agentic.session;

import cn.bitloom.agentic.event.AbstractEvent;
import cn.bitloom.agentic.event.CompactionEvent;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.exception.StorageException;
import cn.bitloom.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import cn.bitloom.agentic.session.compaction.CompactionRequest;
import cn.bitloom.agentic.session.compaction.CompactionResult;
import cn.bitloom.agentic.session.compaction.CompactionStrategy;
import cn.bitloom.agentic.session.compaction.CompactionTrigger;
import cn.bitloom.agentic.session.compaction.CompactionUtils;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
@Component
public class FileSystemSessionManager implements ISessionManager {

    /** per-session 可重入锁，保证同一 session 的事件串行处理 */
    private final ConcurrentHashMap<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    @Override
    public <T> T withLock(String sessionId, Supplier<T> action) {
        ReentrantLock lock = sessionLocks.computeIfAbsent(sessionId, k -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Session create(CreateSessionRequest request) {
        String id = request.id() != null ? request.id() : UUID.randomUUID().toString();
        Instant createdAt = Instant.now();

        Map<String, Object> metadata = new HashMap<>(request.metadata());
        if (!metadata.containsKey("title")) metadata.put("title", "新对话");
        if (!metadata.containsKey("updateAt")) metadata.put("updateAt", createdAt.toEpochMilli());
        if (!metadata.containsKey("messageCount")) metadata.put("messageCount", 0);

        Session session = Session.builder()
                .id(id)
                .userId(request.userId())
                .createdAt(createdAt)
                .metadata(metadata)
                .build();

        persistSession(session);
        return session;
    }

    @Nullable
    @Override
    public Session getById(String sessionId) {
        return loadMetadata(sessionId);
    }

    @Nullable
    public Session findById(String sessionId) {
        return getById(sessionId);
    }

    @Override
    public List<Session> findByUserId(String userId) {
        List<Session> result = new ArrayList<>();
        for (Session session : scanAllSessions()) {
            if (userId.equals(session.userId())) {
                result.add(session);
            }
        }
        return result;
    }

    @Override
    public void remove(String sessionId) {
        try {
            Path sessionPath = AppConstants.Session.sessionDir(sessionId);
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
        } catch (IOException e) {
            log.error("删除会话失败: {}", sessionId, e);
            throw StorageException.writeError("session-delete-" + sessionId, e);
        }
    }

    /** code 目录下的保留名（非项目），扫描时跳过 */
    private static final java.util.Set<String> CODE_RESERVED_NAMES = java.util.Set.of("sessions", "memory");

    /**
     * 扫描所有 session 目录（work 模式 + code 模式各项目目录）。
     * - workspace/work/sessions/
     * - workspace/code/{project}/sessions/（跳过 sessions/memory 等保留名）
     */
    private List<Session> scanAllSessions() {
        // work 模式
        List<Session> result = new ArrayList<>(scanSessionDir(AppConstants.Base.WORKSPACE_DIR.resolve("work/sessions")));
        // code 模式（每个项目一个目录，跳过保留名）
        Path codeDir = AppConstants.Base.WORKSPACE_DIR.resolve("code");
        if (Files.exists(codeDir)) {
            try (Stream<Path> dirs = Files.list(codeDir)) {
                dirs.filter(Files::isDirectory)
                    .filter(p -> !CODE_RESERVED_NAMES.contains(p.getFileName().toString()))
                    .forEach(projectDir -> result.addAll(scanSessionDir(projectDir.resolve("sessions"))));
            } catch (IOException e) {
                log.error("扫描 code 项目目录失败: {}", codeDir, e);
            }
        }
        return result;
    }

    /**
     * 扫描单个 sessions 目录，加载所有 session 的 metadata
     */
    private List<Session> scanSessionDir(Path sessionsDir) {
        List<Session> result = new ArrayList<>();
        if (!Files.exists(sessionsDir)) return result;
        try (Stream<Path> sessionDirs = Files.list(sessionsDir)) {
            sessionDirs.filter(Files::isDirectory).forEach(sessionDir -> {
                String sid = sessionDir.getFileName().toString();
                Session session = loadMetadata(sid);
                if (session != null) {
                    result.add(session);
                }
            });
        } catch (IOException e) {
            log.error("列出 sessions 目录失败: {}", sessionsDir, e);
        }
        return result;
    }

    /**
     * 直接将事件以一行 JSON 追加到 events.jsonl 文件。
     * 无内存缓冲：写入即落盘，进程崩溃不丢数据，中断时无需 flush 善后。
     */
    @Override
    public void appendEvent(AbstractEvent event) {
        String sessionId = event.getSessionId();
        try {
            Path eventsFile = AppConstants.Session.eventsFile(sessionId);
            Files.createDirectories(eventsFile.getParent());
            String line = JsonUtils.toJson(event) + "\n";
            Files.writeString(eventsFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("追加事件失败: sessionId={}, eventType={}", sessionId, event.getEventType(), e);
            throw StorageException.writeError("events-append-" + sessionId, e);
        }
    }

    /**
     * 善后被中断的 toolCalls：检查 events.jsonl 末尾，若最后一条事件是
     * 含 toolCalls 的 assistant 消息（缺少对应 ToolResponseMessage），
     * 为每个 toolCall 补一条虚拟 ToolResponse（content 标记被用户中断）。
     */
    @Override
    public void finalizeInterruptedToolCalls(String sessionId) {
        Path eventsFile = AppConstants.Session.eventsFile(sessionId);
        if (!Files.exists(eventsFile)) return;

        List<String> lines;
        try {
            lines = Files.readAllLines(eventsFile);
        } catch (IOException e) {
            log.error("读取事件文件失败: sessionId={}", sessionId, e);
            return;
        }

        // 从末尾往前找最后一条非空行
        int last = -1;
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (!lines.get(i).isBlank()) {
                last = i;
                break;
            }
        }
        if (last < 0) return;

        AbstractEvent lastEvent = deserializeEvent(lines.get(last));
        if (!(lastEvent instanceof MessageEvent me)) return;
        if (!(me.isAssistantMessage() && me.hasToolCalls())) return;

        // 为每个 toolCall 补一条虚拟 ToolResponse，标记为被用户中断
        // 参考 Claude Code 的做法：保持历史成对完整，LLM 能感知中断自然续接
        List<MessageEvent.ToolCallInfo> toolCalls = me.getToolCalls();
        if (toolCalls == null || toolCalls.isEmpty()) return;

        List<MessageEvent.ToolResponseInfo> virtualResponses = toolCalls.stream()
                .map(tc -> new MessageEvent.ToolResponseInfo(
                        tc.id(),
                        tc.name(),
                        "[Tool execution interrupted by user]"))
                .toList();
        MessageEvent toolResponseEvent = MessageEvent.toolResponse(sessionId, virtualResponses);

        try {
            String line = JsonUtils.toJson(toolResponseEvent) + "\n";
            Files.writeString(eventsFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("补虚拟 ToolResponse 善后被中断的 toolCalls: sessionId={}, toolCount={}",
                    sessionId, toolCalls.size());
        } catch (IOException e) {
            log.error("补虚拟 ToolResponse 失败: sessionId={}", sessionId, e);
        }
    }

    @Override
    public List<AbstractEvent> getEvents(String sessionId, EventFilter filter) {
        Session session = getById(sessionId);
        if (session == null) return List.of();

        List<AbstractEvent> allEvents = new ArrayList<>();
        try {
            Path eventsFile = AppConstants.Session.eventsFile(sessionId);
            if (Files.exists(eventsFile)) {
                try (BufferedReader reader = Files.newBufferedReader(eventsFile)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) continue;
                        AbstractEvent se = deserializeEvent(line);
                        if (se != null) allEvents.add(se);
                    }
                }
            }
        } catch (IOException e) {
            log.error("加载事件失败: {}", sessionId, e);
        }

        List<AbstractEvent> matched = allEvents.stream()
                .filter(filter::matches)
                .toList();

        if (filter.lastN() != null && matched.size() > filter.lastN()) {
            matched = matched.subList(matched.size() - filter.lastN(), matched.size());
        } else if (filter.pageSize() != null) {
            int page = filter.page() != null ? filter.page() : 0;
            int from = page * filter.pageSize();
            if (from >= matched.size()) return List.of();
            int to = Math.min(from + filter.pageSize(), matched.size());
            matched = matched.subList(from, to);
        }

        return matched;
    }

    private AbstractEvent deserializeEvent(String line) {
        try {
            return JsonUtils.mapper().readValue(line, AbstractEvent.class);
        } catch (Exception e) {
            log.warn("反序列化事件失败: {}", line, e);
            return null;
        }
    }

    @Override
    public CompactionResult compact(String sessionId, CompactionTrigger trigger, CompactionStrategy strategy) {
        Session session = getById(sessionId);
        if (session == null) return new CompactionResult(List.of(), List.of(), 0);

        List<AbstractEvent> allEvents = getEvents(sessionId, EventFilter.all());
        List<MessageEvent> events = allEvents.stream()
                .filter(e -> e instanceof MessageEvent)
                .map(e -> (MessageEvent) e)
                .toList();
        CompactionRequest request = CompactionRequest.of(session, events);

        if (!trigger.shouldCompact(request)) {
            return new CompactionResult(events, List.of(), 0);
        }

        CompactionResult result = strategy.compact(request);

        // 压缩按 token 截断时，cut 点可能落在同一轮 tool 交互的 assistant(toolCalls) 与
        // ToolResponseMessage 之间，导致两种孤儿：孤儿 ToolResponse（assistant 被归档）或
        // 孤儿 assistant(toolCalls)（ToolResponse 被归档）。统一用 reconcileToolPairs 修复，
        // 所有策略都受益。
        List<MessageEvent> finalActive = CompactionUtils.reconcileToolPairs(
                result.compactedEvents(), result.archivedEvents());

        // 归档事件标记为 archived=true 保留在文件中（而不是物理删除）。
        // 架构设计了 archived 标记机制：
        //   - SessionMemoryAdvisor.before() 用 EventFilter.active() 读取，排除 archived（不发给 LLM）
        //   - prepareHistoricalMessages() 用 EventFilter.all() 读取，包含 archived（UI 能看到完整历史）
        //   - 搜索工具用 all() 读取，能搜到归档事件
        // compactedEvents（synthetic summary + activeWindow）保持 archived=false，
        // 其余旧事件标记为 archived=true。
        Set<String> activeIds = finalActive.stream()
                .map(MessageEvent::getId)
                .collect(java.util.stream.Collectors.toSet());

        List<MessageEvent> toWrite = new ArrayList<>();
        // 先写旧事件中不在 active 集合里的，标记为 archived
        for (AbstractEvent e : allEvents) {
            if (!(e instanceof MessageEvent me)) continue;
            if (activeIds.contains(me.getId())) continue; // 这些会在 finalActive 里写
            if (me.isArchived()) {
                toWrite.add(me); // 已经是 archived，保持
            } else {
                toWrite.add(me.asArchived()); // 标记为 archived
            }
        }
        // 再写 compactedEvents（synthetic summary + activeWindow，archived=false）
        toWrite.addAll(finalActive);

        Path eventsFile = AppConstants.Session.eventsFile(sessionId);
        try {
            StringBuilder sb = new StringBuilder();
            for (MessageEvent e : toWrite) {
                sb.append(JsonUtils.toJson(e)).append("\n");
            }
            Files.writeString(eventsFile, sb.toString());
        } catch (IOException e) {
            log.error("重写 compacted events 失败: {}", sessionId, e);
        }

        log.info("压缩完成: sessionId={}, archived={}, active={}",
                sessionId, result.archivedEvents().size(), finalActive.size());

        // 追加压缩事件，UI 在聊天消息展示处渲染为"上下文已压缩"提示卡片。
        // 非 MessageEvent，SessionMemoryAdvisor.before() 只读 MessageEvent 时自动排除。
        CompactionEvent compactionEvent = CompactionEvent.completed(
                sessionId, strategy.getClass().getSimpleName(),
                result.archivedEvents().size(), finalActive.size());
        appendEvent(compactionEvent);

        return new CompactionResult(finalActive, result.archivedEvents(), result.tokensEstimatedSaved());
    }

    @Nullable
    @Override
    public Session loadMetadata(String sessionId) {
        Path metadataFile = AppConstants.Session.metadataFile(sessionId);
        if (!Files.exists(metadataFile)) return null;
        try {
            String content = Files.readString(metadataFile);
            return JsonUtils.mapper().readValue(content, Session.class);
        } catch (IOException e) {
            log.warn("加载 Session metadata 失败: sessionId={}", sessionId, e);
            return null;
        }
    }

    @Override
    public void persistSession(Session session) {
        Path metadataFile = AppConstants.Session.metadataFile(session.id());
        try {
            Files.createDirectories(metadataFile.getParent());
            Files.writeString(metadataFile, JsonUtils.toJson(session));
            log.debug("持久化 Session: sessionId={}", session.id());
        } catch (IOException e) {
            log.warn("持久化 Session 失败: sessionId={}", session.id(), e);
        }
    }
}
