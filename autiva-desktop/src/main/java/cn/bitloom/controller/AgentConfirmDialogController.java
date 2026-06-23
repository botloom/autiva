package cn.bitloom.controller;

import cn.bitloom.holder.DialogHolder;
import cn.bitloom.window.WindowManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.function.Consumer;
import java.util.ResourceBundle;

@Slf4j
@Component
public class AgentConfirmDialogController implements WindowManager.StageAware, DialogHolder, Initializable {

    @FXML
    private Label headerLabel;
    @FXML
    private Label messageLabel;
    @FXML
    private Button cancelButton;
    @FXML
    private Button confirmButton;

    @Getter
    private Stage stage;

    private Consumer<Boolean> onResult;

    @Override
    public double getWidth() {
        return 380;
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
    }

    public void init(String title, String message, Consumer<Boolean> onResult) {
        headerLabel.setText(title);
        messageLabel.setText(message);
        this.onResult = onResult;

        if (stage != null) {
            stage.setTitle(title);
        }
    }

    private void confirm() {
        if (onResult != null) {
            onResult.accept(true);
        }
        if (stage != null) {
            stage.close();
        }
    }

    private void cancel() {
        if (onResult != null) {
            onResult.accept(false);
        }
        if (stage != null) {
            stage.close();
        }
    }
}
