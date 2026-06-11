package cn.bitloom.agentic.agent;

import cn.bitloom.agentic.hook.AgentHook;
import cn.bitloom.agentic.hook.JournalHook;
import cn.bitloom.agentic.hook.MemoryConsolidateHook;
import cn.bitloom.agentic.memory.JournalManager;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.tool.Toolkit;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.exception.AgentException;
import cn.bitloom.exception.StorageException;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 智能体管理器，统一管理所有 Agent（MAIN 和 SUBAGENT）。
 * 合并了原 AgentFactory 的功能，支持懒加载。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentManager {

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();
    private final Map<String, AgentDefinition> definitions = new ConcurrentHashMap<>();

    private final ModelFactory modelFactory;
    @Getter
    private final SkillManager skillManager;
    private final Toolkit toolkit;
    private final JournalManager journalManager;

    @PostConstruct
    public void init() {
        // 扫描 agents/ 目录，加载所有 AgentDefinition
        loadAllDefinitions();
        // 只预加载 default 主智能体
        getAgent("default");
    }

    /**
     * 扫描 agents/ 目录，加载所有 agent.md 定义
     */
    private void loadAllDefinitions() {
        Path agentsDir = AppConstants.Base.AGENTS_DIR;
        if (!Files.exists(agentsDir)) {
            return;
        }

        try (Stream<Path> dirs = Files.list(agentsDir)) {
            dirs.filter(Files::isDirectory)
                    .forEach(dir -> {
                        String agentId = dir.getFileName().toString();
                        Path agentMd = dir.resolve("agent.md");
                        if (Files.exists(agentMd)) {
                            try {
                                AgentDefinition definition = AgentDefinition.fromMarkdown(agentMd);
                                definitions.put(agentId, definition);
                                log.info("加载智能体定义: agentId={}, kind={}", agentId, definition.kind());
                            } catch (Exception e) {
                                log.error("加载智能体定义失败: {}", agentMd, e);
                            }
                        }
                    });
        } catch (IOException e) {
            log.error("扫描 agents/ 目录失败", e);
        }
    }

    /**
     * 获取 Agent，懒加载
     */
    public Agent getAgent(String agentId) {
        return agents.computeIfAbsent(agentId, this::createAgent);
    }

    /**
     * 创建 Agent 实例
     */
    private Agent createAgent(String agentId) {
        AgentDefinition definition = definitions.get(agentId);
        if (definition == null) {
            // 尝试从文件系统加载
            Path agentMd = AppConstants.Base.agentDefinitionFile(agentId);
            if (Files.exists(agentMd)) {
                definition = AgentDefinition.fromMarkdown(agentMd);
                definitions.put(agentId, definition);
            } else {
                // 如果没有 agent.md，创建默认的 MAIN 定义
                definition = new AgentDefinition(agentId, "默认智能体", AgentKind.MAIN,
                        null, List.of(), List.of(), List.of(), "default", "");
                definitions.put(agentId, definition);
            }
        }

        List<AgentHook> hooks = buildHooks(definition);

        Agent agent = Agent.builder()
                .agentId(agentId)
                .definition(definition)
                .modelFactory(modelFactory)
                .tools(toolkit.buildToolCallbacks(definition))
                .hooks(hooks)
                .build();

        log.info("创建智能体: agentId={}, kind={}", agentId, definition.kind());
        return agent;
    }

    /**
     * 构建 Hook 列表（默认 Hook）
     */
    private List<AgentHook> buildHooks(AgentDefinition definition) {
        List<AgentHook> hooks = new ArrayList<>();
        if (definition.kind() == AgentKind.MAIN) {
            hooks.add(new MemoryConsolidateHook(modelFactory));
            hooks.add(new JournalHook(journalManager));
        }
        return hooks;
    }

    // ===== 系统提示词构建 =====

    /**
     * 构建系统提示词：
     * MAIN 智能体从 agents/{agentId}/ 下的 .md 文件加载
     * SUBAGENT 智能体直接使用 definition.content()
     */
    public String buildSystemPrompt(String agentId) {
        AgentDefinition definition = definitions.get(agentId);
        if (definition == null || definition.kind() == AgentKind.SUBAGENT) {
            return definition != null ? definition.content() : "";
        }

        StringBuilder sb = new StringBuilder();

        // 运行环境信息
        Path workspaceDir = AppConstants.Base.agentWorkspaceDir(agentId);
        sb.append("""

                
                # 运行环境
                
                - 工作目录: %s
                - 当前时间: %s
                - 智能体: %s
                - 平台: %s (%s)
                """.formatted(
                workspaceDir,
                LocalDateTime.now(),
                agentId,
                System.getProperty("os.name"),
                System.getProperty("os.version")
        ));

        // 收集 agents/{agentId}/ 下的 .md 文件名（用于覆盖判断）
        Path agentDir = AppConstants.Base.agentDir(agentId);
        Set<String> agentFileNames = new HashSet<>();
        if (Files.exists(agentDir) && Files.isDirectory(agentDir)) {
            try (Stream<Path> stream = Files.list(agentDir)) {
                stream.filter(p -> p.toString().endsWith(".md"))
                        .filter(p -> !p.getFileName().toString().equals("agent.md"))
                        .forEach(p -> agentFileNames.add(p.getFileName().toString()));
            } catch (IOException e) {
                log.error("列出目录文件失败: {}", agentDir, e);
            }
        }

        // 1. 读取根目录 AGENTS.md（agents/{agentId} 有同名文件则跳过）
        if (!agentFileNames.contains("AGENTS.md")) {
            appendFileContent(sb, AppConstants.Base.AGENTS_MD);
        }

        // 2. 读取根目录 MEMORY.md（agents/{agentId} 有同名文件则跳过）
        if (!agentFileNames.contains("MEMORY.md")) {
            appendFileContent(sb, AppConstants.Base.MEMORY_MD);
        }

        // 3. 读取 agents/{agentId}/*.md（覆盖或扩展根目录文件，排除 agent.md）
        if (Files.exists(agentDir) && Files.isDirectory(agentDir)) {
            try (Stream<Path> stream = Files.list(agentDir)) {
                stream.filter(p -> p.toString().endsWith(".md"))
                        .filter(p -> !p.getFileName().toString().equals("agent.md"))
                        .sorted()
                        .forEach(p -> appendFileContent(sb, p));
            } catch (IOException e) {
                log.error("列出目录文件失败: {}", agentDir, e);
            }
        }

        return sb.toString();
    }

    private void appendFileContent(StringBuilder sb, Path file) {
        if (Files.exists(file)) {
            try {
                sb.append(Files.readString(file, StandardCharsets.UTF_8));
                sb.append("\n");
            } catch (IOException e) {
                log.error("读取文件失败: {}", file, e);
            }
        }
    }

    // ===== 配置加载 =====

    /**
     * 获取指定智能体的 WorkspaceConfig
     */
    public WorkspaceConfig getWorkspaceConfig(String agentId) {
        return toolkit.loadWorkspaceConfig(agentId);
    }

    // ===== CRUD 方法 =====

    /**
     * 获取所有子智能体定义
     */
    public List<AgentDefinition> listSubagentDefinitions() {
        return definitions.values().stream()
                .filter(d -> d.kind() == AgentKind.SUBAGENT)
                .toList();
    }

    /**
     * 获取所有主智能体定义
     */
    public List<AgentDefinition> listMainAgentDefinitions() {
        return definitions.values().stream()
                .filter(d -> d.kind() == AgentKind.MAIN)
                .toList();
    }

    public boolean exists(String name) {
        return definitions.containsKey(name);
    }

    public int count() {
        return definitions.size();
    }

    public AgentDefinition getDefinition(String agentId) {
        return definitions.get(agentId);
    }

    /**
     * 复制主智能体
     */
    public void copyMainAgent(String sourceAgentId, String targetAgentId) {
        Path sourceDir = AppConstants.Base.agentDir(sourceAgentId);
        Path targetDir = AppConstants.Base.agentDir(targetAgentId);

        if (!Files.exists(sourceDir)) {
            throw AgentException.subagentNotFound(sourceAgentId);
        }

        try {
            // 复制 agents/ 目录
            copyDirectory(sourceDir, targetDir);

            // 创建 workspace/ 目录
            Path targetWorkspace = AppConstants.Base.agentWorkspaceDir(targetAgentId);
            Files.createDirectories(targetWorkspace);

            // 重新加载定义
            AgentDefinition newDef = AgentDefinition.fromMarkdown(targetDir.resolve("agent.md"));
            definitions.put(targetAgentId, newDef);

            log.info("复制主智能体: {} -> {}", sourceAgentId, targetAgentId);
        } catch (IOException e) {
            throw StorageException.writeError(targetAgentId, e);
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (Stream<Path> stream = Files.list(source)) {
            stream.forEach(path -> {
                try {
                    Path targetPath = target.resolve(path.getFileName().toString());
                    if (Files.isDirectory(path)) {
                        copyDirectory(path, targetPath);
                    } else {
                        Files.copy(path, targetPath);
                    }
                } catch (IOException e) {
                    log.error("复制文件失败: {}", path, e);
                }
            });
        }
    }

    // ===== Agent 文件夹管理 =====

    public List<AgentFolder> loadAgentFolders() {
        List<AgentFolder> result = new ArrayList<>();
        Path agentsDir = AppConstants.Base.AGENTS_DIR;

        if (!Files.exists(agentsDir)) {
            try {
                Files.createDirectories(agentsDir);
            } catch (IOException e) {
                log.error("Failed to create agents directory", e);
                return result;
            }
        }

        try (Stream<Path> dirs = Files.list(agentsDir)) {
            dirs.filter(Files::isDirectory)
                    .forEach(dir -> {
                        AgentFolder agent = new AgentFolder(dir.getFileName().toString(), dir);
                        try (Stream<Path> files = Files.list(dir)) {
                            files.filter(Files::isRegularFile)
                                    .filter(p -> p.toString().endsWith(".md"))
                                    .forEach(agent::addFile);
                        } catch (IOException e) {
                            log.error("Failed to list files in agent folder: {}", dir, e);
                        }
                        result.add(agent);
                    });
        } catch (IOException e) {
            log.error("Failed to list agent folders", e);
        }

        return result;
    }

    // ===== 内部 record =====

    @Getter
    public static class AgentFolder {
        private final String name;
        private final Path path;
        private final List<AgentFile> files = new ArrayList<>();

        public AgentFolder(String name, Path path) {
            this.name = name;
            this.path = path;
        }

        public void addFile(Path file) {
            files.add(new AgentFile(file));
        }
    }

    @Getter
    public static class AgentFile {
        private final Path path;
        private final String displayName;

        public AgentFile(Path path) {
            this.path = path;
            String fileName = path.getFileName().toString();
            this.displayName = fileName.endsWith(".md")
                    ? fileName.substring(0, fileName.length() - 3)
                    : fileName;
        }
    }
}
