package cn.bitloom.agentic.tool.cron;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.cron.CronManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

/**
 * 手动触发定时任务工具。
 */
@Slf4j
public class CronTriggerTool extends AbstractTool<CronTriggerTool.Input> {

    private static final String DESCRIPTION = "手动触发定时任务";

    private final CronManager cronManager;

    private CronTriggerTool(CronManager cronManager) {
        super("cron_trigger", DESCRIPTION, Input.class);
        Assert.notNull(cronManager, "cronManager不能为null");
        this.cronManager = cronManager;
    }

    public record Input(
            @ToolParam(description = "要触发的定时任务名称") String name
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        log.info("[ToolCall] cron_trigger - 手动触发定时任务: name={}", input.name());

        try {
            String sessionId = getSessionId(toolContext);
            cronManager.triggerTask(sessionId, input.name());
            return ToolResult.success("定时任务已手动触发: " + input.name());

        } catch (Exception e) {
            log.error("[ToolCall] cron_trigger - 触发失败: name={}", input.name(), e);
            return ToolResult.error("触发定时任务失败: " + e.getMessage());
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

        public CronTriggerTool build() {
            return new CronTriggerTool(this.cronManager);
        }
    }
}
