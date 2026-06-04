package cn.bitloom.node.canvas.model;

import cn.bitloom.node.canvas.render.ElementRenderer;
import javafx.scene.canvas.GraphicsContext;
import javafx.geometry.Bounds;
import javafx.scene.shape.Polygon;

public class DiamondElement extends CanvasElement {

    @Override
    public String getType() {
        return "diamond";
    }

    @Override
    public boolean contains(Point point) {
        double cx = getX() + getWidth() / 2;
        double cy = getY() + getHeight() / 2;
        double hw = getWidth() / 2;
        double hh = getHeight() / 2;
        if (hw == 0 || hh == 0) return false;
        // 菱形内部判断：|dx/hw| + |dy/hh| <= 1
        double dx = Math.abs(point.x() - cx);
        double dy = Math.abs(point.y() - cy);
        return (dx / hw) + (dy / hh) <= 1;
    }

    @Override
    public Bounds getBounds() {
        double cx = getX() + getWidth() / 2;
        double cy = getY() + getHeight() / 2;
        Polygon polygon = new Polygon(
            cx, getY(),
            getX() + getWidth(), cy,
            cx, getY() + getHeight(),
            getX(), cy
        );
        return polygon.getBoundsInLocal();
    }

    @Override
    public void accept(ElementRenderer renderer, GraphicsContext gc) {
        renderer.render(this, gc);
    }
}
