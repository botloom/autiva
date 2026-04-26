package cn.bitloom.vm;

import cn.bitloom.agentic.mcp.McpManager;
import cn.bitloom.agentic.mcp.McpServer;
import cn.bitloom.agentic.mcp.McpTransportTypeEnum;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpPageViewModel {

    private final McpManager mcpManager;

    @Getter
    private final ObservableList<McpServer> servers = FXCollections.observableArrayList();

    public void loadServers() {
        servers.setAll(mcpManager.getMcpServers().values().stream().toList());
    }

    public void addServer(McpServer server) {
        mcpManager.addServer(server);
        loadServers();
    }

    public void updateServer(String name, McpServer server) {
        mcpManager.updateServer(name, server);
        loadServers();
    }

    public void deleteServer(String name) {
        mcpManager.deleteServer(name);
        loadServers();
    }

    public String buildServerDescription(McpServer server) {
        StringBuilder sb = new StringBuilder();
        if (server.getTransportType() == null) {
            return "";
        }
        switch (server.getTransportType()) {
            case STDIO -> {
                sb.append("命令: ").append(server.getCommand());
                if (server.getArgs() != null && !server.getArgs().isEmpty()) {
                    sb.append(" ").append(String.join(" ", server.getArgs()));
                }
            }
            case SSE -> {
                sb.append("URL: ").append(server.getUrl());
                if (server.getSseEndpoint() != null) {
                    sb.append(" | SSE Endpoint: ").append(server.getSseEndpoint());
                }
            }
            case STREAMABLE_HTTP -> {
                sb.append("URL: ").append(server.getUrl());
                if (server.getEndpoint() != null) {
                    sb.append(" | Endpoint: ").append(server.getEndpoint());
                }
            }
        }
        return sb.toString();
    }
}
