package cn.bitloom.agentic.tool;

import cn.bitloom.cron.CronManager;
import cn.bitloom.cron.CronManager.CronTaskInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class CronTool {

    private final CronManager cronManager;

    private CronTool(CronManager cronManager) {
        Assert.notNull(cronManager, "cronManager不能为null");
        this.cronManager = cronManager;
    }

    @Tool(name = "cron_create", description = "创建定时任务，支持三种类型：once（一次性任务）、interval（周期性任务）、cron（cron表达式任务）")
    public String create(
            @ToolParam(description = "任务名称（唯一标识）") String name,
            @ToolParam(description = "触发类型：once/interval/cron") String type,
            @ToolParam(description = "间隔秒数（interval类型必填）") Integer intervalSeconds,
            @ToolParam(description = "延迟秒数（once类型必填，interval类型可选）") Integer delaySeconds,
            @ToolParam(description = "cron表达式（cron类型必填）") String cronExpression,
            @ToolParam(description = "触发时发送的消息内容") String message,
            ToolContext toolContext
    ) {
        log.info("[ToolCall] cron_create - 创建定时任务: name={}, type={}", name, type);

        try {
            cronManager.createTask(name, type, intervalSeconds, delaySeconds, cronExpression, message, toolContext.getContext().get("sessionId").toString());
            log.info("[ToolCall] cron_create - 创建成功: name={}", name);
            return "定时任务创建成功: " + name;

        } catch (Exception e) {
            log.error("[ToolCall] cron_create - 创建失败: name={}", name, e);
            return "创建定时任务失败: " + e.getMessage();
        }
    }

    @Tool(name = "cron_list", description = "列出所有定时任务")
    public String list(ToolContext toolContext) {
        log.info("[ToolCall] cron_list - 列出所有定时任务");

        Map<String, CronTaskInfo> tasks = cronManager.getAllTasks(toolContext.getContext().get("sessionId").toString());

        if (tasks.isEmpty()) {
            return "当前没有定时任务";
        }

        StringBuilder sb = new StringBuilder("定时任务列表：\n\n");
        List<String> taskDetails = new ArrayList<>();

        for (CronTaskInfo task : tasks.values()) {
            StringBuilder detail = new StringBuilder();
            detail.append("- 任务名称: ").append(task.getName()).append("\n");
            detail.append("  类型: ").append(task.getType()).append("\n");
            detail.append("  状态: ").append(task.getScheduledFuture().isCancelled() ? "已取消" : "运行中").append("\n");
            detail.append("  创建时间: ").append(task.getCreateTime()).append("\n");

            switch (task.getType()) {
                case "once" -> detail.append("  延迟: ").append(task.getDelaySeconds()).append("秒\n");
                case "interval" -> {
                    detail.append("  间隔: ").append(task.getIntervalSeconds()).append("秒\n");
                    if (task.getDelaySeconds() != null) {
                        detail.append("  初始延迟: ").append(task.getDelaySeconds()).append("秒\n");
                    }
                }
                case "cron" -> detail.append("  Cron表达式: ").append(task.getCronExpression()).append("\n");
            }

            detail.append("  消息: ").append(task.getMessage()).append("\n");
            taskDetails.add(detail.toString());
        }

        sb.append(String.join("\n", taskDetails));
        log.info("[ToolCall] cron_list - 查询完成, 共 {} 个任务", tasks.size());
        return sb.toString();
    }

    @Tool(name = "cron_delete", description = "删除指定的定时任务")
    public String delete(@ToolParam(description = "任务名称") String name, ToolContext toolContext) {
        log.info("[ToolCall] cron_delete - 删除定时任务: name={}", name);

        try {
            String sessionId = toolContext.getContext().get("sessionId").toString();
            cronManager.deleteTask(sessionId, name);
            log.info("[ToolCall] cron_delete - 删除成功: name={}", name);
            return "定时任务已删除: " + name;

        } catch (Exception e) {
            log.error("[ToolCall] cron_delete - 删除失败: name={}", name, e);
            return "删除定时任务失败: " + e.getMessage();
        }
    }

    @Tool(name = "cron_trigger", description = "手动触发定时任务")
    public String trigger(@ToolParam(description = "任务名称") String name, ToolContext toolContext) {
        log.info("[ToolCall] cron_trigger - 手动触发定时任务: name={}", name);

        try {
            String sessionId = toolContext.getContext().get("sessionId").toString();
            cronManager.triggerTask(sessionId, name);
            log.info("[ToolCall] cron_trigger - 触发成功: name={}", name);
            return "定时任务已手动触发: " + name;

        } catch (Exception e) {
            log.error("[ToolCall] cron_trigger - 触发失败: name={}", name, e);
            return "触发定时任务失败: " + e.getMessage();
        }
    }

    public static Builder builder(CronManager cronManager) {
        return new Builder(cronManager);
    }

    public static class Builder {

        private final CronManager cronManager;

        private Builder(CronManager cronManager) {
            this.cronManager = cronManager;
        }

        public CronTool build() {
            return new CronTool(this.cronManager);
        }
    }
}
