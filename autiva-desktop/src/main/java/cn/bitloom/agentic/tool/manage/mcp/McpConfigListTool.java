package cn.bitloom.agentic.tool.manage.mcp;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.constant.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 列出所有已配置MCP服务器的工具。
 */
@Slf4j
public class McpConfigListTool extends AbstractTool<McpConfigListTool.Input> {

    private static final String DESCRIPTION = "列出所有已配置的MCP服务器";

    private McpConfigListTool() {
        super("mcp_config_list", DESCRIPTION, Input.class);
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
        log.info("[ToolCall] mcp_config_list - 列出MCP服务器");
        try {
            Path mcpConfigPath = AppConstants.MainAgent.configFile("default");
            if (!Files.exists(mcpConfigPath)) {
                return ToolResult.error("工具配置文件不存在。路径: " + mcpConfigPath);
            }
            String content = Files.readString(mcpConfigPath, StandardCharsets.UTF_8);
            return ToolResult.builder().status(ToolResult.Status.SUCCESS)
                    .message("MCP服务器配置")
                    .rawOutput("MCP服务器配置:\n\n" + content)
                    .build();
        } catch (IOException e) {
            log.error("[ToolCall] mcp_config_list - 读取失败", e);
            return ToolResult.error("读取MCP配置失败: " + e.getMessage());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Builder() {}

        public McpConfigListTool build() {
            return new McpConfigListTool();
        }
    }
}
