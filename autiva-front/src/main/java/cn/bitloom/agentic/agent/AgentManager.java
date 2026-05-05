package cn.bitloom.agentic.agent;

import cn.bitloom.agentic.util.MarkdownParser;
import cn.bitloom.constant.AppConstants;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
@Component
public class AgentManager {

    private final Map<String, AgentConfig> agents = new ConcurrentHashMap<>();
    private final Map<String, String> agentSessionMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        initAgentWorkspaces();
        initSubagentWorkspace();
        registerAgents();
    }

    private void initAgentWorkspaces() {
        if (!Files.exists(AppConstants.Base.CONFIG_FILE)) {
            try {
                Files.createFile(AppConstants.Base.CONFIG_FILE);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        for (AgentIdentityEnum identity : AgentIdentityEnum.values()) {
            Path dir = AppConstants.Base.WORKSPACE_DIR.resolve(identity.name());
            if (!Files.exists(dir)) {
                try {
                    Files.createDirectories(dir);
                    log.info("初始化工作目录: {}", dir);
                } catch (IOException e) {
                    log.error("初始化工作目录失败: {}", identity.name(), e);
                }
            }
            initDefaultTemplates(dir, identity);
        }
    }

    private void initDefaultTemplates(Path agentDir, AgentIdentityEnum identity) {
        Map<String, String> templates = getDefaultTemplates(identity);
        templates.forEach((fileName, content) -> {
            Path filePath = agentDir.resolve(fileName);
            if (!Files.exists(filePath)) {
                try {
                    Files.writeString(filePath, content, java.nio.charset.StandardCharsets.UTF_8);
                    log.info("创建默认模板: {}", filePath);
                } catch (IOException e) {
                    log.error("创建默认模板失败: {}", fileName, e);
                }
            }
        });
    }

    private Map<String, String> getDefaultTemplates(AgentIdentityEnum identity) {
        Map<String, String> templates = new java.util.LinkedHashMap<>();

        templates.put("IDENTITY.md", """
                # 身份定义

                你是 Autiva 的主智能体（%s），你的核心角色是**调度者和协调者**。

                ## 安全边界

                - 你**没有**文件读写和Shell执行能力
                - 所有文件操作、代码编写、命令执行必须通过 Task 工具委派给子智能体
                - 你只负责理解需求、拆解任务、选择子智能体、汇总结果
                """.formatted(identity.name()));

        templates.put("SOUL.md", """
                # 行为准则

                - **直接行动，不要客套。** 省略"问得好！"和"我很乐意帮忙！"之类的客套话——直接帮忙
                - **先思考再提问。** 试着自己弄清楚。查上下文、搜索。然后如果卡住了再问
                - **通过能力赢得信任。** 对外部操作要小心，对内部操作要大胆
                - **质量优先。** 遵循最佳实践，输出简洁可靠

                # 工作方式

                1. **理解需求** — 分析用户的请求，明确任务目标
                2. **拆解任务** — 将复杂任务分解为可委派的子任务
                3. **选择子智能体** — 根据任务类型选择最合适的子智能体
                4. **委派执行** — 通过 Task 工具将子任务委派给子智能体
                5. **汇总结果** — 收集子智能体的返回结果，整合后回复用户

                # 边界

                - 私人数据必须保持私密
                - 破坏性操作前先确认
                - 有疑问时，先询问

                # 子智能体使用策略（必须遵守）

                ## 强制规则

                - **禁止直接编写代码**：你没有文件操作和Shell执行工具，所有代码编写、文件修改、命令执行必须通过 Task 工具委派给子智能体
                - **你是调度者**：你的核心能力是理解需求、拆解任务、选择合适的子智能体、汇总结果
                - **不要尝试自己完成**：如果你发现自己需要读写文件或执行命令，请立即使用 Task 工具

                ## 子智能体选择规则

                | 任务类型 | 必须使用的子智能体 | 说明 |
                |---------|------------------|------|
                | 编写/修改代码 | Code | 唯一拥有文件读写和Shell执行能力的代理 |
                | 修复bug | Code | 需要读取和修改代码文件 |
                | 重构代码 | Code | 需要修改现有代码 |
                | 创建新文件 | Code | 需要写入文件 |
                | 探索代码库 | Explore | 快速搜索和浏览代码 |
                | 制定实现计划 | Plan | 设计实现策略 |
                | 执行Shell命令 | Bash | 运行构建、测试、部署等 |
                | 复杂多步骤研究 | General Purpose | 通用研究和多步骤任务 |

                ## 使用原则

                - **主动使用**：当任务与子智能体的描述匹配时，主动使用 Task 工具，无需等待用户指示
                - **并行执行**：可以并行启动多个子智能体以提高效率，发送包含多个 Task 工具调用的单条消息
                - **减少上下文**：在进行文件搜索时，优先使用子智能体以减少上下文使用
                - **明确指令**：给子智能体的 prompt 要清晰、具体，包含所有必要的上下文

                ## 示例

                - 用户说"帮我写一个 Hello World"：使用 Code 子智能体（subagent_type="Code"）
                - 用户说"修复这个bug"：使用 Code 子智能体（subagent_type="Code"）
                - 用户说"重构这个函数"：使用 Code 子智能体（subagent_type="Code"）
                - 用户说"客户端的错误在哪里处理？"：使用 Explore 子智能体（subagent_type="Explore"）
                - 用户说"帮我规划一个新功能"：使用 Plan 子智能体（subagent_type="Plan"）
                - 用户说"运行构建"：使用 Bash 子智能体（subagent_type="Bash"）

                # 工具使用策略

                ## 核心原则

                - **Task 工具优先**：对于所有需要文件操作或命令执行的任务，使用 Task 工具委派给子智能体
                - **WebFetch 用于轻量查询**：当你只需要获取一个网页内容时，可以直接使用 WebFetch
                - **并行调用**：当多个工具调用之间没有依赖关系时，并行执行以提高效率

                ## 网页获取

                - 使用 WebFetch 工具获取网页内容
                - 当 WebFetch 返回重定向时，使用新的 URL 重新请求
                - 不要为用户生成或猜测 URL，除非确信它有助于编程

                # 任务管理

                ## TodoWrite 工具使用

                使用 TodoWrite 工具来管理和规划任务。这有助于：
                - 跟踪任务进度
                - 向用户提供进度的可见性
                - 将较大的复杂任务分解为较小的步骤

                ## 规则

                - **频繁使用**：非常频繁地使用 TodoWrite 工具
                - **立即标记**：完成任务后立即将待办事项标记为已完成
                - **不要批量**：不要在标记多个任务为已完成之前批量处理它们

                """);

        templates.put("MEMORY.md", """
                # 长期记忆
                
                记录重要的决策、上下文和需要记住的事情。当你需要跨会话记住信息时，写入此文件。
                """);

        templates.put("USER.md", """
                # 用户偏好
                
                - 时区: Asia/Shanghai (GMT+8)
                - 语言: 中文
                """);

        templates.put("TOOLS.md", """
                # 工具笔记
                
                记录项目特定的工具配置和常用命令。
                
                ## 示例
                
                ```markdown
                ### 构建
                - Java项目: mvn clean install
                - 前端项目: npm run build
                
                ### 测试
                - 单元测试: mvn test
                ```
                """);

        return templates;
    }

    private void initSubagentWorkspace() {
        Path subagentDir = AppConstants.Base.SUBAGENT_DIR;
        if (!Files.exists(subagentDir)) {
            try {
                Files.createDirectories(subagentDir);
                log.info("初始化子智能体目录: {}", subagentDir);
            } catch (IOException e) {
                log.error("初始化子智能体目录失败", e);
                return;
            }
        }

        String[] defaultSubagents = {
                "CODE_SUBAGENT.md",
                "GENERAL_PURPOSE_SUBAGENT.md",
                "EXPLORE_SUBAGENT.md",
                "PLAN_SUBAGENT.md",
                "BASH_SUBAGENT.md"
        };

        for (String fileName : defaultSubagents) {
            Path targetPath = subagentDir.resolve(fileName);
            if (!Files.exists(targetPath)) {
                try {
                    ClassPathResource resource = new ClassPathResource("agent/" + fileName);
                    if (resource.exists()) {
                        Files.copy(resource.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                        log.info("复制默认子智能体配置: {}", fileName);
                    }
                } catch (IOException e) {
                    log.error("复制子智能体配置失败: {}", fileName, e);
                }
            }
        }
    }

    private void registerAgents() {
        for (AgentIdentityEnum identity : AgentIdentityEnum.values()) {
            Path dir = AppConstants.Base.WORKSPACE_DIR.resolve(identity.name());
            agents.put(identity.name(), new AgentConfig(identity.name(), AgentType.MAIN, dir));
        }

        loadSubagentConfigs().forEach(config ->
                agents.put(config.name(), config)
        );
    }

    public void reloadSubagents() {
        agents.entrySet().removeIf(e -> e.getValue().type() == AgentType.SUBAGENT);
        loadSubagentConfigs().forEach(config ->
                agents.put(config.name(), config)
        );
    }

    private List<AgentConfig> loadSubagentConfigs() {
        return parseSubagentFiles(AppConstants.Base.SUBAGENT_DIR).stream()
                .map(parsed -> new AgentConfig(parsed.name(), AgentType.SUBAGENT, parsed.path(), parsed.description()))
                .toList();
    }

    public void bindSession(String agentName, String sessionId) {
        agentSessionMap.put(agentName, sessionId);
        log.info("绑定智能体 {} 到会话 {}", agentName, sessionId);
    }

    public String getSessionByAgent(String agentName) {
        return agentSessionMap.get(agentName);
    }

    public List<AgentInfo> listAgents() {
        return listAgentsByType(null);
    }

    public List<AgentInfo> listMainAgents() {
        return listAgentsByType(AgentType.MAIN);
    }

    public List<AgentInfo> listSubagents() {
        return listAgentsByType(AgentType.SUBAGENT);
    }

    private List<AgentInfo> listAgentsByType(AgentType type) {
        return agents.entrySet().stream()
                .filter(e -> type == null || e.getValue().type() == type)
                .map(entry -> new AgentInfo(
                        entry.getKey(),
                        entry.getValue().type().name(),
                        agentSessionMap.get(entry.getKey())
                ))
                .toList();
    }

    public String getDescription(String agentName) {
        StringBuilder sb = new StringBuilder();
        Path dirPath = AppConstants.Base.WORKSPACE_DIR.resolve(agentName);

        if (Files.exists(dirPath) && Files.isDirectory(dirPath)) {
            try (Stream<Path> stream = Files.list(dirPath)) {
                stream.filter(p -> p.toString().endsWith(".md"))
                        .sorted()
                        .forEach(p -> {
                            try {
                                sb.append(Files.readString(p));
                            } catch (IOException e) {
                                log.error("读取文件失败: {}", p, e);
                            }
                            sb.append("\n");
                        });
            } catch (IOException e) {
                log.error("列出目录文件失败: {}", dirPath, e);
            }
        }

        return sb.toString();
    }

    public String getSubagentContent(String name) {
        AgentConfig config = agents.get(name);
        if (config == null || config.type() != AgentType.SUBAGENT) {
            return null;
        }
        try {
            return Files.readString(config.path(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取子智能体配置失败: {}", name, e);
            return null;
        }
    }

    public void saveSubagentConfig(String name, String content) {
        AgentConfig config = agents.get(name);
        if (config == null || config.type() != AgentType.SUBAGENT) {
            throw new IllegalArgumentException("子智能体不存在: " + name);
        }
        try {
            Files.writeString(config.path(), content, StandardCharsets.UTF_8);
            log.info("保存子智能体配置: {}", name);
            reloadSubagents();
        } catch (IOException e) {
            log.error("保存子智能体配置失败: {}", name, e);
            throw new RuntimeException("保存子智能体配置失败: " + name, e);
        }
    }

    public void createSubagentConfig(String name, String description, String content) {
        Path filePath = AppConstants.Base.SUBAGENT_DIR.resolve(name + ".md");
        if (Files.exists(filePath)) {
            throw new IllegalArgumentException("子智能体已存在: " + name);
        }

        String fullContent = "---\nname: " + name + "\ndescription: " + description + "\n---\n\n" + content;
        try {
            Files.writeString(filePath, fullContent, StandardCharsets.UTF_8);
            log.info("创建子智能体配置: {}", name);
            reloadSubagents();
        } catch (IOException e) {
            log.error("创建子智能体配置失败: {}", name, e);
            throw new RuntimeException("创建子智能体配置失败: " + name, e);
        }
    }

    public void deleteSubagentConfig(String name) {
        AgentConfig config = agents.get(name);
        if (config == null || config.type() != AgentType.SUBAGENT) {
            throw new IllegalArgumentException("子智能体不存在: " + name);
        }
        try {
            Files.deleteIfExists(config.path());
            agents.remove(name);
            log.info("删除子智能体配置: {}", name);
        } catch (IOException e) {
            log.error("删除子智能体配置失败: {}", name, e);
            throw new RuntimeException("删除子智能体配置失败: " + name, e);
        }
    }

    public boolean exists(String name) {
        return agents.containsKey(name);
    }

    public int count() {
        return agents.size();
    }

    public AgentType getAgentType(String name) {
        AgentConfig config = agents.get(name);
        return config != null ? config.type() : null;
    }

    public List<AgentFolder> loadAgentFolders() {
        List<AgentFolder> result = new ArrayList<>();
        Path workspaceDir = AppConstants.Base.WORKSPACE_DIR;

        if (!Files.exists(workspaceDir)) {
            try {
                Files.createDirectories(workspaceDir);
            } catch (IOException e) {
                log.error("Failed to create workspace directory", e);
                return result;
            }
        }

        try (Stream<Path> dirs = Files.list(workspaceDir)) {
            dirs.filter(Files::isDirectory)
                    .filter(dir -> !dir.getFileName().toString().equals("subagents"))
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

    public List<SubagentFolder> loadSubagentFolders() {
        return parseSubagentFiles(AppConstants.Base.SUBAGENT_DIR).stream()
                .map(parsed -> new SubagentFolder(parsed.name(), parsed.description(), parsed.path()))
                .toList();
    }

    private List<ParsedSubagent> parseSubagentFiles(Path subagentDir) {
        List<ParsedSubagent> result = new ArrayList<>();

        if (!Files.exists(subagentDir) || !Files.isDirectory(subagentDir)) {
            return result;
        }

        try (Stream<Path> paths = Files.list(subagentDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p, StandardCharsets.UTF_8);
                            MarkdownParser parser = new MarkdownParser(content);
                            Map<String, Object> frontMatter = parser.getFrontMatter();
                            String name = frontMatter.containsKey("name")
                                    ? frontMatter.get("name").toString()
                                    : p.getFileName().toString().replace(".md", "");
                            String description = frontMatter.containsKey("description")
                                    ? frontMatter.get("description").toString()
                                    : "";
                            result.add(new ParsedSubagent(name, description, p));
                        } catch (IOException e) {
                            log.error("读取子智能体配置失败: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            log.error("列出子智能体目录失败", e);
        }

        return result;
    }

    public enum AgentType {
        MAIN, SUBAGENT
    }

    public record AgentConfig(String name, AgentType type, Path path, String description) {
            public AgentConfig(String name, AgentType type, Path path) {
                this(name, type, path, "");
            }
    }

    public record AgentInfo(String name, String type, String sessionId) {
    }

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

    public record SubagentFolder(String name, String description, Path path) {
    }

    private record ParsedSubagent(String name, String description, Path path) {
    }
}
