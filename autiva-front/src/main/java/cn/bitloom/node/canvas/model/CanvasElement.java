package cn.bitloom.node.canvas.model;

import cn.bitloom.node.canvas.render.ElementRenderer;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.BooleanProperty;
import javafx.scene.canvas.GraphicsContext;
import javafx.geometry.Bounds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class CanvasElement {
    private final String id;
    private double x;
    private double y;
    private double width;
    private double height;
    private double rotation;
    private String strokeColor = "#000000";
    private String fillColor = "transparent";
    private double strokeWidth = 2.0;
    private double opacity = 1.0;
    private double roughness = 1.0;
    private String lineStyle = "solid"; // solid, dashed, dotted
    private String arrowStyle = "none"; // none, arrow-start, arrow-end, arrow-both
    private double cornerRadius = 0; // 0=直角, >0=圆角
    private String text = ""; // 图形内嵌文字
    private final BooleanProperty visible = new SimpleBooleanProperty(true);
    private boolean locked = false;
    private long seed;
    private long version = 0;

    // 连接信息：线条/箭头的端点连接到哪个元素的哪个锚点索引
    private String startConnectedElementId = null; // 起点连接的元素ID
    private int startConnectedAnchorIndex = -1;     // 起点连接的锚点索引
    private String endConnectedElementId = null;     // 终点连接的元素ID
    private int endConnectedAnchorIndex = -1;        // 终点连接的锚点索引

    protected CanvasElement() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.seed = System.nanoTime() % 100000;
    }

    public abstract String getType();
    public abstract boolean contains(Point point);
    public abstract Bounds getBounds();
    public abstract void accept(ElementRenderer renderer, GraphicsContext gc);

    /**
     * 获取元素的锚点列表（场景坐标）。
     * 默认实现返回四角、四边中点、中心点共9个锚点。
     * 子类可覆盖以提供自定义锚点（如线条的端点）。
     */
    public List<Point> getAnchorPoints() {
        List<Point> anchors = new ArrayList<>();
        double x = getX(), y = getY();
        double w = getWidth(), h = getHeight();
        // 四角
        anchors.add(new Point(x, y));
        anchors.add(new Point(x + w, y));
        anchors.add(new Point(x + w, y + h));
        anchors.add(new Point(x, y + h));
        // 四边中点
        anchors.add(new Point(x + w / 2, y));
        anchors.add(new Point(x + w, y + h / 2));
        anchors.add(new Point(x + w / 2, y + h));
        anchors.add(new Point(x, y + h / 2));
        // 中心点
        anchors.add(new Point(x + w / 2, y + h / 2));
        return anchors;
    }

    // Getters and setters
    public String getId() { return id; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; this.version++; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; this.version++; }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; this.version++; }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; this.version++; }
    public double getRotation() { return rotation; }
    public void setRotation(double rotation) { this.rotation = rotation; this.version++; }
    public String getStrokeColor() { return strokeColor; }
    public void setStrokeColor(String strokeColor) { this.strokeColor = strokeColor; this.version++; }
    public String getFillColor() { return fillColor; }
    public void setFillColor(String fillColor) { this.fillColor = fillColor; this.version++; }
    public double getStrokeWidth() { return strokeWidth; }
    public void setStrokeWidth(double strokeWidth) { this.strokeWidth = strokeWidth; this.version++; }
    public double getOpacity() { return opacity; }
    public void setOpacity(double opacity) { this.opacity = opacity; this.version++; }
    public double getRoughness() { return roughness; }
    public void setRoughness(double roughness) { this.roughness = roughness; this.version++; }
    public long getSeed() { return seed; }
    public void setSeed(long seed) { this.seed = seed; }
    public long getVersion() { return version; }
    public String getLineStyle() { return lineStyle; }
    public void setLineStyle(String lineStyle) { this.lineStyle = lineStyle; this.version++; }
    public String getArrowStyle() { return arrowStyle; }
    public void setArrowStyle(String arrowStyle) { this.arrowStyle = arrowStyle; this.version++; }
    public boolean isVisible() { return visible.get(); }
    public void setVisible(boolean visible) { this.visible.set(visible); this.version++; }
    public BooleanProperty visibleProperty() { return visible; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; this.version++; }
    public double getCornerRadius() { return cornerRadius; }
    public void setCornerRadius(double cornerRadius) { this.cornerRadius = cornerRadius; this.version++; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; this.version++; }

    public String getStartConnectedElementId() { return startConnectedElementId; }
    public void setStartConnectedElementId(String id) { this.startConnectedElementId = id; }
    public int getStartConnectedAnchorIndex() { return startConnectedAnchorIndex; }
    public void setStartConnectedAnchorIndex(int idx) { this.startConnectedAnchorIndex = idx; }
    public String getEndConnectedElementId() { return endConnectedElementId; }
    public void setEndConnectedElementId(String id) { this.endConnectedElementId = id; }
    public int getEndConnectedAnchorIndex() { return endConnectedAnchorIndex; }
    public void setEndConnectedAnchorIndex(int idx) { this.endConnectedAnchorIndex = idx; }

    /**
     * 判断该元素是否为连接型元素（线条/箭头）
     */
    public boolean isConnector() {
        return "line".equals(getType()) || "arrow".equals(getType());
    }
}
