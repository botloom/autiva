package cn.bitloom.node.canvas.render;

import cn.bitloom.node.canvas.model.CanvasElement;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.geometry.Bounds;

/**
 * 选择框和手柄渲染器。
 * 在选中元素周围绘制虚线边框和调整手柄。
 * 对于连接型元素（线条/箭头），绘制两个圆形端点手柄。
 */
public class SelectionRenderer {

    private static final double HANDLE_SIZE = 8;
    private static final double HANDLE_HALF = HANDLE_SIZE / 2;
    private static final double ENDPOINT_SIZE = 10;
    private static final double ENDPOINT_HALF = ENDPOINT_SIZE / 2;
    private static final double DASH_OFFSET = 6;

    /**
     * 绘制选择框和手柄
     */
    public void render(GraphicsContext gc, CanvasElement element) {
        if (element.isConnector()) {
            renderConnectorSelection(gc, element);
        } else {
            renderShapeSelection(gc, element);
        }
    }

    /**
     * 渲染连接型元素（线条/箭头）的选择框：两个圆形端点手柄
     */
    private void renderConnectorSelection(GraphicsContext gc, CanvasElement element) {
        double startX = element.getX(), startY = element.getY();
        double endX = element.getX() + element.getWidth(), endY = element.getY() + element.getHeight();

        gc.save();
        gc.setStroke(Color.web("#1a73e8"));
        gc.setLineWidth(1.5);

        // 起点手柄 - 圆形
        gc.setFill(Color.WHITE);
        gc.strokeOval(startX - ENDPOINT_HALF, startY - ENDPOINT_HALF, ENDPOINT_SIZE, ENDPOINT_SIZE);
        gc.fillOval(startX - ENDPOINT_HALF, startY - ENDPOINT_HALF, ENDPOINT_SIZE, ENDPOINT_SIZE);

        // 终点手柄 - 圆形
        gc.strokeOval(endX - ENDPOINT_HALF, endY - ENDPOINT_HALF, ENDPOINT_SIZE, ENDPOINT_SIZE);
        gc.fillOval(endX - ENDPOINT_HALF, endY - ENDPOINT_HALF, ENDPOINT_SIZE, ENDPOINT_SIZE);

        gc.restore();
    }

    /**
     * 渲染普通形状的选择框：虚线边框 + 8个方形手柄
     */
    private void renderShapeSelection(GraphicsContext gc, CanvasElement element) {
        Bounds bounds = element.getBounds();
        double x = bounds.getMinX();
        double y = bounds.getMinY();
        double w = bounds.getWidth();
        double h = bounds.getHeight();

        // 虚线边框
        gc.save();
        gc.setLineDashes(6, 4);
        gc.setStroke(Color.web("#1a73e8"));
        gc.setLineWidth(1);
        gc.strokeRect(x, y, w, h);
        gc.setLineDashes();

        // 8个调整手柄
        gc.setFill(Color.WHITE);
        gc.setStroke(Color.web("#1a73e8"));
        gc.setLineWidth(1.5);

        // 四角
        drawHandle(gc, x, y);                          // 左上
        drawHandle(gc, x + w, y);                      // 右上
        drawHandle(gc, x + w, y + h);                  // 右下
        drawHandle(gc, x, y + h);                      // 左下
        // 四边中点
        drawHandle(gc, x + w / 2, y);                  // 上中
        drawHandle(gc, x + w, y + h / 2);              // 右中
        drawHandle(gc, x + w / 2, y + h);              // 下中
        drawHandle(gc, x, y + h / 2);                  // 左中

        gc.restore();
    }

    private void drawHandle(GraphicsContext gc, double cx, double cy) {
        gc.fillRect(cx - HANDLE_HALF, cy - HANDLE_HALF, HANDLE_SIZE, HANDLE_SIZE);
        gc.strokeRect(cx - HANDLE_HALF, cy - HANDLE_HALF, HANDLE_SIZE, HANDLE_SIZE);
    }
}
