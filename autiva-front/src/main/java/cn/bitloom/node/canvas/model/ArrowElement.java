package cn.bitloom.node.canvas.model;

import cn.bitloom.node.canvas.render.ElementRenderer;
import javafx.scene.canvas.GraphicsContext;
import javafx.geometry.Bounds;
import javafx.scene.shape.Line;
import java.util.ArrayList;
import java.util.List;

public class ArrowElement extends CanvasElement {

    private static final double HIT_THRESHOLD = 8.0;

    @Override
    public String getType() {
        return "arrow";
    }

    @Override
    public boolean contains(Point point) {
        double x1 = getX(), y1 = getY();
        double x2 = getX() + getWidth(), y2 = getY() + getHeight();
        return pointToSegmentDistance(point.x(), point.y(), x1, y1, x2, y2) < HIT_THRESHOLD;
    }

    @Override
    public Bounds getBounds() {
        double x1 = getX(), y1 = getY();
        double x2 = getX() + getWidth(), y2 = getY() + getHeight();
        return new Line(x1, y1, x2, y2).getBoundsInLocal();
    }

    @Override
    public void accept(ElementRenderer renderer, GraphicsContext gc) {
        renderer.render(this, gc);
    }

    @Override
    public List<Point> getAnchorPoints() {
        List<Point> anchors = new ArrayList<>();
        anchors.add(new Point(getX(), getY()));
        anchors.add(new Point(getX() + getWidth(), getY() + getHeight()));
        return anchors;
    }

    private double pointToSegmentDistance(double px, double py,
                                          double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double lenSq = dx * dx + dy * dy;
        if (lenSq == 0) return Math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1));
        double t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / lenSq));
        double projX = x1 + t * dx, projY = y1 + t * dy;
        return Math.sqrt((px - projX) * (px - projX) + (py - projY) * (py - projY));
    }
}
