package cn.bitloom.agentic.tool.mcp;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.tool.mcp.McpConnectionManager.McpServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP 清单工具 — 列出 mcp.json 中配置的 server（含连接状态）及已连接 server 的工具清单。
 */
@Slf4j
public class McpListTool extends AbstractTool<McpListTool.Input> {

    private static final String DESCRIPTION =
            "列出 MCP server 清单：项目 .autiva/mcp.json 中配置的所有 server（含连接状态），"
                    + "以及已连接 server 提供的工具列表。";

    private final McpConnectionManager connectionManager;

    private McpListTool(McpConnectionManager connectionManager) {
        super("McpList", DESCRIPTION, Input.class);
        Assert.notNull(connectionManager, "connectionManager不能为null");
        this.connectionManager = connectionManager;
    }

    /** FunctionToolCallback 需要至少一个字段才能生成 JSON Schema */
    public record Input(
            @ToolParam(description = "无参数", required = false) String _none
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        log.info("[ToolCall] McpList - 列出 MCP server 清单");

        String projectDir = extractString(toolContext, "projectPath");
        Map<String, McpServerConfig> servers = connectionManager.loadServers(projectDir);
        Map<String, List<String>> connected = connectionManager.connectedServers();

        if (servers.isEmpty() && connected.isEmpty()) {
            return ToolResult.success("项目尚未配置 MCP server"
                    + "（在 {项目目录}/.autiva/mcp.json 中配置 mcpServers 后使用 McpConnect 连接）");
        }

        List<String> lines = new ArrayList<>();
        lines.add("MCP server 清单：");
        servers.values().stream()
                .sorted(java.util.Comparator.comparing(McpServerConfig::name))
                .forEach(config -> {
                    String status = connectionManager.isConnected(config.name())
                            ? "已连接" : (config.enabled() ? "未连接" : "已禁用");
                    String transport = config.url() != null && !config.url().isBlank()
                            ? "url: " + config.url()
                            : "command: " + config.command();
                    lines.add("- " + config.name() + " [" + status + "] (" + transport + ")");
                    List<String> tools = connected.get(config.name());
                    if (tools != null && !tools.isEmpty()) {
                        lines.add("  工具: " + String.join(", ", tools));
                    }
                });
        // 运行时已连接但不在当前项目 mcp.json 中的 server（如其它项目连接残留）也一并列出
        connected.keySet().stream()
                .filter(name -> !servers.containsKey(name))
                .sorted()
                .forEach(name -> lines.add("- " + name + " [已连接] (项目配置外) 工具: "
                        + String.join(", ", connected.get(name))));

        return ToolResult.success(String.join("\n", lines));
    }

    private String extractString(ToolContext context, String key) {
        if (context == null || context.getContext() == null) {
            return null;
        }
        Object value = context.getContext().get(key);
        return value instanceof String s ? s : null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private McpConnectionManager connectionManager;

        private Builder() {}

        public Builder connectionManager(McpConnectionManager connectionManager) {
            this.connectionManager = connectionManager;
            return this;
        }

        public McpListTool build() {
            return new McpListTool(this.connectionManager);
        }
    }
}
