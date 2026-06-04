package cn.bitloom.vm;

import cn.bitloom.agentic.agent.AgentManager;
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
    private final ObservableList<AgentManager.AgentFolder> agents = FXCollections.observableArrayList();

    public void loadAgents() {
        agents.setAll(agentManager.loadAgentFolders());
    }

    public String readFileContent(AgentManager.AgentFile file) throws IOException {
        return Files.readString(file.getPath());
    }

    public void saveFile(AgentManager.AgentFile file, String content) throws IOException {
        Files.writeString(file.getPath(), content);
    }
}
