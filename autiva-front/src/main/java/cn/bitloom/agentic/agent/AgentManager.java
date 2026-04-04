package cn.bitloom.agentic.agent;

import cn.bitloom.constant.AppConstants;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
@Component
public class AgentManager {

    private final Map<String, AbstractAgent> agents = new ConcurrentHashMap<>();
    private final Map<String, String> agentSessionMap = new ConcurrentHashMap<>();
    private final Map<String, String> currentSubAgentMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        initAgentWorkspaces();
    }

    private void initAgentWorkspaces() {
        Path mainDir = AppConstants.Base.WORKSPACE_DIR.resolve(AgentIdentityEnum.MAIN.name());
        Path doctorDir = AppConstants.Base.WORKSPACE_DIR.resolve(AgentIdentityEnum.DOCTOR.name());
        
        if (!Files.exists(mainDir)) {
            try {
                Files.createDirectories(mainDir);
                log.info("初始化工作目录: {}", mainDir);
            } catch (IOException e) {
                log.error("初始化工作目录失败: main", e);
            }
        }
        
        if (!Files.exists(doctorDir)) {
            try {
                Files.createDirectories(doctorDir);
                log.info("初始化工作目录: {}", doctorDir);
            } catch (IOException e) {
                log.error("初始化工作目录失败: doctor", e);
            }
        }
    }

    public void register(String name, AbstractAgent agent) {
        agents.put(name, agent);
        log.info("注册智能体: {}", name);
    }

    public void unregister(String name) {
        agents.remove(name);
        agentSessionMap.remove(name);
        currentSubAgentMap.remove(name);
        log.info("注销智能体: {}", name);
    }

    public AbstractAgent getAgent(String name) {
        return agents.get(name);
    }

    public void bindSession(String agentName, String sessionId) {
        agentSessionMap.put(agentName, sessionId);
        log.info("绑定智能体 {} 到会话 {}", agentName, sessionId);
    }

    public String getSessionByAgent(String agentName) {
        return agentSessionMap.get(agentName);
    }

    public AbstractAgent getAgentBySession(String sessionId) {
        return agents.entrySet().stream()
                .filter(entry -> sessionId.equals(agentSessionMap.get(entry.getKey())))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    public List<AgentInfo> listAgents() {
        return agents.entrySet().stream()
                .map(entry -> new AgentInfo(
                        entry.getKey(),
                        entry.getValue().getStatus(),
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

    public boolean exists(String name) {
        return agents.containsKey(name);
    }

    public int count() {
        return agents.size();
    }

    public List<AgentFolder> loadAgentFolders() {
        List<AgentFolder> agents = new ArrayList<>();
        Path workspaceDir = AppConstants.Base.WORKSPACE_DIR;
        
        if (!Files.exists(workspaceDir)) {
            try {
                Files.createDirectories(workspaceDir);
            } catch (IOException e) {
                log.error("Failed to create workspace directory", e);
                return agents;
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
                    agents.add(agent);
                });
        } catch (IOException e) {
            log.error("Failed to list agent folders", e);
        }
        
        return agents;
    }

    public record AgentInfo(String name, AgentStatusEnum status, String sessionId) {}

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

        public String getName() {
            return name;
        }

        public Path getPath() {
            return path;
        }

        public List<AgentFile> getFiles() {
            return files;
        }
    }

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

        public Path getPath() {
            return path;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
