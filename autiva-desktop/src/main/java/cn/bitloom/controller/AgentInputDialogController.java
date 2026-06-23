package cn.bitloom.controller;

import cn.bitloom.holder.DialogHolder;
import cn.bitloom.window.WindowManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.function.Consumer;
import java.util.ResourceBundle;

@Slf4j
@Component
public class AgentInputDialogController implements WindowManager.StageAware, DialogHolder, Initializable {

    @FXML
    private Label messageLabel;
    @FXML
    private TextField inputField;
    @FXML
    private Button cancelButton;
    @FXML
    private Button confirmButton;

    @Getter
    private Stage stage;

    private Consumer<String> onConfirm;

    @Override
    public double getWidth() {
        return 400;
    }

    @Override
    public double getHeight() {
        return 180;
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        cancelButton.setOnAction(e -> cancel());
        confirmButton.setOnAction(e -> confirm());
        inputField.setOnAction(e -> confirm());
    }

    public void init(String message, String defaultValue, Consumer<String> onConfirm) {
        messageLabel.setText(message);
        inputField.setText(defaultValue != null ? defaultValue : "");
        inputField.selectAll();
        this.onConfirm = onConfirm;
    }

    private void confirm() {
        String value = inputField.getText();
        if (value == null || value.isBlank()) return;

        String trimmed = value.trim();
        if (onConfirm != null) {
            onConfirm.accept(trimmed);
        }
        if (stage != null) {
            stage.close();
        }
    }

    private void cancel() {
        if (stage != null) {
            stage.close();
        }
    }
}
