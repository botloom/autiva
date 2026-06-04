package cn.bitloom.agentic.tool.manage;

import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.config.ConfigManager;
import cn.bitloom.constant.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class AppConfigTool {

    private final ConfigManager configManager;

    private AppConfigTool(ConfigManager configManager) {
        Assert.notNull(configManager, "configManager不能为null");
        this.configManager = configManager;
    }

    @Tool(name = "app_config_read", description = "读取Autiva应用配置文件内容")
    public ToolResult readAppConfig() {
        log.info("[ToolCall] app_config_read - 读取应用配置");
        try {
            Path configFile = AppConstants.Base.CONFIG_FILE;
            if (!Files.exists(configFile)) {
                return ToolResult.error("配置文件不存在。");
            }
            String content = Files.readString(configFile, StandardCharsets.UTF_8);
            return ToolResult.builder().status(ToolResult.Status.SUCCESS)
                    .message("应用配置内容")
                    .rawOutput("应用配置内容:\n\n" + content)
                    .build();
        } catch (IOException e) {
            log.error("[ToolCall] app_config_read - 读取失败", e);
            return ToolResult.error("读取配置失败: " + e.getMessage());
        }
    }

    @Tool(name = "app_config_get", description = "获取指定配置项的值")
    public ToolResult getConfigValue(
            @ToolParam(description = "配置项键名") String key
    ) {
        log.info("[ToolCall] app_config_get - 获取配置项: {}", key);
        return switch (key) {
            case "app.session.isolation" -> ToolResult.success("app.session.isolation=" + configManager.getIsolation());
            case "spring.ai.deepseek.chat.base-url" -> ToolResult.success("spring.ai.deepseek.chat.base-url=" + configManager.getDeepseekBaseUrl());
            case "spring.ai.deepseek.chat.options.model" -> ToolResult.success("spring.ai.deepseek.chat.options.model=" + configManager.getDeepseekChatModel());
            case "app.search.bocha-api-key" -> ToolResult.success("app.search.bocha-api-key=" + (configManager.getBochaApiKey() != null ? "已配置" : "未配置"));
            default -> ToolResult.error("未知配置项: " + key + "。支持的配置项: app.session.isolation, spring.ai.deepseek.chat.base-url, spring.ai.deepseek.chat.options.model, spring.ai.zhipuai.chat.base-url, spring.ai.zhipuai.chat.options.model, app.search.bocha-api-key");
        };
    }

    @Tool(name = "app_config_set_isolation", description = "设置会话隔离策略")
    public ToolResult setIsolation(
            @ToolParam(description = "隔离策略（PER_PEER或PER_CHANNEL_PEER）") String isolation
    ) {
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

    @Tool(name = "app_config_path", description = "获取应用配置文件路径")
    public ToolResult getConfigPath() {
        return ToolResult.success("应用配置文件路径: " + AppConstants.Base.CONFIG_FILE.toAbsolutePath());
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

        public AppConfigTool build() {
            return new AppConfigTool(configManager);
        }
    }
}
