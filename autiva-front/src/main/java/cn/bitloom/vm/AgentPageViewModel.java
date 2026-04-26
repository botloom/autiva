package cn.bitloom.vm;

import cn.bitloom.agentic.agent.AgentManager;
import cn.bitloom.store.Store;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentPageViewModel {

    private final AgentManager agentManager;

    @Getter
    private final ObservableList<AgentManager.AgentFolder> mainAgents = FXCollections.observableArrayList();

    @Getter
    private final ObservableList<AgentManager.SubagentFolder> subagents = FXCollections.observableArrayList();

    public void loadAgents() {
        mainAgents.setAll(agentManager.loadAgentFolders());
        subagents.setAll(agentManager.loadSubagentFolders());
    }

    public String readFileContent(AgentManager.AgentFile file) throws IOException {
        return Files.readString(file.getPath());
    }

    public String readSubagentContent(AgentManager.SubagentFolder subagent) throws IOException {
        return Files.readString(subagent.path());
    }

    public void saveFile(AgentManager.AgentFile file, String content) throws IOException {
        Files.writeString(file.getPath(), content);
    }

    public void saveSubagentConfig(String name, String content) {
        agentManager.saveSubagentConfig(name, content);
    }

    public void deleteSubagent(String name) {
        agentManager.deleteSubagentConfig(name);
        Platform.runLater(() -> Store.statusText.set("子智能体已删除: " + name));
    }
}
