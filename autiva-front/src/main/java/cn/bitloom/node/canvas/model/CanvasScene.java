package cn.bitloom.node.canvas.model;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;

public class CanvasScene {
    private final ObservableList<CanvasElement> elements = FXCollections.observableArrayList();
    private final DoubleProperty zoom = new SimpleDoubleProperty(1.0);
    private double panX = 0;
    private double panY = 0;
    private int gridStep = 20;
    private boolean showGrid = false;

    public Point screenToScene(double sx, double sy) {
        return new Point(
            (sx - panX) / zoom.get(),
            (sy - panY) / zoom.get()
        );
    }

    public Point sceneToScreen(double sx, double sy) {
        return new Point(
            sx * zoom.get() + panX,
            sy * zoom.get() + panY
        );
    }

    public void addElement(CanvasElement element) {
        elements.add(element);
    }

    public void removeElement(CanvasElement element) {
        elements.remove(element);
    }

    public void clearElements() {
        elements.clear();
    }

    /**
     * 将元素上移一层（在列表中后移，渲染时后绘制的在上层）
     */
    public void moveElementUp(CanvasElement element) {
        int idx = elements.indexOf(element);
        if (idx >= 0 && idx < elements.size() - 1) {
            elements.remove(idx);
            elements.add(idx + 1, element);
        }
    }

    /**
     * 将元素下移一层（在列表中前移）
     */
    public void moveElementDown(CanvasElement element) {
        int idx = elements.indexOf(element);
        if (idx > 0) {
            elements.remove(idx);
            elements.add(idx - 1, element);
        }
    }

    /**
     * 将元素移到最上层
     */
    public void moveElementToTop(CanvasElement element) {
        if (elements.remove(element)) {
            elements.add(element);
        }
    }

    /**
     * 将元素移到最下层
     */
    public void moveElementToBottom(CanvasElement element) {
        if (elements.remove(element)) {
            elements.add(0, element);
        }
    }

    /**
     * 查找距离指定点最近的锚点。
     * @param point 场景坐标点
     * @param threshold 吸附阈值（场景坐标距离）
     * @param excludeElement 排除的元素（通常是正在绘制的线条自身），可为 null
     * @return 最近的锚点，如果在阈值内；否则返回 null
     */
    public Point findNearestAnchor(Point point, double threshold, CanvasElement excludeElement) {
        Point nearest = null;
        double minDist = threshold;
        for (CanvasElement el : elements) {
            if (el == excludeElement || !el.isVisible()) continue;
            for (Point anchor : el.getAnchorPoints()) {
                double dist = point.distanceTo(anchor);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = anchor;
                }
            }
        }
        return nearest;
    }

    /**
     * 根据元素ID查找元素
     */
    public CanvasElement findElementById(String id) {
        if (id == null) return null;
        for (CanvasElement el : elements) {
            if (id.equals(el.getId())) return el;
        }
        return null;
    }

    /**
     * 更新所有连接到指定元素的线条/箭头端点位置。
     * 当元素移动或缩放时调用。
     */
    public void updateConnectedLines(CanvasElement movedElement) {
        String movedId = movedElement.getId();
        List<Point> movedAnchors = movedElement.getAnchorPoints();

        for (CanvasElement el : elements) {
            if (!el.isConnector()) continue;

            // 检查起点是否连接到移动的元素
            if (movedId.equals(el.getStartConnectedElementId())) {
                int anchorIdx = el.getStartConnectedAnchorIndex();
                if (anchorIdx >= 0 && anchorIdx < movedAnchors.size()) {
                    Point newAnchor = movedAnchors.get(anchorIdx);
                    // 线条起点 = (x, y)，终点 = (x + width, y + height)
                    double endX = el.getX() + el.getWidth();
                    double endY = el.getY() + el.getHeight();
                    el.setX(newAnchor.x());
                    el.setY(newAnchor.y());
                    el.setWidth(endX - newAnchor.x());
                    el.setHeight(endY - newAnchor.y());
                }
            }

            // 检查终点是否连接到移动的元素
            if (movedId.equals(el.getEndConnectedElementId())) {
                int anchorIdx = el.getEndConnectedAnchorIndex();
                if (anchorIdx >= 0 && anchorIdx < movedAnchors.size()) {
                    Point newAnchor = movedAnchors.get(anchorIdx);
                    el.setWidth(newAnchor.x() - el.getX());
                    el.setHeight(newAnchor.y() - el.getY());
                }
            }
        }
    }

    // Getters and setters
    public ObservableList<CanvasElement> getElements() { return elements; }
    public double getZoom() { return zoom.get(); }
    public void setZoom(double zoom) { this.zoom.set(Math.max(0.1, Math.min(3.0, zoom))); }
    public DoubleProperty zoomProperty() { return zoom; }
    public double getPanX() { return panX; }
    public void setPanX(double panX) { this.panX = panX; }
    public double getPanY() { return panY; }
    public void setPanY(double panY) { this.panY = panY; }
    public int getGridStep() { return gridStep; }
    public void setGridStep(int gridStep) { this.gridStep = gridStep; }
    public boolean isShowGrid() { return showGrid; }
    public void setShowGrid(boolean showGrid) { this.showGrid = showGrid; }
}
