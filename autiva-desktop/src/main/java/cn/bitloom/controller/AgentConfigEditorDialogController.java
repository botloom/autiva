package cn.bitloom.controller;

import cn.bitloom.holder.DialogHolder;
import cn.bitloom.vm.AgentPageViewModel;
import cn.bitloom.window.WindowManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.ResourceBundle;

@Slf4j
@Component
public class AgentConfigEditorDialogController implements WindowManager.StageAware, DialogHolder, Initializable {

    @FXML
    private TextArea editorArea;
    @FXML
    private Button cancelButton;
    @FXML
    private Button saveButton;

    @Getter
    private Stage stage;

    private String agentId;
    private String fileName;
    private AgentPageViewModel viewModel;

    @Override
    public double getWidth() {
        return 700;
    }

    @Override
    public double getHeight() {
        return 500;
    }

    @Override
    public boolean isResizable() {
        return true;
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
        stage.setMinWidth(500);
        stage.setMinHeight(350);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        cancelButton.setOnAction(e -> cancel());
        saveButton.setOnAction(e -> save());
    }

    public void init(String agentId, String fileName, String content, AgentPageViewModel viewModel) {
        this.agentId = agentId;
        this.fileName = fileName;
        this.viewModel = viewModel;

        editorArea.setText(content);
        editorArea.positionCaret(0);
    }

    private void save() {
        try {
            viewModel.saveFileContent(agentId, fileName, editorArea.getText());
            if (stage != null) {
                stage.close();
            }
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("错误");
            alert.setHeaderText(null);
            alert.setContentText("保存失败: " + e.getMessage());
            if (stage != null) alert.initOwner(stage.getOwner());
            alert.showAndWait();
        }
    }

    private void cancel() {
        if (stage != null) {
            stage.close();
        }
    }
}
