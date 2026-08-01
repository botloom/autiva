package cn.bitloom.agentic.agent;

import cn.bitloom.agentic.agent.AgentDefinition.WorkspaceConfig;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.util.JsonUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 智能体定义管理器，统一管理所有 AgentDefinition（MAIN 和 SUBAGENT）。
 * <p>
 * 提供统一的定义加载和查询接口，供 FileSystemSessionManager 和 TaskTool 共享。
 */
@Slf4j
@Component
public class AgentDefinitionManager {

    private final Map<String, AgentDefinition> definitions = new ConcurrentHashMap<>();

    /**
     * 初始化：加载所有定义
     */
    @PostConstruct
    public void init() {
        loadMainDefinitions();
        loadSubagentDefinitions();
    }

    /**
     * 从 ~/.autiva/subagents/ 加载所有子智能体定义
     */
    private void loadSubagentDefinitions() {
        Path subagentsDir = AppConstants.Base.SUBAGENTS_DIR;
        if (!Files.exists(subagentsDir)) {
            log.warn("子智能体目录不存在: {}", subagentsDir);
            return;
        }
        try (Stream<Path> dirs = Files.list(subagentsDir)) {
            dirs.filter(Files::isDirectory)
                .forEach(dir -> {
                    Path agentFile = dir.resolve("agent.md");
                    if (Files.exists(agentFile)) {
                        try {
                            AgentDefinition definition = AgentDefinition.fromMarkdown(agentFile);
                            definitions.put(definition.name(), definition);
                            log.info("加载子智能体定义: {}", definition.name());
                        } catch (Exception e) {
                            log.warn("加载子智能体定义失败: {}", agentFile, e);
                        }
                    }
                });
        } catch (IOException e) {
            log.warn("扫描子智能体目录失败", e);
        }
    }

    /**
     * 从 agents 目录加载所有主智能体定义，并合并 config.json
     */
    private void loadMainDefinitions() {
        Path agentsDir = AppConstants.Base.AGENTS_DIR;
        if (!Files.exists(agentsDir)) {
            return;
        }

        try (Stream<Path> dirs = Files.list(agentsDir)) {
            dirs.filter(Files::isDirectory)
                    .forEach(dir -> {
                        String agentId = dir.getFileName().toString();
                        Path agentFile = AppConstants.MainAgent.agentFile(agentId);
                        if (Files.exists(agentFile)) {
                            try {
                                AgentDefinition definition = AgentDefinition.fromMarkdown(agentFile);
                                // 合并 config.json（default + agentId，agentId 优先）
                                definition = definition.merge(loadWorkspaceConfig(agentId));
                                definitions.put(agentId, definition);
                                log.info("加载主智能体定义: {}", agentId);
                            } catch (Exception e) {
                                log.error("加载智能体定义失败: {}", agentFile, e);
                            }
                        }
                    });
        } catch (IOException e) {
            log.error("扫描 agents/ 目录失败", e);
        }
    }

    /**
     * 获取指定名称的定义（MAIN 或 SUBAGENT）。
     * 支持大小写不敏感查找（LLM 可能传入小写名称）。
     *
     * @param name 定义名称
     * @return AgentDefinition，不存在返回 null
     */
    public AgentDefinition getDefinition(String name) {
        if (name == null) {
            return null;
        }
        AgentDefinition definition = definitions.get(name);
        if (definition != null) {
            return definition;
        }
        // 大小写不敏感查找
        return definitions.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取所有 SUBAGENT 类型的定义
     */
    public List<AgentDefinition> getSubagentDefinitions() {
        return definitions.values().stream()
                .filter(d -> d.kind() == AgentKind.SUBAGENT)
                .toList();
    }

    /**
     * 获取所有 MAIN 类型的 agent ID
     */
    public Set<String> getMainAgentIds() {
        return definitions.entrySet().stream()
                .filter(e -> e.getValue().kind() == AgentKind.MAIN)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 获取或懒加载 MAIN 定义。
     * 如果缓存中没有，尝试从文件系统加载，并合并 config.json。
     *
     * @param agentId 智能体 ID
     * @return AgentDefinition，不存在则创建默认定义
     */
    public AgentDefinition getOrLoadMainDefinition(String agentId) {
        AgentDefinition definition = definitions.get(agentId);
        if (definition != null) {
            return definition;
        }

        // 尝试从文件系统加载
        Path agentMd = AppConstants.MainAgent.agentFile(agentId);
        if (Files.exists(agentMd)) {
            definition = AgentDefinition.fromMarkdown(agentMd);
            // 合并 config.json
            definition = definition.merge(loadWorkspaceConfig(agentId));
            definitions.put(agentId, definition);
            return definition;
        }

        return definition;
    }

    /**
     * 加载并合并 WorkspaceConfig（default + agentId，agentId 优先）。
     */
    public WorkspaceConfig loadWorkspaceConfig(String agentId) {
        WorkspaceConfig rootConfig = loadConfigFromFile(AppConstants.MainAgent.configFile(AppConstants.Agents.WORK_AGENT));
        WorkspaceConfig agentConfig = loadConfigFromFile(AppConstants.MainAgent.configFile(agentId));

        WorkspaceConfig merged = new WorkspaceConfig();
        merged.setTools(agentConfig.getTools() != null && !agentConfig.getTools().isEmpty()
                ? agentConfig.getTools() : rootConfig.getTools());
        merged.setMcpServers(agentConfig.getMcpServers() != null && !agentConfig.getMcpServers().isEmpty()
                ? agentConfig.getMcpServers() : rootConfig.getMcpServers());
        merged.setSkills(agentConfig.getSkills() != null && !agentConfig.getSkills().isEmpty()
                ? agentConfig.getSkills() : rootConfig.getSkills());
        merged.setSubagents(agentConfig.getSubagents() != null && !agentConfig.getSubagents().isEmpty()
                ? agentConfig.getSubagents() : rootConfig.getSubagents());
        return merged;
    }

    private WorkspaceConfig loadConfigFromFile(Path configFile) {
        if (Files.exists(configFile)) {
            try {
                String content = Files.readString(configFile, StandardCharsets.UTF_8);
                return JsonUtils.fromJson(content, WorkspaceConfig.class);
            } catch (IOException e) {
                log.error("读取配置文件失败: {}", configFile, e);
            }
        }
        return new WorkspaceConfig();
    }
}
