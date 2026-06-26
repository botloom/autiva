package cn.bitloom.holder;

import javafx.event.EventHandler;

import java.util.List;

/**
 * ButtonBar 配置接口
 * 每个页面控制器实现此接口来定义自己的按钮配置
 *
 * @author bitloom
 */
public interface ButtonBarHolder {

    /**
     * 按钮对齐方式
     */
    enum Alignment {
        LEFT,   // 左对齐（放在 dynamicButtonContainer）
        RIGHT   // 右对齐（放在 rightButtonContainer）
    }

    /**
     * 获取按钮配置列表
     *
     * @return 按钮配置列表
     */
    List<ButtonConfig> getButtonConfigs();

    /**
     * 按钮配置类
     */
    record ButtonConfig(
            String id,
            String text,
            String styleClass,
            String svgPath,
            Alignment alignment,
            EventHandler<javafx.event.ActionEvent> actionHandler
    ) {
        // 兼容旧的构造方式（无图标，左对齐）
        public ButtonConfig(String id, String text, String styleClass, EventHandler<javafx.event.ActionEvent> actionHandler) {
            this(id, text, styleClass, null, Alignment.LEFT, actionHandler);
        }

        // 带图标，左对齐
        public ButtonConfig(String id, String text, String styleClass, String svgPath, EventHandler<javafx.event.ActionEvent> actionHandler) {
            this(id, text, styleClass, svgPath, Alignment.LEFT, actionHandler);
        }
    }
}