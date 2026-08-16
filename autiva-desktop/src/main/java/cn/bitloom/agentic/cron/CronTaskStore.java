package cn.bitloom.agentic.cron;

import cn.bitloom.constant.AppConstants;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 定时任务持久化存储（对标 learn-claude-code s12 durable 语义）。
 * <p>
 * per-session 存储：{@code {sessionDir}/cron-tasks.json}，session 删除时任务随之删除。
 * 写入用临时文件 + ATOMIC_MOVE 原子替换（对齐 ApprovalStore 约定）。
 * 进程重启后通过 {@link #loadAllSessions()} 恢复任务定义。
 */
@Slf4j
@Component
public class CronTaskStore {

    private static final String CRON_TASKS_FILE = "cron-tasks.json";

    /**
     * 持久化一个 session 的全部任务（原子写）。
     */
    public void save(String sessionId, List<CronManager.CronTaskInfo> tasks) {
        try {
            Path file = AppConstants.Session.cronTasksFile(sessionId);
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(CRON_TASKS_FILE + ".tmp");
            Files.writeString(tmp, JsonUtils.toJson(tasks));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("[CronTaskStore] 持久化失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 加载一个 session 的任务。文件不存在或损坏时返回空列表。
     */
    public List<CronManager.CronTaskInfo> load(String sessionId) {
        try {
            Path file = AppConstants.Session.cronTasksFile(sessionId);
            if (!Files.exists(file)) {
                return List.of();
            }
            List<CronManager.CronTaskInfo> tasks =
                    JsonUtils.fromJson(Files.readString(file), new TypeReference<List<CronManager.CronTaskInfo>>() {});
            return tasks != null ? tasks : List.of();
        } catch (Exception e) {
            log.warn("[CronTaskStore] 加载失败: sessionId={}, error={}", sessionId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 扫描所有 session 目录，加载全部持久化任务（启动恢复用）。
     * 扫描路径：workspace 下 work 与 code 两种模式的 sessions 目录。
     */
    public Map<String, List<CronManager.CronTaskInfo>> loadAllSessions() {
        Map<String, List<CronManager.CronTaskInfo>> result = new HashMap<>();
        for (Path sessionsDir : findSessionsDirs()) {
            try (Stream<Path> sessionDirs = Files.list(sessionsDir)) {
                sessionDirs.filter(Files::isDirectory).forEach(dir -> {
                    String sessionId = dir.getFileName().toString();
                    if (Files.exists(dir.resolve(CRON_TASKS_FILE))) {
                        List<CronManager.CronTaskInfo> tasks = load(sessionId);
                        if (!tasks.isEmpty()) {
                            result.put(sessionId, tasks);
                        }
                    }
                });
            } catch (IOException e) {
                log.warn("[CronTaskStore] 扫描 session 目录失败: {}", sessionsDir, e);
            }
        }
        return result;
    }

    private List<Path> findSessionsDirs() {
        List<Path> dirs = new ArrayList<>();
        Path workspace = AppConstants.Base.WORKSPACE_DIR;
        // work 模式
        Path workSessions = workspace.resolve("work").resolve("sessions");
        if (Files.isDirectory(workSessions)) {
            dirs.add(workSessions);
        }
        // code 模式：每个项目下
        Path codeDir = workspace.resolve("code");
        if (Files.isDirectory(codeDir)) {
            try (Stream<Path> projects = Files.list(codeDir)) {
                projects.filter(Files::isDirectory).forEach(project -> {
                    Path sessions = project.resolve("sessions");
                    if (Files.isDirectory(sessions)) {
                        dirs.add(sessions);
                    }
                });
            } catch (IOException e) {
                log.warn("[CronTaskStore] 扫描 code 目录失败: {}", codeDir, e);
            }
        }
        return dirs;
    }
}
