package cn.bitloom.node.canvas.model;

import cn.bitloom.node.canvas.render.ElementRenderer;
import javafx.scene.canvas.GraphicsContext;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.scene.shape.Rectangle;
import lombok.Getter;

@Getter
public class FreehandElement extends CanvasElement {

    private final ObservableList<Point> points = FXCollections.observableArrayList();
    private static final double HIT_THRESHOLD = 8.0;

    @Override
    public String getType() {
        return "freehand";
    }

    @Override
    public boolean contains(Point point) {
        for (int i = 0; i < points.size() - 1; i++) {
            Point p1 = points.get(i);
            Point p2 = points.get(i + 1);
            if (pointToSegmentDistance(point.x(), point.y(), p1.x(), p1.y(), p2.x(), p2.y()) < HIT_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Bounds getBounds() {
        if (points.isEmpty()) {
            return new Rectangle(getX(), getY(), 0, 0).getBoundsInLocal();
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
        for (Point p : points) {
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
        }
        return new Rectangle(minX, minY, maxX - minX, maxY - minY).getBoundsInLocal();
    }

    @Override
    public void accept(ElementRenderer renderer, GraphicsContext gc) {
        renderer.render(this, gc);
    }

    public void addPoint(Point point) {
        points.add(point);
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
