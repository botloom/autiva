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
 * 更新MCP服务器配置的工具（覆盖整个配置文件，需谨慎操作）。
 */
@Slf4j
public class McpConfigUpdateTool extends AbstractTool<McpConfigUpdateTool.Input> {

    private static final String DESCRIPTION = "更新MCP服务器配置（覆盖整个配置文件，需谨慎操作）";

    private McpConfigUpdateTool() {
        super("mcp_config_update", DESCRIPTION, Input.class);
    }

    public record Input(
            @ToolParam(description = "完整的MCP服务器配置JSON内容") String jsonContent
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        log.info("[ToolCall] mcp_config_update - 更新MCP配置");
        try {
            Path mcpConfigPath = AppConstants.Base.AGENT_CONFIG_FILE;
            if (!Files.exists(mcpConfigPath.getParent())) {
                Files.createDirectories(mcpConfigPath.getParent());
            }
            Files.writeString(mcpConfigPath, input.jsonContent(), StandardCharsets.UTF_8);
            return ToolResult.success("MCP配置已更新。需要重启应用才能生效。");
        } catch (IOException e) {
            log.error("[ToolCall] mcp_config_update - 更新失败", e);
            return ToolResult.error("更新MCP配置失败: " + e.getMessage());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Builder() {}

        public McpConfigUpdateTool build() {
            return new McpConfigUpdateTool();
        }
    }
}
