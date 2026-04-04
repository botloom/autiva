package cn.bitloom.controller;

import cn.bitloom.agentic.agent.AgentManager;
import cn.bitloom.agentic.tool.ToolManager;
import cn.bitloom.config.ConfigManager;
import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.store.Store;
import cn.bitloom.window.WindowManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;

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
    private final ConfigManager configManager;
    private final ToolManager toolManager;
    private final AgentManager agentManager;
    
    private final Map<String, List<CheckBox>> agentToolCheckBoxesMap = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadAgents();
    }
    
    public void saveAllToolConfigs() {
        for (Map.Entry<String, List<CheckBox>> entry : agentToolCheckBoxesMap.entrySet()) {
            String agentName = entry.getKey();
            List<CheckBox> checkBoxes = entry.getValue();
            
            List<String> selectedTools = checkBoxes.stream()
                    .filter(CheckBox::isSelected)
                    .map(cb -> (String) cb.getUserData())
                    .toList();
            
            configManager.setAgentTools(agentName, selectedTools);
        }
        configManager.save();
        Store.statusText.set("所有智能体工具配置已保存");
    }

    private void loadAgents() {
        agentListContainer.getChildren().clear();
        agentToolCheckBoxesMap.clear();
        
        List<AgentManager.AgentFolder> agents = agentManager.loadAgentFolders();
        
        if (agents.isEmpty()) {
            Label emptyLabel = new Label("暂无智能体配置");
            emptyLabel.getStyleClass().add("agent-page__empty");
            VBox.setMargin(emptyLabel, new Insets(40, 0, 0, 0));
            agentListContainer.getChildren().add(emptyLabel);
            return;
        }
        
        VBox cardsContainer = new VBox();
        cardsContainer.getStyleClass().add("agent-page__cards-container");
        cardsContainer.setSpacing(16);
        
        for (AgentManager.AgentFolder agent : agents) {
            VBox card = createAgentCard(agent);
            cardsContainer.getChildren().add(card);
        }
        
        agentListContainer.getChildren().add(cardsContainer);
    }

    private VBox createAgentCard(AgentManager.AgentFolder agent) {
        VBox card = new VBox();
        card.getStyleClass().add("agent-page__agent-card");
        
        HBox header = new HBox();
        header.getStyleClass().add("agent-page__agent-header");
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label nameLabel = new Label(agent.getName());
        nameLabel.getStyleClass().add("agent-page__agent-title");
        header.getChildren().add(nameLabel);
        card.getChildren().add(header);
        
        VBox workspaceSection = createWorkspaceSection(agent);
        card.getChildren().add(workspaceSection);
        
        VBox toolSection = createToolSection(agent);
        card.getChildren().add(toolSection);
        
        return card;
    }
    
    private VBox createWorkspaceSection(AgentManager.AgentFolder agent) {
        VBox section = new VBox();
        section.getStyleClass().add("agent-page__section");
        section.setSpacing(12);
        
        Label sectionLabel = new Label("工作区配置");
        sectionLabel.getStyleClass().add("agent-page__section-title");
        section.getChildren().add(sectionLabel);
        
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
    
    private VBox createToolSection(AgentManager.AgentFolder agent) {
        VBox section = new VBox();
        section.getStyleClass().add("agent-page__section");
        section.setSpacing(12);
        
        Label sectionLabel = new Label("工具配置");
        sectionLabel.getStyleClass().add("agent-page__section-title");
        section.getChildren().add(sectionLabel);
        
        List<ToolDefinition> allTools = toolManager.getToolDefinitions();
        if (allTools.isEmpty()) {
            Label emptyLabel = new Label("暂无可用工具");
            emptyLabel.getStyleClass().add("agent-page__empty-text");
            section.getChildren().add(emptyLabel);
            return section;
        }
        
        VBox toolList = new VBox();
        toolList.getStyleClass().add("agent-page__tool-list");
        toolList.setSpacing(8);
        
        List<String> enabledTools = configManager.getAgentToolList(agent.getName());
        List<CheckBox> agentCheckBoxes = new ArrayList<>();
        
        for (ToolDefinition tool : allTools) {
            VBox toolCard = createToolCard(tool, enabledTools, agentCheckBoxes);
            toolList.getChildren().add(toolCard);
        }
        
        agentToolCheckBoxesMap.put(agent.getName(), agentCheckBoxes);
        section.getChildren().add(toolList);
        
        return section;
    }
    
    private VBox createToolCard(ToolDefinition tool, List<String> enabledTools, List<CheckBox> checkBoxes) {
        VBox card = new VBox();
        card.getStyleClass().add("agent-page__tool-card");
        
        HBox header = new HBox();
        header.getStyleClass().add("agent-page__tool-card-header");
        header.setAlignment(Pos.TOP_LEFT);
        header.setSpacing(12);
        
        VBox infoBox = new VBox();
        infoBox.setSpacing(4);
        infoBox.setFillWidth(true);
        HBox.setHgrow(infoBox, javafx.scene.layout.Priority.ALWAYS);
        
        Label nameLabel = new Label(tool.name());
        nameLabel.getStyleClass().add("agent-page__tool-name");
        
        String description = tool.description();
        if (description != null && !description.isEmpty()) {
            Label descLabel = new Label(description);
            descLabel.getStyleClass().add("agent-page__tool-desc");
            descLabel.setWrapText(true);
            descLabel.setMaxWidth(Double.MAX_VALUE);
            infoBox.getChildren().addAll(nameLabel, descLabel);
        } else {
            infoBox.getChildren().add(nameLabel);
        }
        
        CheckBox toggleSwitch = new CheckBox();
        toggleSwitch.getStyleClass().add("agent-page__tool-switch");
        toggleSwitch.setSelected(enabledTools.contains(tool.name()));
        toggleSwitch.setUserData(tool.name());
        toggleSwitch.setMinWidth(51);
        toggleSwitch.setMinHeight(31);
        
        header.getChildren().addAll(infoBox, toggleSwitch);
        card.getChildren().add(header);
        
        checkBoxes.add(toggleSwitch);
        
        return card;
    }
    
    private String truncateText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private void openEditor(AgentManager.AgentFile file, AgentManager.AgentFolder agent) {
        try {
            String content = Files.readString(file.getPath());
            
            WindowManager.WindowConfig<MdEditorController> config = windowManager.<MdEditorController>configBuilder()
                .fxmlPath("cn/bitloom/components/MdEditor.fxml")
                .title(agent.getName() + " - " + file.getDisplayName())
                .owner(agentPage.getScene().getWindow())
                .controllerInitializer(controller -> {
                    controller.setTitle(file.getDisplayName());
                    controller.setContent(content);
                    controller.setOnSaveCallback(data -> {
                        try {
                            Files.writeString(file.getPath(), data.content());
                            controller.setStatus("已保存: " + LocalDateTime.now().toString());
                            loadAgents();
                        } catch (IOException e) {
                            log.error("Failed to save file: {}", file.getPath(), e);
                            controller.setStatus("保存失败: " + e.getMessage());
                        }
                    });
                })
                .build();
            
            windowManager.showDialog(config);
        } catch (Exception e) {
            log.error("Failed to open agent editor", e);
            showAlert("打开编辑器失败: " + e.getMessage());
        }
    }

    private void showAlert(String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @Override
    public void show() {
        this.agentPage.setVisible(true);
        this.agentPage.setManaged(true);
        agentToolCheckBoxesMap.clear();
        loadAgents();
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
                event -> {
                    agentToolCheckBoxesMap.clear();
                    loadAgents();
                }
            ),
            new ButtonBarHolder.ButtonConfig(
                "saveToolConfigButton",
                "保存配置",
                "dynamic-btn",
                event -> saveAllToolConfigs()
            )
        );
    }
}
