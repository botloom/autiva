package cn.bitloom.agentic.team;

import cn.bitloom.constant.AppConstants;
import cn.bitloom.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * 队友注册表 — per-session 持久化（{@code {sessionDir}/teammates.json}，原子写）。
 *
 * <p>可恢复持久化（设计纪律 3）：进程重启后从磁盘恢复；重启时处于 work 状态的队友
 * 运行已被中断，恢复为 idle（邮箱未消费消息 / 任务板会再次唤醒）。
 *
 * <p>状态转换在 per-session 锁内执行，保证 wake 防重入判断的原子性。
 */
@Slf4j
@Component
public class TeammateRegistry {

    private static final String TEAMMATES_FILE = "teammates.json";
    /** 队友名白名单：防 branch 注入（"teammate." 前缀之外的非法字符 / 点号分隔符） */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,32}$");

    /** sessionId → (name → record)；per-session 锁保护同 session 的读改写 */
    private final Map<String, Map<String, TeammateRecord>> cache = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /** 创建队友（名称校验 + 查重 + 落盘） */
    public TeammateRecord spawn(String sessionId, String name, String description, String definition,
            String projectPath) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "队友名仅允许 1-32 位字母数字下划线连字符（不能含点号）: " + name);
        }
        return withLock(sessionId, () -> {
            Map<String, TeammateRecord> teammates = cache.computeIfAbsent(sessionId, this::loadFromDisk);
            if (teammates.containsKey(name)) {
                throw new IllegalStateException("队友已存在: " + name + "（状态 " + teammates.get(name).getStatus()
                        + "），如需重建请先 shutdown");
            }
            TeammateRecord record = TeammateRecord.spawn(name, description, definition, projectPath);
            teammates.put(name, record);
            persist(sessionId, teammates);
            log.info("[Teams] spawn 队友: session={}, name={}, definition={}", sessionId, name, record.getDefinition());
            return record;
        });
    }

    public Optional<TeammateRecord> get(String sessionId, String name) {
        Map<String, TeammateRecord> teammates = cache.computeIfAbsent(sessionId, this::loadFromDisk);
        return Optional.ofNullable(teammates.get(name));
    }

    /** 活跃队友（非 shutdown） */
    public List<TeammateRecord> listActive(String sessionId) {
        Map<String, TeammateRecord> teammates = cache.computeIfAbsent(sessionId, this::loadFromDisk);
        return teammates.values().stream().filter(t -> !t.isShutdown())
                .sorted((a, b) -> a.getName().compareTo(b.getName()))
                .toList();
    }

    /** 有活跃队友的所有 sessionId（轮询器用；含已缓存但全为 shutdown 的 session） */
    public List<String> activeSessionIds() {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, TeammateRecord>> entry : cache.entrySet()) {
            if (entry.getValue().values().stream().anyMatch(t -> !t.isShutdown())) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * 原子状态转换：仅当当前状态为 expect 时转为 target，否则失败。
     * work→idle 转换时 workVersion+1（类型化协议：使旧审批/关机请求失效）。
     */
    public boolean transition(String sessionId, String name, String expect, String target) {
        return withLock(sessionId, () -> {
            Map<String, TeammateRecord> teammates = cache.computeIfAbsent(sessionId, this::loadFromDisk);
            TeammateRecord record = teammates.get(name);
            if (record == null || !expect.equals(record.getStatus())) {
                return false;
            }
            record.setStatus(target);
            record.setUpdatedAt(java.time.Instant.now());
            if (TeammateRecord.STATUS_WORK.equals(expect) && TeammateRecord.STATUS_IDLE.equals(target)) {
                record.setWorkVersion(record.getWorkVersion() + 1);
            }
            persist(sessionId, teammates);
            log.debug("[Teams] 状态转换: session={}, name={}, {} -> {}", sessionId, name, expect, target);
            return true;
        });
    }

    /** 关机（幂等）：任何状态 → shutdown */
    public boolean shutdown(String sessionId, String name) {
        return withLock(sessionId, () -> {
            Map<String, TeammateRecord> teammates = cache.computeIfAbsent(sessionId, this::loadFromDisk);
            TeammateRecord record = teammates.get(name);
            if (record == null || record.isShutdown()) {
                return false;
            }
            record.setStatus(TeammateRecord.STATUS_SHUTDOWN);
            record.setUpdatedAt(java.time.Instant.now());
            persist(sessionId, teammates);
            log.info("[Teams] shutdown 队友: session={}, name={}", sessionId, name);
            return true;
        });
    }

    /** 更新 worktree 绑定 */
    public void bindWorktree(String sessionId, String name, String worktreePath) {
        withLock(sessionId, () -> {
            Map<String, TeammateRecord> teammates = cache.computeIfAbsent(sessionId, this::loadFromDisk);
            TeammateRecord record = teammates.get(name);
            if (record == null) {
                return null;
            }
            record.setWorktreePath(worktreePath);
            record.setUpdatedAt(java.time.Instant.now());
            persist(sessionId, teammates);
            return null;
        });
    }

    /** 应用启动恢复：扫描所有 session 目录；work 状态恢复为 idle（运行已被重启中断） */
    @EventListener(ApplicationReadyEvent.class)
    public void restore() {
        for (Path sessionsDir : findSessionsDirs()) {
            try (Stream<Path> sessionDirs = Files.list(sessionsDir)) {
                sessionDirs.filter(Files::isDirectory).forEach(dir -> {
                    if (Files.exists(dir.resolve(TEAMMATES_FILE))) {
                        String sessionId = dir.getFileName().toString();
                        Map<String, TeammateRecord> teammates = loadFromDisk(sessionId);
                        boolean changed = false;
                        for (TeammateRecord record : teammates.values()) {
                            if (TeammateRecord.STATUS_WORK.equals(record.getStatus())
                                    || TeammateRecord.STATUS_SPAWNED.equals(record.getStatus())) {
                                record.setStatus(TeammateRecord.STATUS_IDLE);
                                record.setUpdatedAt(java.time.Instant.now());
                                changed = true;
                            }
                        }
                        if (!teammates.isEmpty()) {
                            cache.put(sessionId, teammates);
                            if (changed) {
                                persist(sessionId, teammates);
                            }
                            log.info("[Teams] 恢复队友注册表: session={}, count={}", sessionId, teammates.size());
                        }
                    }
                });
            }
            catch (IOException e) {
                log.warn("[Teams] 扫描 session 目录失败: {}", sessionsDir, e);
            }
        }
    }

    private Map<String, TeammateRecord> loadFromDisk(String sessionId) {
        try {
            Path file = AppConstants.Session.teammatesFile(sessionId);
            if (Files.exists(file)) {
                List<TeammateRecord> records = JsonUtils
                        .fromJson(Files.readString(file), new TypeReference<List<TeammateRecord>>() {});
                Map<String, TeammateRecord> map = new ConcurrentHashMap<>();
                if (records != null) {
                    for (TeammateRecord record : records) {
                        map.put(record.getName(), record);
                    }
                }
                return map;
            }
        }
        catch (Exception e) {
            log.warn("[Teams] 加载队友注册表失败: session={}: {}", sessionId, e.getMessage());
        }
        return new ConcurrentHashMap<>();
    }

    private void persist(String sessionId, Map<String, TeammateRecord> teammates) {
        try {
            Path file = AppConstants.Session.teammatesFile(sessionId);
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(TEAMMATES_FILE + ".tmp");
            Files.writeString(tmp, JsonUtils.toJson(teammates.values().toArray()));
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e) {
            log.error("[Teams] 持久化队友注册表失败: session={}", sessionId, e);
        }
    }

    private List<Path> findSessionsDirs() {
        List<Path> dirs = new ArrayList<>();
        Path workspace = AppConstants.Base.WORKSPACE_DIR;
        Path workSessions = workspace.resolve("work").resolve("sessions");
        if (Files.isDirectory(workSessions)) {
            dirs.add(workSessions);
        }
        Path codeDir = workspace.resolve("code");
        if (Files.isDirectory(codeDir)) {
            try (Stream<Path> projects = Files.list(codeDir)) {
                projects.filter(Files::isDirectory).forEach(project -> {
                    Path sessions = project.resolve("sessions");
                    if (Files.isDirectory(sessions)) {
                        dirs.add(sessions);
                    }
                });
            }
            catch (IOException e) {
                log.warn("[Teams] 扫描 code 项目目录失败", e);
            }
        }
        return dirs;
    }

    private <T> T withLock(String sessionId, java.util.function.Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(sessionId, k -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        }
        finally {
            lock.unlock();
        }
    }
}
