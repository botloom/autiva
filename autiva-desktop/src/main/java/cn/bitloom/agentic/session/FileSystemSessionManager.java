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
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
@Component
public class FileSystemSessionManager implements ISessionManager {

    /** per-session 可重入锁，保证同一 session 的事件串行处理 */
    private final ConcurrentHashMap<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    /** 每轮消息缓冲：sessionId → 待刷盘的事件列表 */
    private final Map<String, CopyOnWriteArrayList<AbstractEvent>> pendingEvents = new ConcurrentHashMap<>();

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
     * 缓冲 key：root 事件用 sessionId，子智能体事件用 sessionId@branch。
     * 这样主智能体和子智能体的缓冲隔离，各自的 STOP 只刷自己的缓冲，
     * 不会把对方未完成的 turn 提前刷盘。
     */
    private String bufferKey(String sessionId, String branch) {
        return branch == null ? sessionId : sessionId + "@" + branch;
    }

    /**
     * 将事件写入内存缓冲，而非直接落盘。
     * 按 branch 隔离缓冲：主智能体（branch=null）和子智能体（branch!=null）各自独立缓冲。
     * 当检测到该 branch 本轮对话结束（追加了 finishReason=STOP 的 assistant 消息）时自动刷盘。
     */
    @Override
    public void appendEvent(AbstractEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId == null) return;

        String branch = (event instanceof MessageEvent me) ? me.getBranch() : null;
        String key = bufferKey(sessionId, branch);

        CopyOnWriteArrayList<AbstractEvent> buffer = pendingEvents
                .computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        buffer.add(event);

        // 自动刷盘：该 branch 追加了 finishReason=STOP 的 assistant 消息 → 本轮结束
        // 只刷该 branch 的缓冲，不影响其他 branch
        if (event instanceof MessageEvent me
                && me.isAssistantMessage()
                && "STOP".equals(me.getFinishReason())
                && !me.isSynthetic()) {
            flushPendingEvents(sessionId, branch);
        }
    }

    /**
     * 将该 session 所有 branch 的缓冲刷盘到 events.jsonl。
     * 在 pause/中断时调用，确保所有未完成的事件都落盘。
     */
    @Override
    public void flushPendingEvents(String sessionId) {
        // 找到所有该 session 的缓冲 key（root + 所有 branch）
        List<String> keysToFlush = pendingEvents.keySet().stream()
                .filter(k -> k.equals(sessionId) || k.startsWith(sessionId + "@"))
                .toList();
        for (String key : keysToFlush) {
            String branch = key.equals(sessionId) ? null : key.substring(sessionId.length() + 1);
            flushPendingEvents(sessionId, branch);
        }
    }

    /**
     * 将指定 branch 的缓冲刷盘到 events.jsonl。
     * 刷盘前调用 fixOrphanToolCalls 为孤儿 assistant(toolCalls) 补虚拟 ToolResponse。
     */
    private void flushPendingEvents(String sessionId, String branch) {
        String key = bufferKey(sessionId, branch);
        List<AbstractEvent> snapshot = pendingEvents.remove(key);
        if (snapshot == null || snapshot.isEmpty()) return;

        // 孤儿 toolCall 修复：为缺少 ToolResponse 的 assistant(toolCalls) 补虚拟响应
        List<AbstractEvent> toWrite = fixOrphanToolCalls(sessionId, snapshot);

        Path eventsFile = AppConstants.Session.eventsFile(sessionId);
        try {
            Files.createDirectories(eventsFile.getParent());
            StringBuilder sb = new StringBuilder();
            for (AbstractEvent e : toWrite) {
                sb.append(JsonUtils.toJson(e)).append("\n");
            }
            Files.writeString(eventsFile, sb.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("刷盘待处理事件失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 孤儿 toolCall 修复：为缓冲中缺少对应 ToolResponse 的 assistant(toolCalls) 补虚拟 ToolResponse。
     * 所有孤儿处理逻辑收敛在此方法，CompactionUtils 不再包含任何孤儿处理。
     */
    private List<AbstractEvent> fixOrphanToolCalls(String sessionId, List<AbstractEvent> events) {
        Set<String> respondedIds = new HashSet<>();
        for (AbstractEvent e : events) {
            if (e instanceof MessageEvent me && me.isToolResponse()) {
                me.getResponses().forEach(r -> respondedIds.add(r.id()));
            }
        }
        List<AbstractEvent> result = new ArrayList<>(events.size());
        for (AbstractEvent e : events) {
            result.add(e);
            if (e instanceof MessageEvent me && me.isAssistantMessage() && me.hasToolCalls()) {
                List<MessageEvent.ToolResponseInfo> orphans = me.getToolCalls().stream()
                        .filter(tc -> !respondedIds.contains(tc.id()))
                        .map(tc -> new MessageEvent.ToolResponseInfo(
                                tc.id(), tc.name(), "[Tool execution interrupted by user]"))
                        .toList();
                if (!orphans.isEmpty()) {
                    result.add(MessageEvent.toolResponse(sessionId, orphans));
                }
            }
        }
        return result;
    }

    /**
     * 善后被中断的 toolCalls：将缓冲中的事件刷盘，
     * 刷盘前由 fixOrphanToolCalls 为孤儿 assistant(toolCalls) 补虚拟 ToolResponse。
     */
    @Override
    public void finalizeInterruptedToolCalls(String sessionId) {
        flushPendingEvents(sessionId);
    }

    @Override
    public void truncateEventsFrom(String sessionId, String userMessageText) {
        if (sessionId == null) return;
        // 先刷盘，确保流式/缓冲中的事件（包括撤回目标）落盘，文本匹配才可靠
        flushPendingEvents(sessionId);
        // 再次清空该 session 所有 branch 的内存缓冲（这些都属于撤回目标之后的内容）
        pendingEvents.keySet().removeIf(k -> k.equals(sessionId) || k.startsWith(sessionId + "@"));

        Path eventsFile = AppConstants.Session.eventsFile(sessionId);
        if (!Files.exists(eventsFile)) return;

        try {
            List<AbstractEvent> all = new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(eventsFile)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    AbstractEvent se = deserializeEvent(line);
                    if (se != null) all.add(se);
                }
            }

            // 从后往前找与目标文本匹配的最近一条用户消息，保留其之前的事件
            int keepCount = 0;
            for (int i = all.size() - 1; i >= 0; i--) {
                AbstractEvent e = all.get(i);
                if (e instanceof MessageEvent me
                        && me.isUserMessage()
                        && userMessageText != null
                        && userMessageText.equals(me.getText())) {
                    keepCount = i;
                    break;
                }
            }

            // 无条件重写文件，只保留撤回点之前的事件
            List<AbstractEvent> kept = all.subList(0, keepCount);
            StringBuilder sb = new StringBuilder();
            for (AbstractEvent e : kept) {
                sb.append(JsonUtils.toJson(e)).append("\n");
            }
            Files.writeString(eventsFile, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("撤回对话完成: sessionId={}, 保留事件数={}, 原事件数={}", sessionId, kept.size(), all.size());
        } catch (IOException e) {
            log.error("撤回事件失败: sessionId={}", sessionId, e);
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

        // 合并内存缓冲中的待处理事件（root + 所有 branch）
        pendingEvents.forEach((key, buffer) -> {
            if (key.equals(sessionId) || key.startsWith(sessionId + "@")) {
                if (!buffer.isEmpty()) {
                    allEvents.addAll(buffer);
                }
            }
        });

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
        // 本轮对话未结束（任何 branch 缓冲非空）时不压缩，只压缩文件中已存在的记录
        boolean hasPending = pendingEvents.keySet().stream()
                .anyMatch(k -> (k.equals(sessionId) || k.startsWith(sessionId + "@"))
                        && !pendingEvents.get(k).isEmpty());
        if (hasPending) {
            log.debug("跳过压缩：本轮对话未结束，sessionId={}", sessionId);
            return new CompactionResult(List.of(), List.of(), 0);
        }

        Session session = getById(sessionId);
        if (session == null) return new CompactionResult(List.of(), List.of(), 0);

        List<AbstractEvent> allEvents = getEvents(sessionId, EventFilter.all());
        // 传给 trigger / strategy 的事件应排除 archived：压缩后旧事件会标记 archived 但
        // 仍保留在文件中（供 UI / 搜索查看）。若不过滤，这些归档事件仍会占用 maxTurns /
        // maxEventsToKeep 计数，导致每次对话结束后 turnCount / rootEventCount 永不下降，
        // 压缩只触发一次之后会话就会被"一直压缩"。
        List<MessageEvent> events = allEvents.stream()
                .filter(e -> e instanceof MessageEvent)
                .map(e -> (MessageEvent) e)
                .filter(e -> !e.isArchived())
                .toList();
        CompactionRequest request = CompactionRequest.of(session, events);

        if (!trigger.shouldCompact(request)) {
            return new CompactionResult(events, List.of(), 0);
        }

        CompactionResult result = strategy.compact(request);

        // 孤儿处理已收敛到 flushPendingEvents/fixOrphanToolCalls，
        // 缓冲层保证文件中的 turn 都是完整的，compact 不再做任何孤儿处理
        List<MessageEvent> finalActive = result.compactedEvents();

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
