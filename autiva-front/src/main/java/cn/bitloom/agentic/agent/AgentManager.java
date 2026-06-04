package cn.bitloom.agentic.agent;

import cn.bitloom.agentic.util.MarkdownParser;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.exception.AgentException;
import cn.bitloom.exception.StorageException;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
@Component
public class AgentManager {

    private final Map<String, AgentConfig> agents = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        registerAgents();
    }

    private void registerAgents() {
        for (AgentIdentityEnum identity : AgentIdentityEnum.values()) {
            if (!identity.isMain()) {
                continue;
            }
            Path dir = AppConstants.Base.WORKSPACE_DIR.resolve(identity.name());
            agents.put(identity.name(), new AgentConfig(identity.name(), AgentIdentityEnum.AgentCategory.MAIN, dir));
        }

        loadSubagentConfigs().forEach(config ->
                agents.put(config.name(), config)
        );
    }

    public void reloadSubagents() {
        agents.entrySet().removeIf(e -> e.getValue().type() == AgentIdentityEnum.AgentCategory.SUBAGENT);
        loadSubagentConfigs().forEach(config ->
                agents.put(config.name(), config)
        );
    }

    private List<AgentConfig> loadSubagentConfigs() {
        List<AgentConfig> configs = new ArrayList<>();
        for (AgentIdentityEnum identity : AgentIdentityEnum.values()) {
            if (!identity.isSubagent() || identity == AgentIdentityEnum.A2A) {
                continue;
            }
            Path dir = AppConstants.Base.WORKSPACE_DIR.resolve(identity.name());
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> paths = Files.list(dir)) {
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
                                configs.add(new AgentConfig(name, AgentIdentityEnum.AgentCategory.SUBAGENT, p, description));
                            } catch (IOException e) {
                                log.error("读取子智能体配置失败: {}", p, e);
                            }
                        });
            } catch (IOException e) {
                log.error("列出子智能体目录失败: {}", dir, e);
            }
        }
        return configs;
    }

    public List<AgentInfo> listSubagents() {
        return agents.entrySet().stream()
                .filter(e -> e.getValue().type() == AgentIdentityEnum.AgentCategory.SUBAGENT)
                .map(entry -> new AgentInfo(
                        entry.getKey(),
                        entry.getValue().type().name()
                ))
                .toList();
    }

    public String buildSystemPrompt(AgentIdentityEnum identity) {
        StringBuilder sb = new StringBuilder();

        sb.append("""
                
                
                # 运行环境
                
                - 工作目录: %s
                - 当前时间: %s
                - 智能体: %s
                - 平台: %s (%s)
                """.formatted(
                AppConstants.Base.WORKSPACE_DIR.resolve(identity.name()),
                LocalDateTime.now(),
                identity,
                System.getProperty("os.name"),
                System.getProperty("os.version")
        ));

        Path dirPath = AppConstants.Base.WORKSPACE_DIR.resolve(identity.name());

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
        if (config == null || config.type() != AgentIdentityEnum.AgentCategory.SUBAGENT) {
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
        if (config == null || config.type() != AgentIdentityEnum.AgentCategory.SUBAGENT) {
            throw AgentException.subagentNotFound(name);
        }
        try {
            Files.writeString(config.path(), content, StandardCharsets.UTF_8);
            log.info("保存子智能体配置: {}", name);
            reloadSubagents();
        } catch (IOException e) {
            throw StorageException.writeError(name, e);
        }
    }

    public void createSubagentConfig(String name, String description, String content) {
        String kind = resolveSubagentKind(content);
        Path typeDir = AppConstants.Base.WORKSPACE_DIR.resolve(kind);
        if (!Files.exists(typeDir)) {
            try {
                Files.createDirectories(typeDir);
            } catch (IOException e) {
                throw StorageException.dirError(kind, e);
            }
        }

        Path filePath = typeDir.resolve(name + ".md");
        if (Files.exists(filePath)) {
            throw AgentException.subagentAlreadyExists(name);
        }

        String fullContent = "---\nname: " + name + "\nkind: " + kind + "\ndescription: " + description + "\n---\n\n" + content;
        try {
            Files.writeString(filePath, fullContent, StandardCharsets.UTF_8);
            log.info("创建子智能体配置: {}", name);
            reloadSubagents();
        } catch (IOException e) {
            throw StorageException.writeError(name, e);
        }
    }

    private String resolveSubagentKind(String content) {
        if (content != null && !content.isBlank()) {
            MarkdownParser parser = new MarkdownParser(content);
            Object kindValue = parser.getFrontMatter().get("kind");
            if (kindValue != null && !kindValue.toString().isBlank()) {
                return kindValue.toString().trim().toUpperCase();
            }
        }
        return AgentIdentityEnum.GENERIC.name();
    }

    public void deleteSubagentConfig(String name) {
        AgentConfig config = agents.get(name);
        if (config == null || config.type() != AgentIdentityEnum.AgentCategory.SUBAGENT) {
            throw AgentException.subagentNotFound(name);
        }
        try {
            Files.deleteIfExists(config.path());
            agents.remove(name);
            log.info("删除子智能体配置: {}", name);
        } catch (IOException e) {
            throw StorageException.writeError(name, e);
        }
    }

    public boolean exists(String name) {
        return agents.containsKey(name);
    }

    public int count() {
        return agents.size();
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

    public record AgentConfig(String name, AgentIdentityEnum.AgentCategory type, Path path, String description) {
        public AgentConfig(String name, AgentIdentityEnum.AgentCategory type, Path path) {
            this(name, type, path, "");
        }
    }

    public record AgentInfo(String name, String type) {
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
}
