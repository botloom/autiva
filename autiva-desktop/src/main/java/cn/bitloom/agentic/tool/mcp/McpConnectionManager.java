package cn.bitloom.agentic.tool.mcp;

import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MCP 运行时连接管理器 — 按 mcp.json 连接 server 并暴露工具回调（对标 learn-claude-code s14）。
 *
 * <p>职责：
 * <ul>
 *   <li>加载 {@code {project}/.autiva/mcp.json} 中的 server 清单（url 或 command 二选一）</li>
 *   <li>按 name 建立同步 MCP 连接（HTTP Streamable 或 stdio），拉取工具列表并转为 ToolCallback</li>
 *   <li>维护已连接 server 的工具回调池，供 Toolkit 在构建 Agent 时注入</li>
 *   <li>连接变化时通知监听者（VM 层用于 evict per-session Agent 缓存，下轮对话重建生效）</li>
 * </ul>
 *
 * <p>工具命名沿用 Spring AI 的 {@code mcp__{server}__{tool}} 规范（由
 * {@link McpToolUtils#getToolCallbacksFromSyncClients} 生成，clientInfo.name 即 server 名）。
 */
@Slf4j
@Component
public class McpConnectionManager {

    private static final String AUTIVA_DIR = ".autiva";
    private static final String CONFIG_FILE = "mcp.json";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration INIT_TIMEOUT = Duration.ofSeconds(30);

    /** stdio transport 共享的 JSON mapper（Jackson 3） */
    private static final McpJsonMapper JSON_MAPPER =
            new JacksonMcpJsonMapper(tools.jackson.databind.json.JsonMapper.builder().build());

    /** server name → 已连接的同步客户端 */
    private final Map<String, McpSyncClient> clients = new ConcurrentHashMap<>();
    /** server name → 该 server 提供的工具回调 */
    private final Map<String, List<ToolCallback>> toolCallbacks = new ConcurrentHashMap<>();
    /** MCP 工具名 → 所属 server（用于宿主策略反查，覆盖无 mcp__ 前缀的命名） */
    private final Map<String, String> toolOwner = new ConcurrentHashMap<>();
    /** 连接变化监听者（evict Agent 缓存等） */
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    /**
     * mcp.json 中单个 server 的配置。
     *
     * @param name    server 名（工具名前缀）
     * @param url     HTTP Streamable 端点（与 command 二选一）
     * @param command stdio 启动命令
     * @param args    stdio 命令参数
     * @param env     stdio 环境变量
     * @param enabled 是否启用（false 时 McpConnect 拒绝连接）
     */
    public record McpServerConfig(
            String name, String url, String command, List<String> args,
            Map<String, String> env, boolean enabled) {
    }

    /**
     * 连接结果。
     *
     * @param server server 名
     * @param tools  连接成功后获取的工具名列表（含命名空间前缀）
     */
    public record ConnectResult(String server, List<String> tools) {
    }

    /**
     * 加载项目 mcp.json 中的 server 清单。文件不存在时返回空 Map。
     */
    public Map<String, McpServerConfig> loadServers(String projectDir) {
        Map<String, McpServerConfig> servers = new LinkedHashMap<>();
        if (projectDir == null || projectDir.isBlank()) {
            return servers;
        }
        Path file = Path.of(projectDir).resolve(AUTIVA_DIR).resolve(CONFIG_FILE);
        if (!Files.exists(file)) {
            return servers;
        }
        try {
            JsonNode root = JsonUtils.parse(Files.readString(file));
            JsonNode mcpServers = root.path("mcpServers");
            if (!mcpServers.isObject()) {
                return servers;
            }
            mcpServers.fields().forEachRemaining(entry -> {
                String name = entry.getKey();
                JsonNode node = entry.getValue();
                String url = node.path("url").asText(null);
                String command = node.path("command").asText(null);
                List<String> args = new ArrayList<>();
                node.path("args").forEach(a -> args.add(a.asText()));
                Map<String, String> env = new LinkedHashMap<>();
                node.path("env").fields().forEachRemaining(e -> env.put(e.getKey(), e.getValue().asText()));
                boolean enabled = node.path("enabled").asText("true").equalsIgnoreCase("true");
                servers.put(name, new McpServerConfig(name, url, command, args, env, enabled));
            });
        } catch (IOException | IllegalStateException e) {
            log.warn("[Mcp] 解析 mcp.json 失败: file={}, error={}", file, e.getMessage());
        }
        return servers;
    }

    /**
     * 连接指定 server。已连接时直接返回现有工具清单（幂等）。
     *
     * @throws IllegalStateException 连接或初始化失败（调用方转为错误 ToolResult，不退出循环）
     */
    public ConnectResult connect(McpServerConfig config) {
        String name = config.name();
        McpSyncClient existing = clients.get(name);
        if (existing != null && existing.isInitialized()) {
            return new ConnectResult(name, toolNamesOf(name));
        }

        McpSyncClient client = McpClient.sync(buildTransport(config))
                .clientInfo(new McpSchema.Implementation(name, "1.0"))
                .requestTimeout(REQUEST_TIMEOUT)
                .initializationTimeout(INIT_TIMEOUT)
                .build();
        try {
            client.initialize();
        } catch (Exception e) {
            closeQuietly(client);
            throw new IllegalStateException("MCP server 初始化失败: " + name + " - " + e.getMessage(), e);
        }

        List<ToolCallback> callbacks;
        try {
            callbacks = List.copyOf(McpToolUtils.getToolCallbacksFromSyncClients(List.of(client)));
        } catch (Exception e) {
            closeQuietly(client);
            throw new IllegalStateException("MCP server 工具列表获取失败: " + name + " - " + e.getMessage(), e);
        }

        // 先清理旧连接（重连场景），再登记新连接
        disconnect(name);
        clients.put(name, client);
        toolCallbacks.put(name, callbacks);
        callbacks.forEach(tc -> toolOwner.put(tc.getToolDefinition().name(), name));
        log.info("[Mcp] 已连接 server: {} - 工具数: {}", name, callbacks.size());

        notifyChangeListeners();
        return new ConnectResult(name, callbacks.stream()
                .map(tc -> tc.getToolDefinition().name()).toList());
    }

    /**
     * 根据配置构建 transport：url → HTTP Streamable；command → stdio。
     */
    private McpClientTransport buildTransport(McpServerConfig config) {
        if (config.url() != null && !config.url().isBlank()) {
            return HttpClientStreamableHttpTransport.builder(config.url())
                    .build();
        }
        if (config.command() != null && !config.command().isBlank()) {
            ServerParameters params = ServerParameters.builder(config.command())
                    .args(config.args() != null ? config.args() : List.of())
                    .env(config.env() != null ? config.env() : Map.of())
                    .build();
            return new StdioClientTransport(params, JSON_MAPPER);
        }
        throw new IllegalStateException("MCP server 配置缺少 url 或 command: " + config.name());
    }

    /**
     * 断开指定 server 连接并移除其工具回调。未连接时静默返回。
     */
    public void disconnect(String name) {
        McpSyncClient removed = clients.remove(name);
        List<ToolCallback> removedCallbacks = toolCallbacks.remove(name);
        if (removedCallbacks != null) {
            removedCallbacks.forEach(tc -> toolOwner.remove(tc.getToolDefinition().name()));
        }
        if (removed != null) {
            closeQuietly(removed);
            log.info("[Mcp] 已断开 server: {}", name);
            notifyChangeListeners();
        }
    }

    /**
     * 所有已连接 server 的工具回调（供 Toolkit 构建 Agent 工具池时注入）。
     */
    public List<ToolCallback> getRuntimeToolCallbacks() {
        return toolCallbacks.values().stream()
                .flatMap(List::stream)
                .toList();
    }

    /**
     * 判断工具名是否属于运行时连接的 MCP 工具。
     */
    public boolean isMcpTool(String toolName) {
        return toolOwner.containsKey(toolName);
    }

    /**
     * 查询工具名所属的 server（无映射时返回 null）。
     */
    public String ownerOf(String toolName) {
        return toolOwner.get(toolName);
    }

    /**
     * 已连接 server 清单：server name → 工具名列表。
     */
    public Map<String, List<String>> connectedServers() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        toolCallbacks.forEach((name, callbacks) ->
                result.put(name, callbacks.stream()
                        .map(tc -> tc.getToolDefinition().name()).toList()));
        return result;
    }

    public boolean isConnected(String name) {
        return clients.containsKey(name);
    }

    /**
     * 注册连接变化监听者（连接成功/断开后回调，用于 evict Agent 缓存）。
     */
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    private void notifyChangeListeners() {
        changeListeners.forEach(listener -> {
            try {
                listener.run();
            } catch (Exception e) {
                log.warn("[Mcp] 连接变化监听者执行失败: {}", e.getMessage());
            }
        });
    }

    private List<String> toolNamesOf(String name) {
        List<ToolCallback> callbacks = toolCallbacks.get(name);
        return callbacks == null ? List.of()
                : callbacks.stream().map(tc -> tc.getToolDefinition().name()).toList();
    }

    private void closeQuietly(McpSyncClient client) {
        try {
            client.closeGracefully();
        } catch (Exception e) {
            log.debug("[Mcp] 关闭客户端失败: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void closeAll() {
        Set.copyOf(clients.keySet()).forEach(this::disconnect);
    }
}
