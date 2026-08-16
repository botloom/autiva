package cn.bitloom.agentic.taskboard;

import cn.bitloom.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * 任务图存储 — 项目级持久化任务依赖图的仓库（对标 learn-claude-code s10）。
 *
 * <p>存储：{@code {projectDir}/.autiva/tasks/{id}.json}，每任务一文件；
 * 写入用临时文件 + 原子替换（可恢复持久化纪律）。
 *
 * <p>原子认领：{@code .autiva/tasks/.lock} 进程间文件锁（FileChannel.lock）
 * + 锁内二次状态校验——"扫描只找候选，认领必须原子"，
 * 这是 P2-2 多智能体并发认领同一任务的基础。
 */
@Slf4j
@Component
public class TaskBoardRepository {

    private static final String AUTIVA_DIR = ".autiva";
    private static final String TASKS_DIR = "tasks";
    private static final String LOCK_FILE = ".lock";
    private static final String JSON_SUFFIX = ".json";

    private final SecureRandom random = new SecureRandom();

    /**
     * 认领结果。
     *
     * @param success 是否成功
     * @param message 失败原因或成功说明
     * @param task    成功时的任务记录（含新 owner/status）
     */
    public record ClaimResult(boolean success, String message, TaskRecord task) {
        static ClaimResult ok(TaskRecord task) {
            return new ClaimResult(true, "已认领任务 " + task.getId(), task);
        }

        static ClaimResult fail(String message) {
            return new ClaimResult(false, message, null);
        }
    }

    /**
     * 完成结果。
     *
     * @param success  是否成功
     * @param message  失败原因或成功说明
     * @param unlocked 本次完成后刚解除阻塞的 pending 任务（"Unlocked" 提示用）
     */
    public record CompleteResult(boolean success, String message, List<TaskRecord> unlocked) {
        static CompleteResult ok(List<TaskRecord> unlocked) {
            return new CompleteResult(true, "已完成任务", unlocked);
        }

        static CompleteResult fail(String message) {
            return new CompleteResult(false, message, List.of());
        }
    }

    /**
     * 创建任务：生成 ID、置 pending、落盘。
     */
    public TaskRecord create(String projectDir, String subject, String description, List<String> blockedBy) {
        String id = "task_" + randomHex(8);
        TaskRecord task = TaskRecord.newTask(id, subject, description, blockedBy);
        save(projectDir, task);
        log.info("[TaskBoard] 创建任务: id={}, subject={}, blockedBy={}", id, subject, blockedBy);
        return task;
    }

    /**
     * 列出项目全部任务（按创建时间升序）。statusFilter 非空时按状态过滤。
     */
    public List<TaskRecord> list(String projectDir, String statusFilter) {
        return scan(projectDir)
                .filter(t -> statusFilter == null || statusFilter.isBlank()
                        || statusFilter.equalsIgnoreCase(t.getStatus()))
                .sorted(Comparator.comparing(TaskRecord::getCreatedAt))
                .toList();
    }

    /**
     * 原子认领：全局文件锁内二次校验（pending && 无 owner && 依赖全 completed）后
     * 置 in_progress + owner。失败返回具体原因。
     */
    public ClaimResult claim(String projectDir, String taskId, String owner) {
        return withLock(projectDir, () -> {
            Optional<TaskRecord> found = load(projectDir, taskId);
            if (found.isEmpty()) {
                return ClaimResult.fail("任务不存在: " + taskId);
            }
            TaskRecord task = found.get();
            if (!task.isPending()) {
                return ClaimResult.fail("任务状态为 " + task.getStatus() + "，仅 pending 可认领");
            }
            if (task.getOwner() != null) {
                return ClaimResult.fail("任务已被 " + task.getOwner() + " 认领");
            }
            List<String> incomplete = incompleteDependencies(projectDir, task);
            if (!incomplete.isEmpty()) {
                return ClaimResult.fail("任务被未完成的依赖阻塞: " + incomplete);
            }
            task.setStatus(TaskRecord.STATUS_IN_PROGRESS);
            task.setOwner(owner);
            task.setUpdatedAt(Instant.now());
            save(projectDir, task);
            log.info("[TaskBoard] 认领任务: id={}, owner={}", taskId, owner);
            return ClaimResult.ok(task);
        });
    }

    /**
     * 完成任务：锁内置 completed + 解锁扫描——找出所有"之前被阻塞、现在刚解锁"
     * 的 pending 任务（blockedBy 含本任务、其余依赖均 completed）。
     */
    public CompleteResult complete(String projectDir, String taskId) {
        return withLock(projectDir, () -> {
            Optional<TaskRecord> found = load(projectDir, taskId);
            if (found.isEmpty()) {
                return CompleteResult.fail("任务不存在: " + taskId);
            }
            TaskRecord task = found.get();
            if (!task.isInProgress()) {
                return CompleteResult.fail("任务状态为 " + task.getStatus() + "，仅 in_progress 可完成");
            }
            task.setStatus(TaskRecord.STATUS_COMPLETED);
            task.setUpdatedAt(Instant.now());
            save(projectDir, task);
            log.info("[TaskBoard] 完成任务: id={}", taskId);

            List<TaskRecord> unlocked = scan(projectDir)
                    .filter(t -> t.isPending()
                            && t.getBlockedBy() != null && t.getBlockedBy().contains(taskId))
                    .filter(t -> incompleteDependencies(projectDir, t).isEmpty())
                    .toList();
            return CompleteResult.ok(unlocked);
        });
    }

    /**
     * 任务尚未完成的依赖（不存在/未 completed 的 blockedBy 项）。
     */
    public List<String> incompleteDependencies(String projectDir, TaskRecord task) {
        if (task.getBlockedBy() == null || task.getBlockedBy().isEmpty()) {
            return List.of();
        }
        return task.getBlockedBy().stream()
                .filter(depId -> load(projectDir, depId)
                        .map(d -> !d.isCompleted())
                        .orElse(true))
                .toList();
    }

    /**
     * 生成新任务 ID（保证不与现有文件冲突）。
     */
    public String newTaskId(String projectDir) {
        String id = "task_" + randomHex(8);
        while (Files.exists(taskFile(projectDir, id))) {
            id = "task_" + randomHex(8);
        }
        return id;
    }

    // ===== 存储原语 =====

    public Optional<TaskRecord> load(String projectDir, String taskId) {
        Path file = taskFile(projectDir, taskId);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(JsonUtils.fromJson(Files.readString(file), TaskRecord.class));
        } catch (Exception e) {
            log.warn("[TaskBoard] 加载任务失败: file={}, error={}", file, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 保存任务：临时文件 + 原子替换。
     */
    public void save(String projectDir, TaskRecord task) {
        try {
            Path file = taskFile(projectDir, task.getId());
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(task.getId() + ".tmp");
            Files.writeString(tmp, JsonUtils.toJson(task));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("保存任务失败: " + task.getId(), e);
        }
    }

    /**
     * 扫描项目任务目录中的全部任务文件。
     */
    public Stream<TaskRecord> scan(String projectDir) {
        Path dir = tasksDir(projectDir);
        if (!Files.isDirectory(dir)) {
            return Stream.empty();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().endsWith(JSON_SUFFIX))
                    .map(f -> {
                        try {
                            return JsonUtils.fromJson(Files.readString(f), TaskRecord.class);
                        } catch (Exception e) {
                            log.warn("[TaskBoard] 解析任务文件失败: {}, {}", f, e.getMessage());
                            return null;
                        }
                    })
                    .filter(t -> t != null)
                    .toList()
                    .stream();
        } catch (IOException e) {
            log.warn("[TaskBoard] 扫描任务目录失败: {}, {}", dir, e.getMessage());
            return Stream.empty();
        }
    }

    // ===== 全局文件锁 =====

    /**
     * 任务目录全局文件锁内执行（进程间互斥；同进程 JVM 锁保证 FileChannel.lock 可重入安全）。
     */
    private <T> T withLock(String projectDir, Supplier<T> action) {
        Path lockFile;
        try {
            lockFile = tasksDir(projectDir).resolve(LOCK_FILE);
            Files.createDirectories(lockFile.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("创建任务锁文件失败", e);
        }
        synchronized (TaskBoardRepository.class) {
            try (RandomAccessFile raf = new RandomAccessFile(lockFile.toFile(), "rw");
                 FileChannel channel = raf.getChannel();
                 FileLock ignored = channel.lock()) {
                return action.get();
            } catch (IOException | OverlappingFileLockException e) {
                throw new IllegalStateException("获取任务锁失败", e);
            } catch (RuntimeException e) {
                throw e;
            }
        }
    }

    private Path tasksDir(String projectDir) {
        return Path.of(projectDir).resolve(AUTIVA_DIR).resolve(TASKS_DIR);
    }

    private Path taskFile(String projectDir, String taskId) {
        return tasksDir(projectDir).resolve(taskId + JSON_SUFFIX);
    }

    private String randomHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(Integer.toHexString(random.nextInt(16)));
        }
        return sb.toString();
    }
}
