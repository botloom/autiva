package cn.bitloom.node.editor;

import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;

import java.util.function.Consumer;

/**
 * "添加到对话框"悬浮按钮组件。
 *
 * <p>在编辑器面板（终端/Diff/文件内容）选中文本后浮现，点击后将选中文本追加到对话框输入框。
 * 默认不可见（visible=false, managed=false），通过 {@link #show(String, double, double)} 显示并定位，
 * 通过 {@link #hide()} 隐藏。
 *
 * <p>定位策略：因为父容器是 StackPane（默认居中对齐），使用 setLayoutX/setLayoutY
 * 配合 managed=false 实现绝对定位。show() 调用后通过 requestLayout + runLater 确保
 * 按钮在 StackPane 布局完成后正确定位。
 *
 * <p>样式类：{@code add-to-chat-button}（Apple 风格圆角半透明深色背景 + 阴影）。
 */
public class AddToChatButton extends Button {

    private String selectedText;
    private Consumer<String> onAddToChat;
    private double targetX;
    private double targetY;

    public AddToChatButton() {
        super("添加到对话框");
        getStyleClass().add("add-to-chat-button");
        setVisible(false);
        setManaged(false);
        setOnAction(e -> {
            if (onAddToChat != null && selectedText != null && !selectedText.isBlank()) {
                onAddToChat.accept(selectedText);
            }
            hide();
        });
    }

    /**
     * 显示按钮并定位到指定坐标（父容器坐标系），携带选中文本。
     *
     * @param text 选中的文本内容
     * @param x    父容器坐标系中的 X 坐标
     * @param y    父容器坐标系中的 Y 坐标
     */
    public void show(String text, double x, double y) {
        this.selectedText = text;
        this.targetX = x;
        this.targetY = y;
        setLayoutX(x);
        setLayoutY(y);
        setVisible(true);
        toFront();
        // StackPane 在布局脉冲时可能覆盖 setLayoutX/setLayoutY，
        // 用 runLater 在布局完成后重新定位
        javafx.application.Platform.runLater(() -> {
            setLayoutX(targetX);
            setLayoutY(targetY);
        });
    }

    /**
     * 隐藏按钮并清除选中文本。
     */
    public void hide() {
        setVisible(false);
        this.selectedText = null;
    }

    /**
     * 设置点击回调，参数为选中文本。
     */
    public void setOnAddToChat(Consumer<String> callback) {
        this.onAddToChat = callback;
    }
}
