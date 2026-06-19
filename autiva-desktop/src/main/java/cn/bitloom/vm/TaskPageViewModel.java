package cn.bitloom.vm;

import cn.bitloom.cron.CronManager;
import cn.bitloom.cron.CronManager.CronTaskInfo;
import cn.bitloom.util.ExecutorManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskPageViewModel {

    private final CronManager cronManager;

    @Getter
    private final ObservableList<CronTaskInfo> tasks = FXCollections.observableArrayList();

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public void loadTasks() {
        Map<String, CronTaskInfo> taskMap = cronManager.getAllTasks(null);
        tasks.setAll(taskMap.values());
    }

    public void loadTasksAsync(Runnable onLoaded) {
        Task<Map<String, CronTaskInfo>> task = new Task<>() {
            @Override
            protected Map<String, CronTaskInfo> call() {
                return cronManager.getAllTasks(null);
            }
        };
        task.setOnSucceeded(e -> {
            tasks.setAll(task.getValue().values());
            if (onLoaded != null) onLoaded.run();
        });
        task.setOnFailed(e -> log.error("加载任务列表失败", task.getException()));
        ExecutorManager.getPlatformTaskExecutor().execute(task);
    }

    public Map<String, List<CronTaskInfo>> getTasksGroupedBySessionId() {
        return tasks.stream()
                .collect(Collectors.groupingBy(CronTaskInfo::getSessionId));
    }

    public void triggerTask(String sessionId, String name) {
        cronManager.triggerTask(sessionId, name);
    }

    public void deleteTask(String sessionId, String name) {
        cronManager.deleteTask(sessionId, name);
        loadTasks();
    }

    public String getTypeLabel(String type) {
        return switch (type.toLowerCase()) {
            case "once" -> "一次性任务";
            case "interval" -> "周期性任务";
            case "cron" -> "Cron任务";
            default -> type;
        };
    }

    public String getTaskConfig(CronTaskInfo task) {
        return switch (task.getType().toLowerCase()) {
            case "once" -> "延迟: " + task.getDelaySeconds() + "秒";
            case "interval" -> {
                StringBuilder sb = new StringBuilder("间隔: " + task.getIntervalSeconds() + "秒");
                if (task.getDelaySeconds() != null && task.getDelaySeconds() > 0) {
                    sb.append(", 初始延迟: ").append(task.getDelaySeconds()).append("秒");
                }
                yield sb.toString();
            }
            case "cron" -> "Cron表达式: " + task.getCronExpression();
            default -> "";
        };
    }

    public String truncateMessage(String message) {
        if (message == null) {
            return "";
        }
        if (message.length() <= 50) {
            return message;
        }
        return message.substring(0, 50) + "...";
    }

    public String formatCreateTime(java.time.Instant createTime) {
        return formatter.format(createTime);
    }
}
