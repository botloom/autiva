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
            EventHandler<javafx.event.ActionEvent> actionHandler
    ) {
    }
}
