package cn.bitloom.agentic.tool.manage.mcp;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.constant.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.file.Path;

/**
 * 获取MCP配置文件路径的工具。
 */
@Slf4j
public class McpConfigPathTool extends AbstractTool<McpConfigPathTool.Input> {

    private static final String DESCRIPTION = "获取MCP配置文件路径";

    private McpConfigPathTool() {
        super("mcp_config_path", DESCRIPTION, Input.class);
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
        log.info("[ToolCall] mcp_config_path - 获取MCP配置路径");
        Path mcpConfigPath = AppConstants.MainAgent.configFile("default");
        return ToolResult.success("MCP配置文件路径: " + mcpConfigPath.toAbsolutePath());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Builder() {}

        public McpConfigPathTool build() {
            return new McpConfigPathTool();
        }
    }
}
