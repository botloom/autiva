package cn.bitloom.node.canvas.tool;

import cn.bitloom.node.canvas.model.CanvasScene;
import javafx.scene.Cursor;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

/**
 * 画布工具接口。
 * 每种绘图工具实现此接口，处理鼠标和键盘事件。
 */
public interface CanvasTool {

    /**
     * 获取工具名称
     */
    String getName();

    /**
     * 获取工具对应的光标样式
     */
    default Cursor getCursor() {
        return Cursor.CROSSHAIR;
    }

    /**
     * 鼠标按下
     */
    default void onMousePressed(MouseEvent e, CanvasScene scene) {}

    /**
     * 鼠标拖拽
     */
    default void onMouseDragged(MouseEvent e, CanvasScene scene) {}

    /**
     * 鼠标释放
     */
    default void onMouseReleased(MouseEvent e, CanvasScene scene) {}

    /**
     * 鼠标移动
     */
    default void onMouseMoved(MouseEvent e, CanvasScene scene) {}

    /**
     * 键盘按下
     */
    default void onKeyDown(KeyEvent e, CanvasScene scene) {}

    /**
     * 键盘释放
     */
    default void onKeyUp(KeyEvent e, CanvasScene scene) {}

    /**
     * 工具激活时调用
     */
    default void onActivate(CanvasScene scene) {}

    /**
     * 工具停用时调用
     */
    default void onDeactivate(CanvasScene scene) {}
}
