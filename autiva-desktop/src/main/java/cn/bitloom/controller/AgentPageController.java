package cn.bitloom.controller;

import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.holder.DialogHolder;
import cn.bitloom.vm.AgentPageViewModel;
import cn.bitloom.window.WindowManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentPageController implements Initializable, DialogHolder {

    private final AgentPageViewModel viewModel;
    private final WindowManager windowManager;

    @FXML
    private VBox agentPage;
    @FXML
    private ListView<AgentDefinition> agentListView;

    @Override
    public double getWidth() {
        return 800;
    }

    @Override
    public double getHeight() {
        return 650;
    }

    @Override
    public boolean isResizable() {
        return true;
    }

    /** 弹窗每次打开时刷新数据（showDialog initializer 调用） */
    public void refresh() {
        renderAgents();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        agentListView.setFocusTraversable(false);
        agentListView.setItems(viewModel.getMainAgents());
        agentListView.setCellFactory(_ -> new AgentListCell());
        renderAgents();
    }

    private void renderAgents() {
        viewModel.loadAgentsAsync(null);
    }

    private VBox createAgentCard(AgentDefinition agent) {
        VBox card = new VBox();
        card.getStyleClass().add("agent-page__card");
        card.setMaxWidth(Double.MAX_VALUE);

        HBox header = new HBox();
        header.getStyleClass().add("agent-page__card-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(12);

        Label nameLabel = new Label(agent.name());
        nameLabel.getStyleClass().add("agent-page__card-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Button openDirBtn = new Button("打开目录");
        openDirBtn.getStyleClass().add("agent-page__card-btn");
        openDirBtn.setOnAction(e -> viewModel.openAgentDirectory(agent.name()));

        Button copyBtn = new Button("复制");
        copyBtn.getStyleClass().add("agent-page__card-btn");
        copyBtn.setOnAction(e -> copyAgent(agent.name()));

        Button deleteBtn = new Button("删除");
        deleteBtn.getStyleClass().add("agent-page__card-btn-danger");
        deleteBtn.setOnAction(e -> deleteAgent(agent.name()));

        header.getChildren().addAll(nameLabel, spacer, openDirBtn, copyBtn, deleteBtn);

        String description = agent.description();
        Label descLabel = new Label(description);
        descLabel.getStyleClass().add("agent-page__card-description");
        descLabel.setWrapText(true);

        Separator separator = new Separator();
        separator.getStyleClass().add("agent-page__card-separator");

        VBox fileList = new VBox();
        fileList.getStyleClass().add("agent-page__file-list");
        fileList.setSpacing(6);

        Map<String, String> configFiles = Map.of(
                "agent.md", "智能体定义",
                "config.json", "工具与子智能体配置"
        );

        for (Map.Entry<String, String> entry : configFiles.entrySet()) {
            String fileName = entry.getKey();
            String fileDesc = entry.getValue();

            HBox fileRow = new HBox();
            fileRow.getStyleClass().add("agent-page__file-row");
            fileRow.setAlignment(Pos.CENTER_LEFT);

            VBox fileInfo = new VBox();
            fileInfo.getStyleClass().add("agent-page__file-info");
            HBox.setHgrow(fileInfo, javafx.scene.layout.Priority.ALWAYS);

            Label fileNameLabel = new Label(fileName);
            fileNameLabel.getStyleClass().add("agent-page__file-name");

            Label fileDescLabel = new Label(fileDesc);
            fileDescLabel.getStyleClass().add("agent-page__file-desc");

            fileInfo.getChildren().addAll(fileNameLabel, fileDescLabel);

            Button editBtn = new Button("编辑");
            editBtn.getStyleClass().add("agent-page__file-btn");
            editBtn.setOnAction(e -> editFile(agent.name(), fileName));

            fileRow.getChildren().addAll(fileInfo, editBtn);
            fileList.getChildren().add(fileRow);
        }

        card.getChildren().addAll(header, descLabel, separator, fileList);
        return card;
    }

    @FXML
    private void onCreateAgent() {
        windowManager.showDialog("cn/bitloom/view/AgentInputDialog.fxml",
                agentPage.getScene().getWindow(),
                controller -> {
                    if (controller instanceof AgentInputDialogController inputController) {
                        inputController.init("输入智能体名称", "", agentId -> {
                            if (viewModel.agentExists(agentId)) {
                                showWarning("智能体 \"" + agentId + "\" 已存在");
                                return;
                            }
                            try {
                                viewModel.createAgent(agentId);
                                renderAgents();
                            } catch (Exception e) {
                                showError("创建智能体失败: " + e.getMessage());
                            }
                        });
                    }
                });
    }

    private void deleteAgent(String agentId) {
        windowManager.showDialog("cn/bitloom/view/AgentConfirmDialog.fxml",
                agentPage.getScene().getWindow(),
                controller -> {
                    if (controller instanceof AgentConfirmDialogController confirmController) {
                        confirmController.init("删除智能体",
                                "确定要删除智能体 \"" + agentId + "\" 吗？此操作不可撤销。",
                                confirmed -> {
                                    if (confirmed) {
                                        try {
                                            viewModel.deleteAgent(agentId);
                                            renderAgents();
                                        } catch (Exception e) {
                                            showError("删除智能体失败: " + e.getMessage());
                                        }
                                    }
                                });
                    }
                });
    }

    private void copyAgent(String sourceAgentId) {
        windowManager.showDialog("cn/bitloom/view/AgentInputDialog.fxml",
                agentPage.getScene().getWindow(),
                controller -> {
                    if (controller instanceof AgentInputDialogController inputController) {
                        inputController.init("输入新智能体名称", sourceAgentId + "-copy", targetId -> {
                            if (viewModel.agentExists(targetId)) {
                                showWarning("智能体 \"" + targetId + "\" 已存在");
                                return;
                            }
                            try {
                                viewModel.copyAgent(sourceAgentId, targetId);
                                renderAgents();
                            } catch (Exception e) {
                                showError("复制智能体失败: " + e.getMessage());
                            }
                        });
                    }
                });
    }

    private void editFile(String agentId, String fileName) {
        String content = viewModel.readFileContent(agentId, fileName);
        windowManager.showDialog("cn/bitloom/view/AgentConfigEditorDialog.fxml",
                agentPage.getScene().getWindow(),
                controller -> {
                    if (controller instanceof AgentConfigEditorDialogController editorController) {
                        editorController.init(agentId, fileName, content, viewModel);
                    }
                });
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("警告");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.initOwner(agentPage.getScene().getWindow());
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.initOwner(agentPage.getScene().getWindow());
        alert.showAndWait();
    }

    /**
     * 智能体列表 cell：渲染智能体卡片，约束宽度避免水平滚动条
     */
    private class AgentListCell extends ListCell<AgentDefinition> {
        @Override
        protected void updateItem(AgentDefinition agent, boolean empty) {
            super.updateItem(agent, empty);
            if (empty || agent == null) {
                setGraphic(null);
            } else {
                VBox card = createAgentCard(agent);
                // 减去 ListView 左右 padding(32+32) + 垂直滚动条(6)
                card.prefWidthProperty().bind(
                        getListView().widthProperty().subtract(70)
                );
                card.maxWidthProperty().bind(
                        getListView().widthProperty().subtract(70)
                );
                setGraphic(card);
            }
        }
    }
}
