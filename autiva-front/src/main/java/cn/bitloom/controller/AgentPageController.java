package cn.bitloom.controller;

import cn.bitloom.agentic.agent.AgentManager;
import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.vm.AgentPageViewModel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentPageController implements Initializable, ButtonBarHolder, PageHolder {

    @FXML
    private VBox agentPage;
    @FXML
    private VBox agentListContainer;

    @Getter
    @Setter
    private IndexController indexController;

    private final AgentPageViewModel viewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        renderAgents();
    }

    private void renderAgents() {
        agentListContainer.getChildren().clear();
        viewModel.loadAgentsAsync(() -> {
            VBox cardsContainer = new VBox();
            cardsContainer.getStyleClass().add("agent-page__cards-container");
            cardsContainer.setSpacing(16);

            List<AgentManager.AgentFolder> agents = viewModel.getAgents();
            if (agents.isEmpty()) {
                Label emptyLabel = new Label("暂无智能体配置");
                emptyLabel.getStyleClass().add("agent-page__empty-text");
                cardsContainer.getChildren().add(emptyLabel);
            } else {
                for (AgentManager.AgentFolder agent : agents) {
                    TitledPane card = createAgentCard(agent);
                    cardsContainer.getChildren().add(card);
                }
            }

            agentListContainer.getChildren().add(cardsContainer);
        });
    }

    private TitledPane createAgentCard(AgentManager.AgentFolder agent) {
        VBox content = new VBox();
        content.getStyleClass().add("agent-page__agent-card-content");
        content.setSpacing(16);

        VBox workspaceSection = createWorkspaceSection(agent);
        content.getChildren().add(workspaceSection);

        TitledPane titledPane = new TitledPane();
        titledPane.setText(agent.getName());
        titledPane.setContent(content);
        titledPane.getStyleClass().add("agent-page__agent-card");
        titledPane.setExpanded(false);
        titledPane.setAnimated(true);

        return titledPane;
    }

    private VBox createWorkspaceSection(AgentManager.AgentFolder agent) {
        VBox section = new VBox();
        section.getStyleClass().add("agent-page__section");
        section.setSpacing(12);

        if (agent.getFiles().isEmpty()) {
            Label emptyLabel = new Label("暂无配置文件");
            emptyLabel.getStyleClass().add("agent-page__empty-text");
            section.getChildren().add(emptyLabel);
            return section;
        }

        VBox fileList = new VBox();
        fileList.getStyleClass().add("agent-page__file-list");
        fileList.setSpacing(8);

        for (AgentManager.AgentFile file : agent.getFiles()) {
            VBox fileCard = createFileCard(file);
            fileList.getChildren().add(fileCard);
        }

        section.getChildren().add(fileList);
        return section;
    }

    private VBox createFileCard(AgentManager.AgentFile file) {
        VBox card = new VBox();
        card.getStyleClass().add("agent-page__file-card");
        card.setAlignment(Pos.TOP_LEFT);

        HBox header = new HBox();
        header.getStyleClass().add("agent-page__file-card-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(8);

        Label iconLabel = new Label("\uD83D\uDCC4");
        iconLabel.getStyleClass().add("agent-page__file-icon");

        Label nameLabel = new Label(file.getDisplayName());
        nameLabel.getStyleClass().add("agent-page__file-name");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        header.getChildren().addAll(iconLabel, nameLabel, spacer);
        card.getChildren().add(header);

        card.setCursor(javafx.scene.Cursor.HAND);

        return card;
    }

    @Override
    public void show() {
        this.agentPage.setVisible(true);
        this.agentPage.setManaged(true);
        renderAgents();
    }

    @Override
    public void hide() {
        this.agentPage.setVisible(false);
        this.agentPage.setManaged(false);
    }

    @Override
    public List<ButtonBarHolder.ButtonConfig> getButtonConfigs() {
        return List.of(
            new ButtonBarHolder.ButtonConfig(
                "refreshAgentButton",
                "刷新",
                "dynamic-btn",
                event -> renderAgents()
            )
        );
    }
}
