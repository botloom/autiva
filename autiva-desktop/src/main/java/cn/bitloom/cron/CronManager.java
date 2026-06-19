package cn.bitloom.cron;

import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.agentic.session.Session;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
public class CronManager {

    private final TaskScheduler taskScheduler;
    private final FileSystemSessionManager fileSystemSessionManager;
    private final Map<String, CronTaskInfo> taskMap = new ConcurrentHashMap<>();

    public CronManager(TaskScheduler taskScheduler,
                       @Lazy FileSystemSessionManager fileSystemSessionManager) {
        this.taskScheduler = taskScheduler;
        this.fileSystemSessionManager = fileSystemSessionManager;
    }

    public void createTask(String name, String type, Integer intervalSeconds,
                           Integer delaySeconds, String cronExpression, String message, String sessionId) {
        log.info("[CronManager] 创建定时任务: name={}, type={}", name, type);

        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("任务名称不能为空");
        }

        if (taskMap.containsKey(name)) {
            throw new IllegalArgumentException("任务名称已存在: " + name);
        }

        if (StringUtils.isBlank(type)) {
            throw new IllegalArgumentException("任务类型不能为空");
        }

        if (StringUtils.isBlank(message)) {
            throw new IllegalArgumentException("消息内容不能为空");
        }

        ScheduledFuture<?> scheduledFuture = scheduleTask(name, type, intervalSeconds, delaySeconds, cronExpression);

        CronTaskInfo taskInfo = new CronTaskInfo();
        taskInfo.setSessionId(sessionId);
        taskInfo.setName(name);
        taskInfo.setType(type);
        taskInfo.setIntervalSeconds(intervalSeconds);
        taskInfo.setDelaySeconds(delaySeconds);
        taskInfo.setCronExpression(cronExpression);
        taskInfo.setMessage(message);
        taskInfo.setScheduledFuture(scheduledFuture);
        taskInfo.setCreateTime(Instant.now());

        taskMap.put(name, taskInfo);

        log.info("[CronManager] 创建成功: name={}", name);
    }

    public Map<String, CronTaskInfo> getAllTasks(String sessionId) {
        if (sessionId == null) {
            return new ConcurrentHashMap<>(taskMap);
        }

        Map<String, CronTaskInfo> result = new ConcurrentHashMap<>();
        taskMap.forEach((name, taskInfo) -> {
            if (sessionId.equals(taskInfo.getSessionId())) {
                result.put(name, taskInfo);
            }
        });
        return result;
    }

    public CronTaskInfo getTask(String sessionId, String name) {
        CronTaskInfo taskInfo = taskMap.get(name);
        if (taskInfo != null && sessionId.equals(taskInfo.getSessionId())) {
            return taskInfo;
        }
        return null;
    }

    public void deleteTask(String sessionId, String name) {
        log.info("[CronManager] 删除定时任务: name={}, sessionId={}", name, sessionId);

        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("任务名称不能为空");
        }

        CronTaskInfo taskInfo = taskMap.get(name);
        if (taskInfo == null) {
            throw new IllegalArgumentException("未找到任务: " + name);
        }

        if (!sessionId.equals(taskInfo.getSessionId())) {
            throw new IllegalArgumentException("无权操作该任务: " + name);
        }

        taskInfo.getScheduledFuture().cancel(false);
        taskMap.remove(name);

        log.info("[CronManager] 删除成功: name={}", name);
    }

    public boolean taskExists(String name) {
        return taskMap.containsKey(name);
    }

    public void triggerTask(String sessionId, String name) {
        log.info("[CronManager] 手动触发定时任务: name={}, sessionId={}", name, sessionId);

        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("任务名称不能为空");
        }

        CronTaskInfo taskInfo = taskMap.get(name);
        if (taskInfo == null) {
            throw new IllegalArgumentException("未找到任务: " + name);
        }

        if (!sessionId.equals(taskInfo.getSessionId())) {
            throw new IllegalArgumentException("无权操作该任务: " + name);
        }

        triggerTaskInternal(taskInfo);
        log.info("[CronManager] 触发成功: name={}", name);
    }

    private ScheduledFuture<?> scheduleTask(String name, String type, Integer intervalSeconds,
                                            Integer delaySeconds, String cronExpression) {
        return switch (type.toLowerCase()) {
            case "once" -> {
                if (delaySeconds == null || delaySeconds <= 0) {
                    throw new IllegalArgumentException("一次性任务必须指定有效的延迟秒数");
                }
                Instant startTime = Instant.now().plusSeconds(delaySeconds);
                yield taskScheduler.schedule(() -> executeTask(name), startTime);
            }
            case "interval" -> {
                if (intervalSeconds == null || intervalSeconds <= 0) {
                    throw new IllegalArgumentException("周期性任务必须指定有效的间隔秒数");
                }
                Instant startTime = delaySeconds != null && delaySeconds > 0
                        ? Instant.now().plusSeconds(delaySeconds)
                        : Instant.now();
                yield taskScheduler.scheduleAtFixedRate(() -> executeTask(name), startTime,
                        java.time.Duration.ofSeconds(intervalSeconds));
            }
            case "cron" -> {
                if (StringUtils.isBlank(cronExpression)) {
                    throw new IllegalArgumentException("Cron任务必须指定cron表达式");
                }
                yield taskScheduler.schedule(() -> executeTask(name), new CronTrigger(cronExpression));
            }
            default -> throw new IllegalArgumentException("不支持的任务类型: " + type);
        };
    }

    private void executeTask(String name) {
        log.info("[CronManager] 定时任务触发: name={}", name);
        try {
            CronTaskInfo taskInfo = taskMap.get(name);
            if (taskInfo != null) {
                triggerTaskInternal(taskInfo);

                if ("once".equals(taskInfo.getType())) {
                    taskMap.remove(name);
                    log.info("[CronManager] 一次性任务已完成并移除: name={}", name);
                }
            }
        } catch (Exception e) {
            log.error("[CronManager] 任务执行失败: name={}", name, e);
        }
    }

    private void triggerTaskInternal(CronTaskInfo taskInfo) {
        Session session = fileSystemSessionManager.getById(taskInfo.sessionId);
        session.publish(
                MessageEvent.userMessage(session.getId(), taskInfo.getMessage())
        );
        log.info("[CronManager] 任务消息已发送到EventBus: name={}, sessionId={}", taskInfo.getName(), taskInfo.getSessionId());
    }

    @Setter
    @Getter
    public static class CronTaskInfo {
        private String sessionId;
        private String name;
        private String type;
        private Integer intervalSeconds;
        private Integer delaySeconds;
        private String cronExpression;
        private String message;
        private ScheduledFuture<?> scheduledFuture;
        private Instant createTime;

    }
}
