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
                # 身份
                
                你是 Autiva，一个通用型 AI 助手。你帮助用户完成各种任务。
                
                - 专业、高效、可靠
                - 话不多，但事儿办得漂亮
                - 用中文交流
                """);

        templates.put("SOUL.md", """
                # 行为准则
                
                - **直接行动，不要客套。** 省略"问得好！"和"我很乐意帮忙！"之类的客套话——直接帮忙
                - **先思考再提问。** 试着自己弄清楚。查上下文、搜索。然后如果卡住了再问
                - **通过能力赢得信任。** 对外部操作要小心，对内部操作要大胆
                - **质量优先。** 遵循最佳实践，输出简洁可靠
                
                # 工作方式
                
                - 先理解上下文，再采取行动
                - 遵循用户已有的习惯和约定
                - 做事前先理解影响范围
                - 安全第一，不执行危险操作
                
                # 边界
                
                - 私人数据必须保持私密
                - 破坏性操作前先确认
                - 有疑问时，先询问
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
                agents.put(config.getName(), config)
        );
    }

    public void reloadSubagents() {
        agents.entrySet().removeIf(e -> e.getValue().getType() == AgentType.SUBAGENT);
        loadSubagentConfigs().forEach(config ->
                agents.put(config.getName(), config)
        );
    }

    private List<AgentConfig> loadSubagentConfigs() {
        List<AgentConfig> result = new ArrayList<>();
        Path subagentDir = AppConstants.Base.SUBAGENT_DIR;

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
                            result.add(new AgentConfig(name, AgentType.SUBAGENT, p, description));
                        } catch (IOException e) {
                            log.error("读取子智能体配置失败: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            log.error("列出子智能体目录失败", e);
        }

        return result;
    }

    public void bindSession(String agentName, String sessionId) {
        agentSessionMap.put(agentName, sessionId);
        log.info("绑定智能体 {} 到会话 {}", agentName, sessionId);
    }

    public String getSessionByAgent(String agentName) {
        return agentSessionMap.get(agentName);
    }

    public List<AgentInfo> listAgents() {
        return agents.entrySet().stream()
                .map(entry -> new AgentInfo(
                        entry.getKey(),
                        entry.getValue().getType().name(),
                        agentSessionMap.get(entry.getKey())
                ))
                .toList();
    }

    public List<AgentInfo> listMainAgents() {
        return agents.entrySet().stream()
                .filter(e -> e.getValue().getType() == AgentType.MAIN)
                .map(entry -> new AgentInfo(
                        entry.getKey(),
                        AgentType.MAIN.name(),
                        agentSessionMap.get(entry.getKey())
                ))
                .toList();
    }

    public List<AgentInfo> listSubagents() {
        return agents.entrySet().stream()
                .filter(e -> e.getValue().getType() == AgentType.SUBAGENT)
                .map(entry -> new AgentInfo(
                        entry.getKey(),
                        AgentType.SUBAGENT.name(),
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
        if (config == null || config.getType() != AgentType.SUBAGENT) {
            return null;
        }
        try {
            return Files.readString(config.getPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取子智能体配置失败: {}", name, e);
            return null;
        }
    }

    public void saveSubagentConfig(String name, String content) {
        AgentConfig config = agents.get(name);
        if (config == null || config.getType() != AgentType.SUBAGENT) {
            throw new IllegalArgumentException("子智能体不存在: " + name);
        }
        try {
            Files.writeString(config.getPath(), content, StandardCharsets.UTF_8);
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
        if (config == null || config.getType() != AgentType.SUBAGENT) {
            throw new IllegalArgumentException("子智能体不存在: " + name);
        }
        try {
            Files.deleteIfExists(config.getPath());
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
        return config != null ? config.getType() : null;
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
        List<SubagentFolder> result = new ArrayList<>();
        Path subagentDir = AppConstants.Base.SUBAGENT_DIR;

        if (!Files.exists(subagentDir) || !Files.isDirectory(subagentDir)) {
            return result;
        }

        try (Stream<Path> files = Files.list(subagentDir)) {
            files.filter(Files::isRegularFile)
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
                            result.add(new SubagentFolder(name, description, p));
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

    @Getter
    public static class AgentConfig {
        private final String name;
        private final AgentType type;
        private final Path path;
        private final String description;

        public AgentConfig(String name, AgentType type, Path path) {
            this(name, type, path, "");
        }

        public AgentConfig(String name, AgentType type, Path path, String description) {
            this.name = name;
            this.type = type;
            this.path = path;
            this.description = description;
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

    @Getter
    public static class SubagentFolder {
        private final String name;
        private final String description;
        private final Path path;

        public SubagentFolder(String name, String description, Path path) {
            this.name = name;
            this.description = description;
            this.path = path;
        }
    }
}
