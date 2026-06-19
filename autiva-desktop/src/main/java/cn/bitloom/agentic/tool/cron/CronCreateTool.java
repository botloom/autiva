package cn.bitloom.agentic.tool.cron;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.cron.CronManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

/**
 * 创建定时任务工具，支持三种类型：once（一次性任务）、interval（周期性任务）、cron（cron表达式任务）。
 */
@Slf4j
public class CronCreateTool extends AbstractTool<CronCreateTool.Input> {

    private static final String DESCRIPTION = "创建定时任务，支持三种类型：once（一次性任务）、interval（周期性任务）、cron（cron表达式任务）";

    private final CronManager cronManager;

    private CronCreateTool(CronManager cronManager) {
        super("cron_create", DESCRIPTION, Input.class);
        Assert.notNull(cronManager, "cronManager不能为null");
        this.cronManager = cronManager;
    }

    public record Input(
            @ToolParam(description = "任务名称（唯一标识）") String name,
            @ToolParam(description = "触发类型：once/interval/cron") String type,
            @ToolParam(description = "间隔秒数（interval类型必填）", required = false) Integer intervalSeconds,
            @ToolParam(description = "延迟秒数（once类型必填，interval类型可选）", required = false) Integer delaySeconds,
            @ToolParam(description = "cron表达式（cron类型必填）", required = false) String cronExpression,
            @ToolParam(description = "触发时发送的消息内容") String message
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        log.info("[ToolCall] cron_create - 创建定时任务: name={}, type={}", input.name(), input.type());

        try {
            cronManager.createTask(input.name(), input.type(), input.intervalSeconds(),
                    input.delaySeconds(), input.cronExpression(), input.message(), getSessionId(toolContext));
            log.info("[ToolCall] cron_create - 创建成功: name={}", input.name());
            return ToolResult.success("定时任务创建成功: " + input.name());

        } catch (Exception e) {
            log.error("[ToolCall] cron_create - 创建失败: name={}", input.name(), e);
            return ToolResult.error("创建定时任务失败: " + e.getMessage());
        }
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

        public CronCreateTool build() {
            return new CronCreateTool(this.cronManager);
        }
    }
}
