package cn.bitloom.node.canvas.model;

import cn.bitloom.node.canvas.render.ElementRenderer;
import javafx.scene.canvas.GraphicsContext;
import javafx.geometry.Bounds;
import javafx.scene.shape.Ellipse;

public class EllipseElement extends CanvasElement {

    @Override
    public String getType() {
        return "ellipse";
    }

    @Override
    public boolean contains(Point point) {
        double cx = getX() + getWidth() / 2;
        double cy = getY() + getHeight() / 2;
        double rx = getWidth() / 2;
        double ry = getHeight() / 2;
        if (rx == 0 || ry == 0) return false;
        double dx = (point.x() - cx) / rx;
        double dy = (point.y() - cy) / ry;
        return dx * dx + dy * dy <= 1;
    }

    @Override
    public Bounds getBounds() {
        return new Ellipse(
            getX() + getWidth() / 2, getY() + getHeight() / 2,
            getWidth() / 2, getHeight() / 2
        ).getBoundsInLocal();
    }

    @Override
    public void accept(ElementRenderer renderer, GraphicsContext gc) {
        renderer.render(this, gc);
    }
}
