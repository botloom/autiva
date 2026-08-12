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
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * The type Button bar controller.
 *
 * @author bitloom
 */
@Slf4j
@Component
public class ButtonBarController implements Initializable {

    /** 需聊天后才显示的右上角视图按钮 id */
    private static final String[] VIEW_BUTTON_IDS = {
            "terminalButton", "toolCallsButton", "todoButton"
    };

    @FXML
    private Button sidebarButton;
    @FXML
    private HBox dynamicButtonContainer;
    @FXML
    private HBox rightButtonContainer;

    /** 按钮 id → Button 引用（updateButtons 时重建） */
    private final Map<String, Button> buttonMap = new HashMap<>();

    /** 视图按钮当前是否应显示（默认隐藏，仅在聊天后由首页控制器置为 true） */
    private boolean viewButtonsVisible = false;

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
        this.buttonMap.clear();

        if (holder != null) {
            for (ButtonBarHolder.ButtonConfig buttonConfig : holder.getButtonConfigs()) {
                Button button = new Button();
                button.setId(buttonConfig.id());
                button.getStyleClass().add(buttonConfig.styleClass());
                button.setOnAction(buttonConfig.actionHandler());
                this.buttonMap.put(buttonConfig.id(), button);

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

        // 重建完成后按当前状态应用视图按钮可见性
        applyViewButtonVisibility();
    }

    /**
     * 控制右上角视图按钮（终端/工具/待办）的显示与隐藏。
     *
     * @param visible true 显示（仅当有聊天消息时）；false 隐藏
     */
    public void setViewButtonsVisible(boolean visible) {
        this.viewButtonsVisible = visible;
        applyViewButtonVisibility();
    }

    private void applyViewButtonVisibility() {
        for (String id : VIEW_BUTTON_IDS) {
            Button button = this.buttonMap.get(id);
            if (button != null) {
                button.setVisible(this.viewButtonsVisible);
                button.setManaged(this.viewButtonsVisible);
            }
        }
    }
}