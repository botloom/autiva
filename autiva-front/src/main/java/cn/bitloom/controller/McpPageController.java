package cn.bitloom.controller;

import cn.bitloom.agentic.mcp.McpServer;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.util.AlertUtil;
import cn.bitloom.vm.McpPageViewModel;
import cn.bitloom.window.WindowManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
public class McpPageController implements Initializable, ButtonBarHolder, PageHolder {

    @FXML
    private VBox mcpPage;
    @FXML
    private VBox mcpListContainer;

    @Getter
    @Setter
    private IndexController indexController;

    private final McpPageViewModel viewModel;
    private final WindowManager windowManager;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        renderServers();
    }

    private void renderServers() {
        viewModel.loadServers();
        mcpListContainer.getChildren().clear();
        List<McpServer> servers = viewModel.getServers();

        for (McpServer server : servers) {
            VBox card = createServerCard(server);
            mcpListContainer.getChildren().add(card);
        }
    }

    private VBox createServerCard(McpServer server) {
        VBox card = new VBox();
        card.getStyleClass().add("mcp-page__card");
        card.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(card, new Insets(0, 0, 16, 0));

        HBox header = new HBox();
        header.getStyleClass().add("mcp-page__card-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(12);

        Label nameLabel = new Label(server.getName());
        nameLabel.getStyleClass().add("mcp-page__card-title");

        Label typeLabel = new Label(server.getTransportType() != null ? server.getTransportType().name() : "N/A");
        typeLabel.getStyleClass().add("mcp-page__card-type");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Button editButton = new Button("编辑");
        editButton.getStyleClass().add("mcp-page__card-btn");
        editButton.setOnAction(e -> openEditor());

        Button deleteButton = new Button("删除");
        deleteButton.getStyleClass().add("mcp-page__card-btn");
        deleteButton.setOnAction(e -> {
            boolean confirmed = AlertUtil.showConfirm("确认删除",
                    "确定要删除 MCP 服务器 \"" + server.getName() + "\" 吗？",
                    mcpPage.getScene().getWindow());
            if (confirmed) {
                try {
                    viewModel.deleteServer(server.getName());
                    renderServers();
                } catch (Exception ex) {
                    AlertUtil.showError("删除失败: " + ex.getMessage());
                }
            }
        });

        header.getChildren().addAll(nameLabel, typeLabel, spacer, editButton, deleteButton);

        String description = viewModel.buildServerDescription(server);
        Label descLabel = new Label(description);
        descLabel.getStyleClass().add("mcp-page__card-description");

        card.getChildren().addAll(header, descLabel);

        card.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                openEditor();
            }
        });
        card.setCursor(javafx.scene.Cursor.HAND);

        return card;
    }

    private void openEditor() {
        try {
            Path mcpDir = AppConstants.Base.MCP_DIR;
            windowManager.<FileEditorController>showDialog(
                "cn/bitloom/view/FileEditorDialog.fxml",
                mcpPage.getScene().getWindow(),
                controller -> controller.initRootPath(mcpDir)
            );
        } catch (Exception e) {
            log.error("Failed to open MCP editor", e);
            AlertUtil.showInfo("打开编辑器失败: " + e.getMessage());
        }
    }

    @Override
    public void show() {
        this.mcpPage.setVisible(true);
        this.mcpPage.setManaged(true);
        renderServers();
    }

    @Override
    public void hide() {
        this.mcpPage.setVisible(false);
        this.mcpPage.setManaged(false);
    }

    @Override
    public List<ButtonBarHolder.ButtonConfig> getButtonConfigs() {
        return List.of(
                new ButtonBarHolder.ButtonConfig(
                        "addMcpButton",
                        "添加",
                        "dynamic-btn",
                        event -> openEditor()
                )
        );
    }
}
