package cn.bitloom.agentic.tool.mcp;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.tool.mcp.McpConnectionManager.McpServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;

/**
 * MCP 连接工具 — 按 name 连接项目 mcp.json 中配置的 MCP server。
 *
 * <p>连接成功后拉取工具列表并注册进运行时工具池；由于 Agent 工具池在构建时固定，
 * 新工具自下一轮对话起生效（VM 层监听连接变化并 evict per-session Agent 缓存）。
 * MCP 工具调用统一受 {@link cn.bitloom.agentic.permission.McpHostPolicy} 宿主策略约束。
 */
@Slf4j
public class McpConnectTool extends AbstractTool<McpConnectTool.Input> {

    private static final String DESCRIPTION =
            "连接 MCP server。按名称连接项目 .autiva/mcp.json 中配置的 server，"
                    + "成功后其工具（命名形如 mcp__{server}__{tool}）自下一轮对话起可用。"
                    + "可用 server 清单先用 McpList 查询。";

    private final McpConnectionManager connectionManager;

    private McpConnectTool(McpConnectionManager connectionManager) {
        super("McpConnect", DESCRIPTION, Input.class);
        Assert.notNull(connectionManager, "connectionManager不能为null");
        this.connectionManager = connectionManager;
    }

    public record Input(
            @ToolParam(description = "mcp.json 中配置的 server 名称") String server
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        String server = input.server();
        log.info("[ToolCall] McpConnect - 连接 MCP server: {}", server);

        String projectDir = extractString(toolContext, "projectPath");
        Map<String, McpServerConfig> servers = connectionManager.loadServers(projectDir);

        McpServerConfig config = servers.get(server);
        if (config == null) {
            List<String> available = servers.keySet().stream().sorted().toList();
            return ToolResult.error("未在 .autiva/mcp.json 中找到 server: " + server
                    + (available.isEmpty() ? "（配置文件为空或不存在）" : "，可用: " + available));
        }
        if (!config.enabled()) {
            return ToolResult.error("server 已被禁用（enabled=false）: " + server);
        }

        boolean alreadyConnected = connectionManager.isConnected(server);
        try {
            McpConnectionManager.ConnectResult result = connectionManager.connect(config);
            List<String> tools = result.tools();
            String message = alreadyConnected
                    ? "server " + server + " 已处于连接状态，工具清单："
                    : "已连接 " + server + "，获取到 " + tools.size() + " 个工具。";
            StringBuilder sb = new StringBuilder(message);
            if (!tools.isEmpty()) {
                sb.append("\n工具清单：\n").append(String.join("\n", tools));
            }
            // 新工具经 Agent 重建注入，当前轮次内尚不可调用
            sb.append("\n注意：新连接的工具自下一轮对话起可用。");
            return ToolResult.success(sb.toString());
        } catch (Exception e) {
            log.warn("[ToolCall] McpConnect 连接失败: server={}, error={}", server, e.getMessage());
            return ToolResult.error("连接 MCP server 失败: " + server + " - " + e.getMessage());
        }
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

        public McpConnectTool build() {
            return new McpConnectTool(this.connectionManager);
        }
    }
}
