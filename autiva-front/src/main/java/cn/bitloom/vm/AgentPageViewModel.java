package cn.bitloom.vm;

import cn.bitloom.agentic.agent.AgentManager;
import cn.bitloom.util.ExecutorManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentPageViewModel {

    private final AgentManager agentManager;

    @Getter
    private final ObservableList<AgentManager.AgentFolder> agents = FXCollections.observableArrayList();

    public void loadAgents() {
        agents.setAll(agentManager.loadAgentFolders());
    }

    public void loadAgentsAsync(Runnable onLoaded) {
        Task<List<AgentManager.AgentFolder>> task = new Task<>() {
            @Override
            protected List<AgentManager.AgentFolder> call() {
                return agentManager.loadAgentFolders();
            }
        };
        task.setOnSucceeded(e -> {
            agents.setAll(task.getValue());
            if (onLoaded != null) onLoaded.run();
        });
        task.setOnFailed(e -> log.error("加载智能体列表失败", task.getException()));
        ExecutorManager.getPlatformTaskExecutor().execute(task);
    }

    public String readFileContent(AgentManager.AgentFile file) throws IOException {
        return Files.readString(file.getPath());
    }

    public void saveFile(AgentManager.AgentFile file, String content) throws IOException {
        Files.writeString(file.getPath(), content);
    }
}
