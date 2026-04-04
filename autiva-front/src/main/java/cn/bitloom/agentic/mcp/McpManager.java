package cn.bitloom.agentic.mcp;

import cn.bitloom.constant.AppConstants;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class McpManager {

    @Getter
    private final Map<String, McpServer> mcpServers = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        this.loadMcpServersConfig();
    }

    public void loadMcpServersConfig() {
        Path configPath = AppConstants.Base.MCP_CONFIG_FILE;
        if (!Files.exists(configPath)) {
            log.info("MCP servers config file not found at {}, skipping load", configPath);
            return;
        }
        try {
            String content = Files.readString(configPath);
            JSONObject root = JSON.parseObject(content);
            JSONObject serversObj = root.getJSONObject("mcpServers");
            if (serversObj != null) {
                for (String key : serversObj.keySet()) {
                    McpServer server = serversObj.getObject(key, McpServer.class);
                    server.setName(key);
                    mcpServers.put(key, server);
                }
                log.info("Loaded {} MCP servers from config", mcpServers.size());
            }
        } catch (IOException e) {
            log.error("Failed to load MCP servers config from {}", configPath, e);
        }
    }

    public void addServer(McpServer server) {
        mcpServers.put(server.getName(), server);
        this.saveConfig();
        log.info("Added MCP server: {}", server.getName());
    }

    public void updateServer(String name, McpServer server) {
        server.setName(name);
        mcpServers.put(name, server);
        this.saveConfig();
        log.info("Updated MCP server: {}", name);
    }

    public void deleteServer(String name) {
        mcpServers.remove(name);
        this.saveConfig();
        log.info("Deleted MCP server: {}", name);
    }

    private void saveConfig() {
        Path configPath = AppConstants.Base.MCP_CONFIG_FILE;
        try {
            Files.createDirectories(configPath.getParent());
            JSONObject root = new JSONObject();
            root.put("mcpServers", new JSONObject(mcpServers));
            Files.writeString(configPath, root.toJSONString());
            log.info("Saved {} MCP servers to config", mcpServers.size());
        } catch (IOException e) {
            log.error("Failed to save MCP servers config to {}", configPath, e);
        }
    }

}