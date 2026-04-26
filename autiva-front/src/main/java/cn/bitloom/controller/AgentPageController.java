package cn.bitloom.controller;

import cn.bitloom.agentic.agent.AgentManager;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.util.AlertUtil;
import cn.bitloom.vm.AgentPageViewModel;
import cn.bitloom.window.WindowManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
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
import java.nio.file.Path;
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

    private final WindowManager windowManager;
    private final AgentPageViewModel viewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        renderAgents();
    }

    private void renderAgents() {
        viewModel.loadAgents();
        agentListContainer.getChildren().clear();

        VBox cardsContainer = new VBox();
        cardsContainer.getStyleClass().add("agent-page__cards-container");
        cardsContainer.setSpacing(16);

        Label mainAgentLabel = new Label("主智能体");
        mainAgentLabel.getStyleClass().add("agent-page__section-title");
        cardsContainer.getChildren().add(mainAgentLabel);

        List<AgentManager.AgentFolder> mainAgents = viewModel.getMainAgents();
        if (mainAgents.isEmpty()) {
            Label emptyLabel = new Label("暂无主智能体配置");
            emptyLabel.getStyleClass().add("agent-page__empty-text");
            VBox.setMargin(emptyLabel, new Insets(0, 0, 16, 0));
            cardsContainer.getChildren().add(emptyLabel);
        } else {
            for (AgentManager.AgentFolder agent : mainAgents) {
                TitledPane card = createMainAgentCard(agent);
                cardsContainer.getChildren().add(card);
            }
        }

        Label subagentLabel = new Label("子智能体");
        subagentLabel.getStyleClass().add("agent-page__section-title");
        VBox.setMargin(subagentLabel, new Insets(24, 0, 0, 0));
        cardsContainer.getChildren().add(subagentLabel);

        List<AgentManager.SubagentFolder> subagents = viewModel.getSubagents();
        if (subagents.isEmpty()) {
            Label emptyLabel = new Label("暂无子智能体配置");
            emptyLabel.getStyleClass().add("agent-page__empty-text");
            cardsContainer.getChildren().add(emptyLabel);
        } else {
            for (AgentManager.SubagentFolder subagent : subagents) {
                TitledPane card = createSubagentCard(subagent);
                cardsContainer.getChildren().add(card);
            }
        }

        agentListContainer.getChildren().add(cardsContainer);
    }

    private TitledPane createMainAgentCard(AgentManager.AgentFolder agent) {
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

    private TitledPane createSubagentCard(AgentManager.SubagentFolder subagent) {
        VBox content = new VBox();
        content.getStyleClass().add("agent-page__agent-card-content");
        content.setSpacing(16);

        HBox fileRow = new HBox();
        fileRow.getStyleClass().add("agent-page__file-card");
        fileRow.setAlignment(Pos.CENTER_LEFT);
        fileRow.setSpacing(8);

        Label iconLabel = new Label("📄");
        iconLabel.getStyleClass().add("agent-page__file-icon");

        Label nameLabel = new Label(subagent.path().getFileName().toString());
        nameLabel.getStyleClass().add("agent-page__file-name");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Button editBtn = new Button("编辑");
        editBtn.getStyleClass().add("agent-page__file-btn");
        editBtn.setOnAction(e -> openSubagentEditor(subagent));

        Button deleteBtn = new Button("删除");
        deleteBtn.getStyleClass().add("agent-page__file-btn");
        deleteBtn.setOnAction(e -> deleteSubagent(subagent));

        fileRow.getChildren().addAll(iconLabel, nameLabel, spacer, editBtn, deleteBtn);
        content.getChildren().add(fileRow);

        TitledPane titledPane = new TitledPane();
        titledPane.setText(subagent.name());
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
            VBox fileCard = createFileCard(file, agent);
            fileList.getChildren().add(fileCard);
        }

        section.getChildren().add(fileList);
        return section;
    }

    private VBox createFileCard(AgentManager.AgentFile file, AgentManager.AgentFolder agent) {
        VBox card = new VBox();
        card.getStyleClass().add("agent-page__file-card");
        card.setAlignment(Pos.TOP_LEFT);

        HBox header = new HBox();
        header.getStyleClass().add("agent-page__file-card-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(8);

        Label iconLabel = new Label("📄");
        iconLabel.getStyleClass().add("agent-page__file-icon");

        Label nameLabel = new Label(file.getDisplayName());
        nameLabel.getStyleClass().add("agent-page__file-name");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Button editBtn = new Button("编辑");
        editBtn.getStyleClass().add("agent-page__file-btn");
        editBtn.setOnAction(e -> openEditor(file, agent));

        header.getChildren().addAll(iconLabel, nameLabel, spacer, editBtn);
        card.getChildren().add(header);

        card.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                openEditor(file, agent);
            }
        });
        card.setCursor(javafx.scene.Cursor.HAND);

        return card;
    }

    private void openEditor(AgentManager.AgentFile file, AgentManager.AgentFolder agent) {
        try {
            Path agentPath = agent.getPath();
            windowManager.<FileEditorController>showDialog(
                "cn/bitloom/view/FileEditorDialog.fxml",
                agentPage.getScene().getWindow(),
                controller -> controller.initRootPath(agentPath)
            );
        } catch (Exception e) {
            log.error("Failed to open agent editor", e);
            AlertUtil.showInfo("打开编辑器失败: " + e.getMessage());
        }
    }

    private void openSubagentEditor(AgentManager.SubagentFolder subagent) {
        try {
            Path subagentDir = AppConstants.Base.SUBAGENT_DIR;
            windowManager.<FileEditorController>showDialog(
                "cn/bitloom/view/FileEditorDialog.fxml",
                agentPage.getScene().getWindow(),
                controller -> controller.initRootPath(subagentDir)
            );
        } catch (Exception e) {
            log.error("Failed to open subagent editor", e);
            AlertUtil.showInfo("打开编辑器失败: " + e.getMessage());
        }
    }

    private void deleteSubagent(AgentManager.SubagentFolder subagent) {
        boolean confirmed = AlertUtil.showConfirm("确认删除",
                "确定要删除子智能体 \"" + subagent.name() + "\" 吗？此操作不可撤销。",
                agentPage.getScene().getWindow());
        if (confirmed) {
            try {
                viewModel.deleteSubagent(subagent.name());
                renderAgents();
            } catch (Exception e) {
                log.error("Failed to delete subagent: {}", subagent.name(), e);
                AlertUtil.showError("删除失败: " + e.getMessage());
            }
        }
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
