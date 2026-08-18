package cn.bitloom.agentic.tool.cron;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.cron.CronManager;
import cn.bitloom.agentic.cron.CronManager.CronTaskInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 列出所有定时任务工具。
 */
@Slf4j
public class CronListTool extends AbstractTool<CronListTool.Input> {

    private static final String DESCRIPTION = "列出所有定时任务";

    private final CronManager cronManager;

    private CronListTool(CronManager cronManager) {
        super("cron_list", DESCRIPTION, Input.class);
        Assert.notNull(cronManager, "cronManager不能为null");
        this.cronManager = cronManager;
    }

    /**
     * 由于 FunctionToolCallback 需要至少有一个字段才能正确生成 JSON Schema，
     * 定义一个可选的 dummy 参数。
     */
    public record Input(
            @ToolParam(description = "无参数", required = false) String _none
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        log.info("[ToolCall] cron_list - 列出所有定时任务");

        Map<String, CronTaskInfo> tasks = cronManager.getAllTasks(getSessionId(toolContext));

        if (tasks.isEmpty()) {
            return ToolResult.success("当前没有定时任务");
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
        return ToolResult.builder().status(ToolResult.Status.SUCCESS)
                .message(tasks.size() + " 个定时任务")
                .data(Map.of("count", tasks.size()))
                .rawOutput(sb.toString())
                .build();
    }

    /**
     * 从 ToolContext 提取 sessionId
     */
    private String getSessionId(ToolContext toolContext) {
        Object sessionId = toolContext.getContext().get("sessionId");
        return sessionId != null ? sessionId.toString() : null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private CronManager cronManager;

        private Builder() {}

        public Builder cronManager(CronManager cronManager) {
            this.cronManager = cronManager;
            return this;
        }

        public CronListTool build() {
            return new CronListTool(this.cronManager);
        }
    }
}
