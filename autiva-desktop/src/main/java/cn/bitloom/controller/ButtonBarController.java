package cn.bitloom.controller;

import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.node.svg.SvgImageView;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * The type Button bar controller.
 *
 * @author bitloom
 */
@Slf4j
@Component
public class ButtonBarController implements Initializable {

    @FXML
    private Button sidebarButton;
    @FXML
    private HBox dynamicButtonContainer;
    @FXML
    private HBox rightButtonContainer;

    @Setter
    private IndexController indexController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.sidebarButton.setOnAction(event -> {
            if (this.indexController != null) {
                this.indexController.toggleSidebar();
            }
        });
    }

    /**
     * 根据配置更新按钮
     *
     * @param holder 按钮配置
     */
    public void updateButtons(ButtonBarHolder holder) {
        this.dynamicButtonContainer.getChildren().clear();
        this.rightButtonContainer.getChildren().clear();

        if (holder != null) {
            for (ButtonBarHolder.ButtonConfig buttonConfig : holder.getButtonConfigs()) {
                Button button = new Button();
                button.setId(buttonConfig.id());
                button.getStyleClass().add(buttonConfig.styleClass());
                button.setOnAction(buttonConfig.actionHandler());

                // 如果有图标路径，只显示图标；否则只显示文字
                if (buttonConfig.svgPath() != null && !buttonConfig.svgPath().isEmpty()) {
                    SvgImageView icon = new SvgImageView();
                    icon.setFitWidth(18);
                    icon.setFitHeight(18);
                    icon.setSvgPath(buttonConfig.svgPath());
                    button.setGraphic(icon);
                    button.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
                } else if (buttonConfig.text() != null && !buttonConfig.text().isEmpty()) {
                    button.setText(buttonConfig.text());
                    button.setContentDisplay(javafx.scene.control.ContentDisplay.TEXT_ONLY);
                }

                // 根据对齐方式放到不同容器
                if (buttonConfig.alignment() == ButtonBarHolder.Alignment.RIGHT) {
                    this.rightButtonContainer.getChildren().add(button);
                } else {
                    this.dynamicButtonContainer.getChildren().add(button);
                }
            }
        }
    }

}