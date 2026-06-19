package cn.bitloom.agentic.agent;

import cn.bitloom.constant.AppConstants;
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
     * 从 classpath 加载所有子智能体定义（subagent 目录下的 agent.md）
     */
    private void loadSubagentDefinitions() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:subagent/*/agent.md");
            for (Resource resource : resources) {
                try {
                    String markdown = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    AgentDefinition definition = AgentDefinition.fromMarkdown(markdown);
                    definitions.put(definition.name(), definition);
                    log.info("加载子智能体定义: {}", definition.name());
                } catch (Exception e) {
                    log.warn("加载子智能体定义失败: {}", resource, e);
                }
            }
        } catch (IOException e) {
            log.warn("扫描子智能体目录失败", e);
        }
    }

    /**
     * 从 agents 目录加载所有主智能体定义
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
     * 获取指定名称的定义（MAIN 或 SUBAGENT）
     *
     * @param name 定义名称
     * @return AgentDefinition，不存在返回 null
     */
    public AgentDefinition getDefinition(String name) {
        return definitions.get(name);
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
     * 获取指定的 SUBAGENT 定义（按名称列表过滤）。
     * 如果 names 为空或 null，返回所有 SUBAGENT 定义。
     *
     * @param names 允许的子智能体名称列表
     * @return 过滤后的 SUBAGENT 定义列表
     */
    public List<AgentDefinition> getSubagentDefinitions(List<String> names) {
        List<AgentDefinition> all = getSubagentDefinitions();
        if (names == null || names.isEmpty()) {
            return all;
        }
        Set<String> allowed = Set.copyOf(names);
        return all.stream()
                .filter(d -> allowed.contains(d.name()))
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
     * 如果缓存中没有，尝试从文件系统加载。
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
            definitions.put(agentId, definition);
            return definition;
        }

        // 如果没有 agent.md，创建默认的 MAIN 定义
        definition = new AgentDefinition(agentId, "默认智能体", AgentKind.MAIN,
                null, List.of(), List.of(), List.of(), "default", "");
        definitions.put(agentId, definition);
        return definition;
    }
}
