package cn.bitloom.vm;

import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentDefinitionManager;
import cn.bitloom.agentic.agent.AgentKind;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.util.ExecutorManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentPageViewModel {

    private final AgentDefinitionManager definitionManager;

    @Getter
    private final ObservableList<AgentDefinition> mainAgents = FXCollections.observableArrayList();

    public void loadAgents() {
        mainAgents.setAll(definitionManager.getMainAgentIds().stream()
                .map(id -> definitionManager.getOrLoadMainDefinition(id))
                .filter(d -> d != null && d.kind() == AgentKind.MAIN)
                .toList());
    }

    public void loadAgentsAsync(Runnable onLoaded) {
        Task<List<AgentDefinition>> task = new Task<>() {
            @Override
            protected List<AgentDefinition> call() {
                return definitionManager.getMainAgentIds().stream()
                        .map(id -> definitionManager.getOrLoadMainDefinition(id))
                        .filter(d -> d != null && d.kind() == AgentKind.MAIN)
                        .toList();
            }
        };
        task.setOnSucceeded(e -> {
            mainAgents.setAll(task.getValue());
            if (onLoaded != null) onLoaded.run();
        });
        task.setOnFailed(e -> log.error("加载智能体列表失败", task.getException()));
        ExecutorManager.getPlatformTaskExecutor().execute(task);
    }

    public String readFileContent(String agentId, String fileName) {
        Path filePath = AppConstants.Agents.agentDir(agentId).resolve(fileName);
        if (Files.exists(filePath)) {
            try {
                return Files.readString(filePath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("读取文件失败: {}", filePath, e);
            }
        }
        return "";
    }

    public void saveFileContent(String agentId, String fileName, String content) {
        Path filePath = AppConstants.Agents.agentDir(agentId).resolve(fileName);
        try {
            Files.writeString(filePath, content, StandardCharsets.UTF_8);
            // 重新加载该智能体定义
            definitionManager.getOrLoadMainDefinition(agentId);
        } catch (IOException e) {
            log.error("保存文件失败: {}", filePath, e);
            throw new RuntimeException("保存文件失败: " + e.getMessage(), e);
        }
    }

    public void createAgent(String agentId) {
        Path agentDir = AppConstants.Agents.agentDir(agentId);
        if (Files.exists(agentDir)) {
            throw new IllegalArgumentException("智能体已存在: " + agentId);
        }

        try {
            Files.createDirectories(agentDir);

            // 复制 work 模板
            Path workDir = AppConstants.Agents.agentDir(AppConstants.Agents.WORK_AGENT);
            if (Files.exists(workDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(workDir)) {
                    for (Path source : stream) {
                        if (Files.isRegularFile(source)) {
                            Path target = agentDir.resolve(source.getFileName());
                            String content = Files.readString(source, StandardCharsets.UTF_8);
                            // 替换名称
                            if (source.getFileName().toString().equals("agent.md")) {
                                content = content.replace("name: work", "name: " + agentId)
                                        .replace("name: " + AppConstants.Agents.WORK_AGENT, "name: " + agentId);
                            }
                            Files.writeString(target, content, StandardCharsets.UTF_8);
                        }
                    }
                }
            } else {
                // 创建最小模板
                String agentMd = "---\nname: " + agentId + "\ndescription: 新智能体\nkind: main\n---\n";
                Files.writeString(AppConstants.MainAgent.agentFile(agentId), agentMd, StandardCharsets.UTF_8);

                String configJson = "{\"tools\":[],\"mcpServers\":{},\"skills\":[],\"subagents\":[]}";
                Files.writeString(AppConstants.MainAgent.configFile(agentId), configJson, StandardCharsets.UTF_8);
            }

            // 重新加载定义
            definitionManager.getOrLoadMainDefinition(agentId);
        } catch (IOException e) {
            log.error("创建智能体失败: {}", agentId, e);
            throw new RuntimeException("创建智能体失败: " + e.getMessage(), e);
        }
    }

    public void deleteAgent(String agentId) {
        Path agentDir = AppConstants.Agents.agentDir(agentId);
        try {
            if (Files.exists(agentDir)) {
                try (Stream<Path> walk = Files.walk(agentDir)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    Files.delete(p);
                                } catch (IOException e) {
                                    log.warn("删除文件失败: {}", p, e);
                                }
                            });
                }
            }
        } catch (IOException e) {
            log.error("删除智能体失败: {}", agentId, e);
            throw new RuntimeException("删除智能体失败: " + e.getMessage(), e);
        }
    }

    public void copyAgent(String sourceAgentId, String targetAgentId) {
        Path sourceDir = AppConstants.Agents.agentDir(sourceAgentId);
        Path targetDir = AppConstants.Agents.agentDir(targetAgentId);

        if (!Files.exists(sourceDir)) {
            throw new IllegalArgumentException("源智能体不存在: " + sourceAgentId);
        }
        if (Files.exists(targetDir)) {
            throw new IllegalArgumentException("目标智能体已存在: " + targetAgentId);
        }

        try {
            Files.createDirectories(targetDir);

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourceDir)) {
                for (Path source : stream) {
                    if (Files.isRegularFile(source)) {
                        Path target = targetDir.resolve(source.getFileName());
                        String content = Files.readString(source, StandardCharsets.UTF_8);
                        if (source.getFileName().toString().equals("agent.md")) {
                            content = content.replace("name: " + sourceAgentId, "name: " + targetAgentId);
                        }
                        Files.writeString(target, content, StandardCharsets.UTF_8);
                    }
                }
            }

            // 重新加载定义
            definitionManager.getOrLoadMainDefinition(targetAgentId);
        } catch (IOException e) {
            log.error("复制智能体失败: {} -> {}", sourceAgentId, targetAgentId, e);
            throw new RuntimeException("复制智能体失败: " + e.getMessage(), e);
        }
    }

    public void openAgentDirectory(String agentId) {
        Path agentDir = AppConstants.Agents.agentDir(agentId);
        if (!Files.exists(agentDir)) {
            try {
                Files.createDirectories(agentDir);
            } catch (IOException e) {
                log.error("创建目录失败: {}", agentDir, e);
            }
        }
        try {
            Desktop.getDesktop().open(agentDir.toFile());
        } catch (IOException e) {
            log.error("打开目录失败: {}", agentDir, e);
        }
    }

    public List<String> getAgentFiles(String agentId) {
        Path agentDir = AppConstants.Agents.agentDir(agentId);
        List<String> files = new ArrayList<>();
        if (Files.exists(agentDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(agentDir)) {
                for (Path file : stream) {
                    if (Files.isRegularFile(file)) {
                        files.add(file.getFileName().toString());
                    }
                }
            } catch (IOException e) {
                log.error("获取文件列表失败: {}", agentDir, e);
            }
        }
        return files;
    }

    public boolean agentExists(String agentId) {
        return Files.exists(AppConstants.Agents.agentDir(agentId));
    }
}
