package cn.bitloom.agentic.tool.manage.app;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.config.ConfigManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

/**
 * 获取指定配置项的值。
 */
@Slf4j
public class AppConfigGetTool extends AbstractTool<AppConfigGetTool.Input> {

    private static final String DESCRIPTION = "获取指定配置项的值";

    private final ConfigManager configManager;

    private AppConfigGetTool(ConfigManager configManager) {
        super("app_config_get", DESCRIPTION, Input.class);
        Assert.notNull(configManager, "configManager不能为null");
        this.configManager = configManager;
    }

    public record Input(
            @ToolParam(description = "配置项键名") String key
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        String key = input.key();
        log.info("[ToolCall] app_config_get - 获取配置项: {}", key);
        return switch (key) {
            case "app.session.isolation" -> ToolResult.success("app.session.isolation=" + configManager.getIsolation());
            case "spring.ai.deepseek.chat.base-url" -> ToolResult.success("spring.ai.deepseek.chat.base-url=" + configManager.getDeepseekBaseUrl());
            case "spring.ai.deepseek.chat.options.model" -> ToolResult.success("spring.ai.deepseek.chat.options.model=" + configManager.getDeepseekChatModel());
            case "app.search.bocha-api-key" -> ToolResult.success("app.search.bocha-api-key=" + (configManager.getBochaApiKey() != null ? "已配置" : "未配置"));
            default -> ToolResult.error("未知配置项: " + key + "。支持的配置项: app.session.isolation, spring.ai.deepseek.chat.base-url, spring.ai.deepseek.chat.options.model, spring.ai.zhipuai.chat.base-url, spring.ai.zhipuai.chat.options.model, app.search.bocha-api-key");
        };
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

        public AppConfigGetTool build() {
            return new AppConfigGetTool(configManager);
        }
    }
}
