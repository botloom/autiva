package cn.bitloom.agentic.session;

import cn.bitloom.agentic.event.AbstractEvent;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
@Component
public class FileSystemSessionManager implements ISessionManager {

    private final ConcurrentHashMap<String, List<AbstractEvent>> eventBuffers = new ConcurrentHashMap<>();

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
        Instant expiresAt = request.timeToLive() != null
                ? createdAt.plus(request.timeToLive())
                : Instant.ofEpochMilli(Long.MAX_VALUE);

        Map<String, Object> metadata = new HashMap<>(request.metadata());
        if (!metadata.containsKey("title")) metadata.put("title", "新对话");
        if (!metadata.containsKey("updateAt")) metadata.put("updateAt", createdAt.toEpochMilli());
        if (!metadata.containsKey("messageCount")) metadata.put("messageCount", 0);

        Session session = Session.builder()
                .id(id)
                .userId(request.userId())
                .createdAt(createdAt)
                .expiresAt(expiresAt)
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
            eventBuffers.remove(sessionId);
            log.info("删除会话: {}", sessionId);
        } catch (IOException e) {
            log.error("删除会话失败: {}", sessionId, e);
            throw StorageException.writeError("session-delete-" + sessionId, e);
        }
    }

    @Override
    public int deleteExpiredSessions(Instant before) {
        List<String> expiredIds = new ArrayList<>();
        for (Session session : scanAllSessions()) {
            if (session.createdAt() != null && session.createdAt().isBefore(before)) {
                expiredIds.add(session.id());
            }
        }
        int count = 0;
        for (String id : expiredIds) {
            try {
                remove(id);
                count++;
            } catch (Exception e) {
                log.warn("删除过期会话失败: {}", id, e);
            }
        }
        return count;
    }

    /** code 目录下的保留名（非项目），扫描时跳过 */
    private static final java.util.Set<String> CODE_RESERVED_NAMES = java.util.Set.of("sessions", "memory");

    /**
     * 扫描所有 session 目录（work 模式 + code 模式各项目目录）。
     * - workspace/work/sessions/
     * - workspace/code/{project}/sessions/（跳过 sessions/memory 等保留名）
     */
    private List<Session> scanAllSessions() {
        List<Session> result = new ArrayList<>();
        // work 模式
        result.addAll(scanSessionDir(AppConstants.Base.WORKSPACE_DIR.resolve("work/sessions")));
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

    @Override
    public void appendEvent(AbstractEvent event) {
        String sessionId = event.getSessionId();
        eventBuffers.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>())).add(event);
    }

    @Override
    public void flush(String sessionId) {
        List<AbstractEvent> buffer = eventBuffers.get(sessionId);
        if (buffer == null || buffer.isEmpty()) return;

        Session session = getById(sessionId);
        if (session == null) return;

        try {
            Path eventsFile = AppConstants.Session.eventsFile(sessionId);
            Files.createDirectories(eventsFile.getParent());
            StringBuilder sb = new StringBuilder();
            synchronized (buffer) {
                for (AbstractEvent event : buffer) {
                    sb.append(JsonUtils.toJson(event)).append("\n");
                }
                buffer.clear();
            }
            if (!sb.isEmpty()) {
                Files.writeString(eventsFile, sb.toString(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            throw StorageException.writeError("events-flush-" + sessionId, e);
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

        List<AbstractEvent> buffered = eventBuffers.get(sessionId);
        if (buffered != null) {
            synchronized (buffered) {
                allEvents.addAll(buffered);
            }
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

        Path eventsFile = AppConstants.Session.eventsFile(sessionId);
        try {
            StringBuilder sb = new StringBuilder();
            for (AbstractEvent e : result.compactedEvents()) {
                sb.append(JsonUtils.toJson(e)).append("\n");
            }
            Files.writeString(eventsFile, sb.toString());
        } catch (IOException e) {
            log.error("重写 compacted events 失败: {}", sessionId, e);
        }

        return result;
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
