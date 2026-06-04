package cn.bitloom.node.canvas.render;

import cn.bitloom.node.canvas.model.*;
import cn.bitloom.node.canvas.tool.SelectTool;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 画布主渲染器。
 * 管理渲染循环，协调网格绘制、元素渲染和选择框渲染。
 */
public class CanvasRenderer implements ElementRenderer {

    private final Canvas canvas;
    @Getter
    private final RoughRenderer roughRenderer;
    @Getter
    private final SelectionRenderer selectionRenderer;
    private final CanvasScene scene;
    @Setter
    private SelectTool selectTool;
    private boolean showAnchors = false;
    private Point snappedAnchor = null;
    private boolean dirty = true;

    public CanvasRenderer(Canvas canvas, CanvasScene scene) {
        this.canvas = canvas;
        this.scene = scene;
        this.roughRenderer = new RoughRenderer();
        this.selectionRenderer = new SelectionRenderer();
    }

    /**
     * 执行渲染。仅在脏标记为 true 时重绘。
     */
    public void render() {
        if (!dirty) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        double width = canvas.getWidth();
        double height = canvas.getHeight();

        // 清除画布
        gc.clearRect(0, 0, width, height);

        // 绘制背景
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, width, height);

        // 绘制网格
        if (scene.isShowGrid()) {
            drawGrid(gc, width, height);
        }

        // 应用视口变换
        gc.save();
        gc.translate(scene.getPanX(), scene.getPanY());
        gc.scale(scene.getZoom(), scene.getZoom());

        // 渲染所有元素
        for (CanvasElement element : scene.getElements()) {
            if (!element.isVisible()) continue;

            gc.save();
            gc.setGlobalAlpha(element.getOpacity());
            applyStrokeStyle(gc, element);
            element.accept(this, gc);
            gc.restore();
        }

        // 渲染选中元素的选择框
        if (selectTool != null && !selectTool.getSelectedElements().isEmpty()) {
            gc.save();
            for (CanvasElement el : selectTool.getSelectedElements()) {
                selectionRenderer.render(gc, el);
            }
            gc.restore();
        }

        // 渲染锚点（线条/箭头工具激活时）
        if (showAnchors) {
            gc.save();
            for (CanvasElement el : scene.getElements()) {
                if (!el.isVisible()) continue;
                for (Point anchor : el.getAnchorPoints()) {
                    gc.setFill(Color.web("#1a73e8", 0.3));
                    gc.fillOval(anchor.x() - 3, anchor.y() - 3, 6, 6);
                }
            }
            gc.restore();
        }

        // 渲染吸附指示器
        if (snappedAnchor != null) {
            gc.save();
            gc.setStroke(Color.web("#1a73e8"));
            gc.setLineWidth(2);
            gc.strokeOval(snappedAnchor.x() - 6, snappedAnchor.y() - 6, 12, 12);
            gc.setFill(Color.web("#1a73e8", 0.2));
            gc.fillOval(snappedAnchor.x() - 6, snappedAnchor.y() - 6, 12, 12);
            gc.restore();
        }

        gc.restore();
        dirty = false;
    }

    /**
     * 标记需要重绘
     */
    public void markDirty() {
        dirty = true;
    }

    public void setShowAnchors(boolean showAnchors) {
        this.showAnchors = showAnchors;
        dirty = true;
    }

    public void setSnappedAnchor(Point snappedAnchor) {
        this.snappedAnchor = snappedAnchor;
        dirty = true;
    }

    private void drawGrid(GraphicsContext gc, double width, double height) {
        gc.save();
        gc.setStroke(Color.web("#e0e0e0"));
        gc.setLineWidth(0.5);

        double step = scene.getGridStep() * scene.getZoom();
        double offsetX = scene.getPanX() % step;
        double offsetY = scene.getPanY() % step;

        // 垂直线
        for (double x = offsetX; x < width; x += step) {
            gc.strokeLine(x, 0, x, height);
        }
        // 水平线
        for (double y = offsetY; y < height; y += step) {
            gc.strokeLine(0, y, width, y);
        }

        gc.restore();
    }

    // ---- 样式设置 ----

    private void applyStrokeStyle(GraphicsContext gc, CanvasElement element) {
        String strokeColor = element.getStrokeColor();
        if (!"transparent".equals(strokeColor)) {
            gc.setStroke(parseColor(strokeColor));
        }
        String fillColor = element.getFillColor();
        if (!"transparent".equals(fillColor)) {
            gc.setFill(parseColor(fillColor));
        }
        gc.setLineWidth(element.getStrokeWidth());

        // 线条风格
        String lineStyle = element.getLineStyle();
        if ("dashed".equals(lineStyle)) {
            gc.setLineDashes(10, 6);
        } else if ("dotted".equals(lineStyle)) {
            gc.setLineDashes(3, 3);
        } else {
            gc.setLineDashes(); // 实线
        }
    }

    /**
     * 解析颜色字符串，支持 #hex 和 rgba() 格式
     */
    private Color parseColor(String colorStr) {
        if (colorStr == null || "transparent".equals(colorStr)) {
            return Color.TRANSPARENT;
        }
        if (colorStr.startsWith("rgba(")) {
            // 格式: rgba(r, g, b, a)
            String inner = colorStr.substring(5, colorStr.length() - 1);
            String[] parts = inner.split(",");
            double r = Double.parseDouble(parts[0].trim()) / 255.0;
            double g = Double.parseDouble(parts[1].trim()) / 255.0;
            double b = Double.parseDouble(parts[2].trim()) / 255.0;
            double a = Double.parseDouble(parts[3].trim());
            return new Color(r, g, b, a);
        }
        return Color.web(colorStr);
    }

    // ---- ElementRenderer 实现 ----

    @Override
    public void render(RectangleElement el, GraphicsContext gc) {
        double x = el.getX(), y = el.getY(), w = el.getWidth(), h = el.getHeight();
        double roughness = el.getRoughness();
        long seed = el.getSeed();
        double cornerRadius = el.getCornerRadius();

        // 填充
        if (!"transparent".equals(el.getFillColor())) {
            gc.save();
            gc.setFill(parseColor(el.getFillColor()));
            gc.setGlobalAlpha(el.getOpacity() * 0.3);
            if (cornerRadius > 0) {
                roughRenderer.drawRoundRectHachureFill(gc, x, y, w, h, cornerRadius, roughness, seed + 400);
            } else {
                roughRenderer.drawHachureFill(gc, x, y, w, h, roughness, seed + 400);
            }
            gc.restore();
        }

        // 描边
        if (cornerRadius > 0) {
            roughRenderer.drawRoundRectangle(gc, x, y, w, h, cornerRadius, roughness, seed);
        } else {
            roughRenderer.drawRectangle(gc, x, y, w, h, roughness, seed);
        }

        // 渲染内嵌文字
        renderElementText(el, gc);
    }

    @Override
    public void render(EllipseElement el, GraphicsContext gc) {
        double cx = el.getX() + el.getWidth() / 2;
        double cy = el.getY() + el.getHeight() / 2;
        double rx = el.getWidth() / 2;
        double ry = el.getHeight() / 2;
        double roughness = el.getRoughness();
        long seed = el.getSeed();

        // 填充
        if (!"transparent".equals(el.getFillColor())) {
            gc.save();
            gc.setFill(parseColor(el.getFillColor()));
            gc.setGlobalAlpha(el.getOpacity() * 0.3);
            roughRenderer.drawEllipseHachureFill(gc, cx, cy, rx, ry, roughness, seed + 400);
            gc.restore();
        }

        // 描边
        roughRenderer.drawEllipse(gc, cx, cy, rx, ry, roughness, seed);

        // 渲染内嵌文字
        renderElementText(el, gc);
    }

    @Override
    public void render(DiamondElement el, GraphicsContext gc) {
        double x = el.getX(), y = el.getY(), w = el.getWidth(), h = el.getHeight();
        double roughness = el.getRoughness();
        long seed = el.getSeed();

        // 填充
        if (!"transparent".equals(el.getFillColor())) {
            gc.save();
            gc.setFill(parseColor(el.getFillColor()));
            gc.setGlobalAlpha(el.getOpacity() * 0.3);
            // 菱形填充使用矩形近似
            roughRenderer.drawHachureFill(gc, x, y, w, h, roughness, seed + 400);
            gc.restore();
        }

        roughRenderer.drawDiamond(gc, x, y, w, h, roughness, seed);

        // 渲染内嵌文字
        renderElementText(el, gc);
    }

    @Override
    public void render(LineElement el, GraphicsContext gc) {
        roughRenderer.drawLine(gc, el.getX(), el.getY(),
                el.getX() + el.getWidth(), el.getY() + el.getHeight(),
                el.getRoughness(), el.getSeed());
    }

    @Override
    public void render(ArrowElement el, GraphicsContext gc) {
        roughRenderer.drawArrow(gc, el.getX(), el.getY(),
                el.getX() + el.getWidth(), el.getY() + el.getHeight(),
                el.getRoughness(), el.getSeed());
    }

    @Override
    public void render(TextElement el, GraphicsContext gc) {
        gc.save();
        gc.setFill(parseColor(el.getStrokeColor()));
        gc.setFont(getHandwrittenFont(el.getFontSize()));
        gc.fillText(el.getText(), el.getX(), el.getY() + el.getFontSize());
        gc.restore();
    }

    @Override
    public void render(FreehandElement el, GraphicsContext gc) {
        if (el.getPoints().size() < 2) return;

        gc.save();
        gc.setStroke(parseColor(el.getStrokeColor()));
        gc.setLineWidth(el.getStrokeWidth());
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

        gc.beginPath();
        Point first = el.getPoints().get(0);
        gc.moveTo(first.x(), first.y());

        for (int i = 1; i < el.getPoints().size() - 1; i++) {
            Point current = el.getPoints().get(i);
            Point next = el.getPoints().get(i + 1);
            double midX = (current.x() + next.x()) / 2;
            double midY = (current.y() + next.y()) / 2;
            gc.quadraticCurveTo(current.x(), current.y(), midX, midY);
        }

        Point last = el.getPoints().get(el.getPoints().size() - 1);
        gc.lineTo(last.x(), last.y());
        gc.stroke();
        gc.restore();
    }

    /**
     * 渲染元素内嵌文字（居中显示，手写字体）
     */
    private void renderElementText(CanvasElement el, GraphicsContext gc) {
        String text = el.getText();
        if (text == null || text.isEmpty()) return;

        gc.save();
        gc.setFill(parseColor(el.getStrokeColor()));
        double fontSize = Math.min(16, Math.min(el.getWidth() / 4, el.getHeight() / 2));
        fontSize = Math.max(fontSize, 10);
        gc.setFont(getHandwrittenFont(fontSize));

        // 居中绘制
        double textWidth = computeTextWidth(text, fontSize);
        double textHeight = fontSize;
        double centerX = el.getX() + el.getWidth() / 2;
        double centerY = el.getY() + el.getHeight() / 2;

        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        gc.setTextBaseline(javafx.geometry.VPos.CENTER);
        gc.fillText(text, centerX, centerY);
        gc.restore();
    }

    /**
     * 获取手写字体，按优先级尝试系统可用的手写字体
     */
    private javafx.scene.text.Font getHandwrittenFont(double size) {
        if (handwrittenFontFamily != null) {
            return javafx.scene.text.Font.font(handwrittenFontFamily, size);
        }
        return javafx.scene.text.Font.font(size);
    }

    /** 缓存的手写字体族名 */
    private static final String handwrittenFontFamily = findHandwrittenFontFamily();

    /**
     * 查找系统中可用的手写字体族名
     */
    private static String findHandwrittenFontFamily() {
        java.util.List<String> candidates = java.util.List.of(
            "Segoe Script",       // Windows
            "Bradley Hand",       // macOS
            "Comic Sans MS",      // 通用回退
            "Kristen ITC",        // Windows
            "Lucida Handwriting", // 通用
            "Apple Chancery"      // macOS
        );
        List<String> available = Font.getFamilies();
        for (String candidate : candidates) {
            if (available.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 估算文字宽度
     */
    private double computeTextWidth(String text, double fontSize) {
        return text.length() * fontSize * 0.6;
    }
}
