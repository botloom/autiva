package cn.bitloom.agentic.agent;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class WorkspaceConfig {
    private List<String> tools = new ArrayList<>();
    private Map<String, Object> mcpServers = new LinkedHashMap<>();
    private List<String> skills = new ArrayList<>();
    private List<String> subagents = new ArrayList<>();
}
