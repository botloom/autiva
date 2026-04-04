package cn.bitloom.controller;

import cn.bitloom.agentic.mcp.McpManager;
import cn.bitloom.agentic.mcp.McpServer;
import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.window.WindowManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
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

    private final McpManager mcpManager;
    private final WindowManager windowManager;

    private final ObservableList<McpServer> serverList = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadServers();
    }

    private void loadServers() {
        mcpListContainer.getChildren().clear();
        serverList.clear();
        List<McpServer> servers = mcpManager.getMcpServers().values().stream().toList();
        serverList.addAll(servers);

        for (McpServer server : serverList) {
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
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.setSpacing(12);

        Label nameLabel = new Label(server.getName());
        nameLabel.getStyleClass().add("mcp-page__card-title");

        Label typeLabel = new Label(server.getTransportType() != null ? server.getTransportType().name() : "N/A");
        typeLabel.getStyleClass().add("mcp-page__card-type");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Button editButton = new Button("编辑");
        editButton.getStyleClass().add("mcp-page__card-btn");
        editButton.setOnAction(e -> openEditor(server));

        Button deleteButton = new Button("删除");
        deleteButton.getStyleClass().add("mcp-page__card-btn");
        deleteButton.setOnAction(e -> deleteServer(server));

        header.getChildren().addAll(nameLabel, typeLabel, spacer, editButton, deleteButton);

        String description = buildServerDescription(server);
        Label descLabel = new Label(description);
        descLabel.getStyleClass().add("mcp-page__card-description");

        card.getChildren().addAll(header, descLabel);

        card.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                openEditor(server);
            }
        });
        card.setCursor(javafx.scene.Cursor.HAND);

        return card;
    }

    private String buildServerDescription(McpServer server) {
        StringBuilder sb = new StringBuilder();
        if (server.getTransportType() == null) {
            return "";
        }
        switch (server.getTransportType()) {
            case STDIO -> {
                sb.append("命令: ").append(server.getCommand());
                if (server.getArgs() != null && !server.getArgs().isEmpty()) {
                    sb.append(" ").append(String.join(" ", server.getArgs()));
                }
            }
            case SSE -> {
                sb.append("URL: ").append(server.getUrl());
                if (server.getSseEndpoint() != null) {
                    sb.append(" | SSE Endpoint: ").append(server.getSseEndpoint());
                }
            }
            case STREAMABLE_HTTP -> {
                sb.append("URL: ").append(server.getUrl());
                if (server.getEndpoint() != null) {
                    sb.append(" | Endpoint: ").append(server.getEndpoint());
                }
            }
        }
        return sb.toString();
    }

    private void openEditor(McpServer server) {
        try {
            WindowManager.WindowConfig<McpEditorDialogController> config = windowManager.<McpEditorDialogController>configBuilder()
                .fxmlPath("cn/bitloom/view/McpEditorDialog.fxml")
                .owner(mcpPage.getScene().getWindow())
                .resizable(true)
                .controllerInitializer(controller -> {
                    controller.initData(server);
                    controller.setOnSaveCallback(savedServer -> {
                        if (server == null) {
                            mcpManager.addServer(savedServer);
                        } else {
                            mcpManager.updateServer(savedServer.getName(), savedServer);
                        }
                        loadServers();
                    });
                })
                .build();
            
            windowManager.showDialog(config);
        } catch (Exception e) {
            log.error("Failed to open MCP editor", e);
            showAlert("打开编辑器失败: " + e.getMessage());
        }
    }

    private void deleteServer(McpServer server) {
        try {
            mcpManager.deleteServer(server.getName());
            loadServers();
        } catch (Exception e) {
            showAlert("删除失败: " + e.getMessage());
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @Override
    public void show() {
        this.mcpPage.setVisible(true);
        this.mcpPage.setManaged(true);
        loadServers();
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
                        event -> openEditor(null)
                )
        );
    }
}