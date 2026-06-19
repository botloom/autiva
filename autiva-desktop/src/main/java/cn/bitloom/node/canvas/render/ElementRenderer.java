package cn.bitloom.node.canvas.render;

import cn.bitloom.node.canvas.model.*;
import javafx.scene.canvas.GraphicsContext;

/**
 * 元素渲染器接口（访问者模式）。
 * 每种元素类型对应一个渲染方法，CanvasRenderer 实现此接口。
 */
public interface ElementRenderer {
    void render(RectangleElement el, GraphicsContext gc);
    void render(EllipseElement el, GraphicsContext gc);
    void render(DiamondElement el, GraphicsContext gc);
    void render(LineElement el, GraphicsContext gc);
    void render(ArrowElement el, GraphicsContext gc);
    void render(TextElement el, GraphicsContext gc);
    void render(FreehandElement el, GraphicsContext gc);
}
