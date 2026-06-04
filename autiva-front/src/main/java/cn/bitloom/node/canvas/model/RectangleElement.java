package cn.bitloom.node.canvas.model;

import cn.bitloom.node.canvas.render.ElementRenderer;
import javafx.scene.canvas.GraphicsContext;
import javafx.geometry.Bounds;
import javafx.scene.shape.Rectangle;

public class RectangleElement extends CanvasElement {

    @Override
    public String getType() {
        return "rectangle";
    }

    @Override
    public boolean contains(Point point) {
        return point.x() >= getX() && point.x() <= getX() + getWidth()
            && point.y() >= getY() && point.y() <= getY() + getHeight();
    }

    @Override
    public Bounds getBounds() {
        return new Rectangle(getX(), getY(), getWidth(), getHeight()).getBoundsInLocal();
    }

    @Override
    public void accept(ElementRenderer renderer, GraphicsContext gc) {
        renderer.render(this, gc);
    }
}
