package cn.bitloom.controller;

import cn.bitloom.agentic.mcp.McpServer;
import cn.bitloom.agentic.mcp.McpTransportTypeEnum;
import cn.bitloom.window.WindowManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
public class McpEditorDialogController implements WindowManager.StageAware {

    @FXML
    private TextField nameField;
    @FXML
    private ComboBox<McpTransportTypeEnum> connectionTypeCombo;

    @FXML
    private VBox stdioSection;
    @FXML
    private TextField stdioCommandField;
    @FXML
    private TextField stdioArgsField;
    @FXML
    private TextField stdioEnvField;

    @FXML
    private VBox sseSection;
    @FXML
    private TextField sseUrlField;
    @FXML
    private TextField sseEndpointField;

    @FXML
    private VBox httpSection;
    @FXML
    private TextField httpUrlField;
    @FXML
    private TextField httpEndpointField;

    @FXML
    private Label statusLabel;

    @Getter
    @Setter
    private Stage stage;

    @Getter
    @Setter
    private McpServer server;

    @Getter
    @Setter
    private Consumer<McpServer> onSaveCallback;

    @FXML
    public void initialize() {
        connectionTypeCombo.setItems(FXCollections.observableArrayList(McpTransportTypeEnum.values()));
        connectionTypeCombo.setOnAction(e -> updateSectionVisibility());
    }

    public void initData(McpServer server) {
        this.server = server;
        if (server != null) {
            nameField.setText(server.getName());
            connectionTypeCombo.setValue(server.getTransportType());

            stdioCommandField.setText(server.getCommand());
            stdioArgsField.setText(server.getArgs() != null ? String.join(" ", server.getArgs()) : "");
            if (server.getEnv() != null && !server.getEnv().isEmpty()) {
                stdioEnvField.setText(server.getEnv().entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse(""));
            } else {
                stdioEnvField.clear();
            }
            sseUrlField.setText(server.getUrl());
            sseEndpointField.setText(server.getSseEndpoint());
            httpUrlField.setText(server.getUrl());
            httpEndpointField.setText(server.getEndpoint());
        } else {
            nameField.clear();
            connectionTypeCombo.setValue(null);
            clearAllFields();
        }
        updateSectionVisibility();
        statusLabel.setText("就绪");
    }

    private void clearAllFields() {
        stdioCommandField.clear();
        stdioArgsField.clear();
        stdioEnvField.clear();
        sseUrlField.clear();
        sseEndpointField.clear();
        httpUrlField.clear();
        httpEndpointField.clear();
    }

    private void updateSectionVisibility() {
        McpTransportTypeEnum type = connectionTypeCombo.getValue();
        stdioSection.setVisible(type == McpTransportTypeEnum.STDIO);
        stdioSection.setManaged(type == McpTransportTypeEnum.STDIO);
        sseSection.setVisible(type == McpTransportTypeEnum.SSE);
        sseSection.setManaged(type == McpTransportTypeEnum.SSE);
        httpSection.setVisible(type == McpTransportTypeEnum.STREAMABLE_HTTP);
        httpSection.setManaged(type == McpTransportTypeEnum.STREAMABLE_HTTP);
    }

    @FXML
    private void saveMcp() {
        if (nameField.getText() == null || nameField.getText().trim().isEmpty()) {
            statusLabel.setText("错误: 请输入服务器名称");
            return;
        }

        McpTransportTypeEnum transportType = connectionTypeCombo.getValue();
        if (transportType == null) {
            statusLabel.setText("错误: 请选择连接类型");
            return;
        }

        McpServer savedServer;
        if (server != null) {
            savedServer = server;
        } else {
            savedServer = McpServer.builder().build();
        }

        savedServer.setName(nameField.getText().trim());
        savedServer.setTransportType(transportType);

        switch (transportType) {
            case STDIO -> {
                savedServer.setCommand(stdioCommandField.getText());
                savedServer.setArgs(stdioArgsField.getText() != null && !stdioArgsField.getText().isEmpty()
                        ? Arrays.asList(stdioArgsField.getText().split(" "))
                        : null);
                if (stdioEnvField.getText() != null && !stdioEnvField.getText().trim().isEmpty()) {
                    Map<String, String> envMap = new HashMap<>();
                    for (String pair : stdioEnvField.getText().split(",")) {
                        String[] kv = pair.split("=", 2);
                        if (kv.length == 2) {
                            envMap.put(kv[0].trim(), kv[1].trim());
                        }
                    }
                    savedServer.setEnv(envMap.isEmpty() ? null : envMap);
                } else {
                    savedServer.setEnv(null);
                }
            }
            case SSE -> {
                savedServer.setUrl(sseUrlField.getText());
                savedServer.setSseEndpoint(sseEndpointField.getText());
            }
            case STREAMABLE_HTTP -> {
                savedServer.setUrl(httpUrlField.getText());
                savedServer.setEndpoint(httpEndpointField.getText());
            }
        }

        if (onSaveCallback != null) {
            onSaveCallback.accept(savedServer);
        }
        stage.close();
    }

    @FXML
    private void cancel() {
        stage.close();
    }
}