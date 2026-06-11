package cn.bitloom.agentic.tool.manage.app;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.config.ConfigManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

/**
 * 设置会话隔离策略。
 */
@Slf4j
public class AppConfigSetIsolationTool extends AbstractTool<AppConfigSetIsolationTool.Input> {

    private static final String DESCRIPTION = "设置会话隔离策略";

    private final ConfigManager configManager;

    private AppConfigSetIsolationTool(ConfigManager configManager) {
        super("app_config_set_isolation", DESCRIPTION, Input.class);
        Assert.notNull(configManager, "configManager不能为null");
        this.configManager = configManager;
    }

    public record Input(
            @ToolParam(description = "隔离策略（PER_PEER或PER_CHANNEL_PEER）") String isolation
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        String isolation = input.isolation();
        log.info("[ToolCall] app_config_set_isolation - 设置隔离策略: {}", isolation);
        try {
            configManager.setIsolation(cn.bitloom.agentic.session.SessionIsolationEnum.valueOf(isolation.toUpperCase()));
            configManager.save();
            return ToolResult.success("会话隔离策略已设置为: " + isolation);
        } catch (Exception e) {
            log.error("[ToolCall] app_config_set_isolation - 设置失败", e);
            return ToolResult.error("设置隔离策略失败: " + e.getMessage());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ConfigManager configManager;

        public Builder configManager(ConfigManager configManager) {
            this.configManager = configManager;
            return this;
        }

        public AppConfigSetIsolationTool build() {
            return new AppConfigSetIsolationTool(configManager);
        }
    }
}
