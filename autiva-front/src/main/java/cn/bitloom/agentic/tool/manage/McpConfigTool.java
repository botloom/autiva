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
public class McpConfigTool {

    private final ConfigManager configManager;

    private McpConfigTool(ConfigManager configManager) {
        Assert.notNull(configManager, "configManager不能为null");
        this.configManager = configManager;
    }

    @Tool(name = "mcp_config_list", description = "列出所有已配置的MCP服务器")
    public ToolResult listMcpServers() {
        log.info("[ToolCall] mcp_config_list - 列出MCP服务器");
        try {
            Path mcpConfigPath = AppConstants.Base.MCP_DIR.resolve("mcp-servers.json");
            if (!Files.exists(mcpConfigPath)) {
                return ToolResult.error("MCP配置文件不存在。路径: " + mcpConfigPath);
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

    @Tool(name = "mcp_config_update", description = "更新MCP服务器配置（覆盖整个配置文件，需谨慎操作）")
    public ToolResult updateMcpConfig(
            @ToolParam(description = "完整的MCP服务器配置JSON内容") String jsonContent
    ) {
        log.info("[ToolCall] mcp_config_update - 更新MCP配置");
        try {
            Path mcpConfigPath = AppConstants.Base.MCP_DIR.resolve("mcp-servers.json");
            if (!Files.exists(mcpConfigPath.getParent())) {
                Files.createDirectories(mcpConfigPath.getParent());
            }
            Files.writeString(mcpConfigPath, jsonContent, StandardCharsets.UTF_8);
            return ToolResult.success("MCP配置已更新。需要重启应用才能生效。");
        } catch (IOException e) {
            log.error("[ToolCall] mcp_config_update - 更新失败", e);
            return ToolResult.error("更新MCP配置失败: " + e.getMessage());
        }
    }

    @Tool(name = "mcp_config_path", description = "获取MCP配置文件路径")
    public ToolResult getMcpConfigPath() {
        Path mcpConfigPath = AppConstants.Base.MCP_DIR.resolve("mcp-servers.json");
        return ToolResult.success("MCP配置文件路径: " + mcpConfigPath.toAbsolutePath());
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

        public McpConfigTool build() {
            return new McpConfigTool(configManager);
        }
    }
}
