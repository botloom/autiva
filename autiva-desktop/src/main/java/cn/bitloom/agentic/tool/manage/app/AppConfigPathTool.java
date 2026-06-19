package cn.bitloom.agentic.tool.manage.app;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.config.ConfigManager;
import cn.bitloom.constant.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

/**
 * 获取应用配置文件路径。
 */
@Slf4j
public class AppConfigPathTool extends AbstractTool<AppConfigPathTool.Input> {

    private static final String DESCRIPTION = "获取应用配置文件路径";

    private final ConfigManager configManager;

    private AppConfigPathTool(ConfigManager configManager) {
        super("app_config_path", DESCRIPTION, Input.class);
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
        return ToolResult.success("应用配置文件路径: " + AppConstants.Base.SETTINGS_FILE.toAbsolutePath());
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

        public AppConfigPathTool build() {
            return new AppConfigPathTool(configManager);
        }
    }
}
