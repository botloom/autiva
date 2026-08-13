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
                // 隐藏时不再保持激活色，避免残留
                if (!this.viewButtonsVisible) {
                    button.getStyleClass().remove(ACTIVE_STYLE_CLASS);
                }
            }
        }
    }

    /** 激活态样式类：视图打开时按钮呈蓝色高亮 */
    private static final String ACTIVE_STYLE_CLASS = "button-bar__icon-btn--active";

    /**
     * 设置视图按钮的激活状态：true 时按钮呈蓝色高亮，false 时恢复默认。
     *
     * @param buttonId 按钮 id（如 terminalButton / toolCallsButton / todoButton）
     * @param active   true 激活（蓝色高亮）
     */
    public void setViewActive(String buttonId, boolean active) {
        Button button = this.buttonMap.get(buttonId);
        if (button == null) return;
        if (active) {
            if (!button.getStyleClass().contains(ACTIVE_STYLE_CLASS)) {
                button.getStyleClass().add(ACTIVE_STYLE_CLASS);
            }
        } else {
            button.getStyleClass().remove(ACTIVE_STYLE_CLASS);
        }
    }

    /**
     * 设置左侧侧边栏抽屉按钮的激活状态。
     * 侧边栏按钮不启用蓝色高亮激活态（与打开/关闭无关，始终显示默认灰色样式），
     * 此处仅确保移除共享的激活样式类，避免误加残留。
     *
     * @param active 保留参数以维持调用约定，但不再作用于按钮外观
     */
    public void setSidebarActive(boolean active) {
        if (sidebarButton == null) return;
        sidebarButton.getStyleClass().remove(ACTIVE_STYLE_CLASS);
    }
}