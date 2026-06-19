package cn.bitloom.controller;

import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.store.Store;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * The type Button bar controller.
 *
 * @author bitloom
 */
@Component
public class ButtonBarController implements Initializable {

    @FXML
    private Button sidebarButton;
    @FXML
    private HBox dynamicButtonContainer;
    @FXML
    private Label statusBarLabel;

    @Setter
    private IndexController indexController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.statusBarLabel.textProperty().bind(Store.statusText);
        
        this.statusBarLabel.textProperty().addListener((observable, oldValue, newValue) -> {
            this.playBounceAnimation();
        });
        
        this.sidebarButton.setOnAction(event -> {
            if (this.indexController != null) {
                this.indexController.toggleSidebar();
            }
        });
    }

    private void playBounceAnimation() {
        ScaleTransition scaleUp = new ScaleTransition(Duration.millis(150), this.statusBarLabel);
        scaleUp.setToX(1.15);
        scaleUp.setToY(1.15);
        scaleUp.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        
        Timeline colorTransition = new Timeline(
            new KeyFrame(Duration.millis(150), 
                new KeyValue(this.statusBarLabel.textFillProperty(), Color.web("#0071e3"), javafx.animation.Interpolator.EASE_OUT)
            )
        );
        
        ScaleTransition scaleDown = new ScaleTransition(Duration.millis(150), this.statusBarLabel);
        scaleDown.setToX(1.0);
        scaleDown.setToY(1.0);
        scaleDown.setInterpolator(javafx.animation.Interpolator.EASE_IN);
        
        Timeline colorTransitionBack = new Timeline(
            new KeyFrame(Duration.millis(150), 
                new KeyValue(this.statusBarLabel.textFillProperty(), Color.web("#86868b"), javafx.animation.Interpolator.EASE_IN)
            )
        );
        
        scaleUp.setOnFinished(event -> {
            scaleDown.play();
            colorTransitionBack.play();
        });
        scaleUp.play();
        colorTransition.play();
    }

    /**
     * 根据配置更新按钮
     *
     * @param holder 按钮配置
     */
    public void updateButtons(ButtonBarHolder holder) {
        this.dynamicButtonContainer.getChildren().clear();

        if (holder != null) {
            for (ButtonBarHolder.ButtonConfig buttonConfig : holder.getButtonConfigs()) {
                Button button = new Button(buttonConfig.text());
                button.setId(buttonConfig.id());
                button.getStyleClass().add(buttonConfig.styleClass());
                button.setOnAction(buttonConfig.actionHandler());
                this.dynamicButtonContainer.getChildren().add(button);
            }
        }
    }


}

