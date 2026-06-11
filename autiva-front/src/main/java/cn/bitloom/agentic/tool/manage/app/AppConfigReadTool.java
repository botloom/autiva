package cn.bitloom.agentic.tool.manage.app;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.config.ConfigManager;
import cn.bitloom.constant.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 读取Autiva应用配置文件内容。
 */
@Slf4j
public class AppConfigReadTool extends AbstractTool<AppConfigReadTool.Input> {

    private static final String DESCRIPTION = "读取Autiva应用配置文件内容";

    private final ConfigManager configManager;

    private AppConfigReadTool(ConfigManager configManager) {
        super("app_config_read", DESCRIPTION, Input.class);
        Assert.notNull(configManager, "configManager不能为null");
        this.configManager = configManager;
    }

    /**
     * 无参数输入
     */
    public record Input(
            @ToolParam(description = "无参数，传空字符串即可") String _none
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        log.info("[ToolCall] app_config_read - 读取应用配置");
        try {
            Path configFile = AppConstants.Base.SETTINGS_FILE;
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ConfigManager configManager;

        public Builder configManager(ConfigManager configManager) {
            this.configManager = configManager;
            return this;
        }

        public AppConfigReadTool build() {
            return new AppConfigReadTool(configManager);
        }
    }
}
