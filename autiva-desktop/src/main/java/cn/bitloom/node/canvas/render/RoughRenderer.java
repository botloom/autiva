package cn.bitloom.node.canvas.render;

import javafx.scene.canvas.GraphicsContext;
import java.util.Random;

/**
 * 手绘风格渲染器，移植自 Rough.js 核心算法。
 * 使用确定性随机偏移产生手绘效果，同一 seed 值保证渲染结果一致。
 */
public class RoughRenderer {

    private static final double DEFAULT_ROUGHNESS = 1.0;
    private static final double DEFAULT_BOWING = 1.0;
    private static final double OFFSET_EPSILON = 0.8;

    /**
     * 手绘直线（两次描边产生手绘效果）
     */
    public void drawLine(GraphicsContext gc, double x1, double y1, double x2, double y2,
                         double roughness, long seed) {
        double[] offsets = generateOffsets(seed, 4);
        double offset1 = offsets[0] * roughness * OFFSET_EPSILON;
        double offset2 = offsets[1] * roughness * OFFSET_EPSILON;
        double offset3 = offsets[2] * roughness * OFFSET_EPSILON;
        double offset4 = offsets[3] * roughness * OFFSET_EPSILON;

        // 第一次描边
        roughLine(gc, x1 + offset1, y1 + offset2, x2 + offset3, y2 + offset4, roughness, seed);
        // 第二次描边（偏移更大，产生手绘双重线效果）
        roughLine(gc, x1 + offset1 * 2.0, y1 + offset2 * 2.0, x2 + offset3 * 2.0, y2 + offset4 * 2.0, roughness, seed + 1);
    }

    /**
     * 手绘矩形
     */
    public void drawRectangle(GraphicsContext gc, double x, double y, double w, double h,
                              double roughness, long seed) {
        drawLine(gc, x, y, x + w, y, roughness, seed);
        drawLine(gc, x + w, y, x + w, y + h, roughness, seed + 100);
        drawLine(gc, x + w, y + h, x, y + h, roughness, seed + 200);
        drawLine(gc, x, y + h, x, y, roughness, seed + 300);
    }

    /**
     * 手绘圆角矩形
     */
    public void drawRoundRectangle(GraphicsContext gc, double x, double y, double w, double h,
                                   double cornerRadius, double roughness, long seed) {
        double r = Math.min(cornerRadius, Math.min(w, h) / 2);
        // 四条直线段
        drawLine(gc, x + r, y, x + w - r, y, roughness, seed);
        drawLine(gc, x + w, y + r, x + w, y + h - r, roughness, seed + 100);
        drawLine(gc, x + w - r, y + h, x + r, y + h, roughness, seed + 200);
        drawLine(gc, x, y + h - r, x, y + r, roughness, seed + 300);
        // 四个圆角弧线
        drawArc(gc, x + w - r, y + r, r, -Math.PI / 2, 0, roughness, seed + 400);
        drawArc(gc, x + w - r, y + h - r, r, 0, Math.PI / 2, roughness, seed + 500);
        drawArc(gc, x + r, y + h - r, r, Math.PI / 2, Math.PI, roughness, seed + 600);
        drawArc(gc, x + r, y + r, r, Math.PI, Math.PI * 1.5, roughness, seed + 700);
    }

    /**
     * 手绘弧线
     */
    private void drawArc(GraphicsContext gc, double cx, double cy, double r,
                         double startAngle, double endAngle, double roughness, long seed) {
        double step = 0.1;
        double[] offsets = generateOffsets(seed, 100);

        // 第一次描边
        gc.beginPath();
        for (int i = 0; i <= (int) ((endAngle - startAngle) / step); i++) {
            double angle = startAngle + i * step;
            double offIdx = i % offsets.length;
            double offsetX = offsets[(int) offIdx] * roughness * OFFSET_EPSILON * 0.5;
            double offsetY = offsets[((int) offIdx + 50) % offsets.length] * roughness * OFFSET_EPSILON * 0.5;
            double px = cx + r * Math.cos(angle) + offsetX;
            double py = cy + r * Math.sin(angle) + offsetY;
            if (i == 0) gc.moveTo(px, py);
            else gc.lineTo(px, py);
        }
        gc.stroke();

        // 第二次描边
        double[] offsets2 = generateOffsets(seed + 100, 100);
        gc.beginPath();
        for (int i = 0; i <= (int) ((endAngle - startAngle) / step); i++) {
            double angle = startAngle + i * step;
            double offIdx = i % offsets2.length;
            double offsetX = offsets2[(int) offIdx] * roughness * OFFSET_EPSILON * 0.7;
            double offsetY = offsets2[((int) offIdx + 50) % offsets2.length] * roughness * OFFSET_EPSILON * 0.7;
            double px = cx + r * Math.cos(angle) + offsetX;
            double py = cy + r * Math.sin(angle) + offsetY;
            if (i == 0) gc.moveTo(px, py);
            else gc.lineTo(px, py);
        }
        gc.stroke();
    }

    /**
     * 圆角矩形填充（hachure）
     */
    public void drawRoundRectHachureFill(GraphicsContext gc, double x, double y, double w, double h,
                                         double cornerRadius, double roughness, long seed) {
        // 简化：使用矩形填充，圆角区域会被描边覆盖
        drawHachureFill(gc, x, y, w, h, roughness, seed);
    }

    /**
     * 手绘椭圆
     */
    public void drawEllipse(GraphicsContext gc, double cx, double cy, double rx, double ry,
                            double roughness, long seed) {
        double step = 0.05;
        int segments = (int) (2 * Math.PI / step);
        double[] offsets = generateOffsets(seed, segments * 2);

        // 第一次描边
        gc.beginPath();
        for (int i = 0; i <= segments; i++) {
            double angle = i * step;
            double offsetIdx = i % offsets.length;
            double offsetX = offsets[(int) offsetIdx] * roughness * OFFSET_EPSILON * 0.5;
            double offsetY = offsets[((int) offsetIdx + segments / 2) % offsets.length] * roughness * OFFSET_EPSILON * 0.5;
            double px = cx + rx * Math.cos(angle) + offsetX;
            double py = cy + ry * Math.sin(angle) + offsetY;
            if (i == 0) {
                gc.moveTo(px, py);
            } else {
                gc.lineTo(px, py);
            }
        }
        gc.closePath();
        gc.stroke();

        // 第二次描边
        gc.beginPath();
        double[] offsets2 = generateOffsets(seed + 1000, segments * 2);
        for (int i = 0; i <= segments; i++) {
            double angle = i * step;
            double offsetIdx = i % offsets2.length;
            double offsetX = offsets2[(int) offsetIdx] * roughness * OFFSET_EPSILON * 0.7;
            double offsetY = offsets2[((int) offsetIdx + segments / 2) % offsets2.length] * roughness * OFFSET_EPSILON * 0.7;
            double px = cx + rx * Math.cos(angle) + offsetX;
            double py = cy + ry * Math.sin(angle) + offsetY;
            if (i == 0) {
                gc.moveTo(px, py);
            } else {
                gc.lineTo(px, py);
            }
        }
        gc.closePath();
        gc.stroke();
    }

    /**
     * 手绘菱形
     */
    public void drawDiamond(GraphicsContext gc, double x, double y, double w, double h,
                            double roughness, long seed) {
        double cx = x + w / 2;
        double cy = y + h / 2;
        drawLine(gc, cx, y, x + w, cy, roughness, seed);
        drawLine(gc, x + w, cy, cx, y + h, roughness, seed + 100);
        drawLine(gc, cx, y + h, x, cy, roughness, seed + 200);
        drawLine(gc, x, cy, cx, y, roughness, seed + 300);
    }

    /**
     * 手绘箭头
     */
    public void drawArrow(GraphicsContext gc, double x1, double y1, double x2, double y2,
                          double roughness, long seed) {
        drawLine(gc, x1, y1, x2, y2, roughness, seed);

        // 箭头头部
        double angle = Math.atan2(y2 - y1, x2 - x1);
        double headLen = 12;
        double headAngle = Math.PI / 6;

        double ax1 = x2 - headLen * Math.cos(angle - headAngle);
        double ay1 = y2 - headLen * Math.sin(angle - headAngle);
        double ax2 = x2 - headLen * Math.cos(angle + headAngle);
        double ay2 = y2 - headLen * Math.sin(angle + headAngle);

        drawLine(gc, x2, y2, ax1, ay1, roughness, seed + 500);
        drawLine(gc, x2, y2, ax2, ay2, roughness, seed + 600);
    }

    /**
     * 交叉线填充（hachure）
     */
    public void drawHachureFill(GraphicsContext gc, double x, double y, double w, double h,
                                double roughness, long seed) {
        double gap = 8;
        double angle = -Math.PI / 4; // 45度倾斜
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        double cx = x + w / 2;
        double cy = y + h / 2;
        double maxDim = Math.sqrt(w * w + h * h);

        double[] offsets = generateOffsets(seed, 200);

        int lineIndex = 0;
        for (double d = -maxDim / 2; d < maxDim / 2; d += gap) {
            // 计算填充线与矩形边界的交点
            double lx1 = cx + d * cos - maxDim * sin;
            double ly1 = cy + d * sin + maxDim * cos;
            double lx2 = cx + d * cos + maxDim * sin;
            double ly2 = cy + d * sin - maxDim * cos;

            // 裁剪到矩形内
            double[] clipped = clipLineToRect(lx1, ly1, lx2, ly2, x, y, w, h);
            if (clipped != null) {
                double offIdx = lineIndex % offsets.length;
                double off = offsets[(int) offIdx] * roughness * OFFSET_EPSILON * 0.3;
                gc.beginPath();
                gc.moveTo(clipped[0] + off, clipped[1] + off);
                gc.lineTo(clipped[2] + off, clipped[3] + off);
                gc.stroke();
                lineIndex++;
            }
        }
    }

    /**
     * 椭圆填充（hachure）
     */
    public void drawEllipseHachureFill(GraphicsContext gc, double cx, double cy, double rx, double ry,
                                       double roughness, long seed) {
        double gap = 8;
        double angle = -Math.PI / 4;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        double maxDim = Math.max(rx, ry) * 2;
        double[] offsets = generateOffsets(seed, 200);

        int lineIndex = 0;
        for (double d = -maxDim; d < maxDim; d += gap) {
            // 计算填充线与椭圆的交点
            double lx1 = cx + d * cos - maxDim * sin;
            double ly1 = cy + d * sin + maxDim * cos;
            double lx2 = cx + d * cos + maxDim * sin;
            double ly2 = cy + d * sin - maxDim * cos;

            double[] clipped = clipLineToEllipse(lx1, ly1, lx2, ly2, cx, cy, rx, ry);
            if (clipped != null) {
                double offIdx = lineIndex % offsets.length;
                double off = offsets[(int) offIdx] * roughness * OFFSET_EPSILON * 0.3;
                gc.beginPath();
                gc.moveTo(clipped[0] + off, clipped[1] + off);
                gc.lineTo(clipped[2] + off, clipped[3] + off);
                gc.stroke();
                lineIndex++;
            }
        }
    }

    // ---- 内部方法 ----

    /**
     * 手绘单条线段（带随机偏移的二次贝塞尔曲线）
     */
    private void roughLine(GraphicsContext gc, double x1, double y1, double x2, double y2,
                           double roughness, long seed) {
        double len = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
        double bowing = DEFAULT_BOWING * roughness * 1.5;

        // 中点偏移（弯曲效果）
        double midX = (x1 + x2) / 2;
        double midY = (y1 + y2) / 2;
        double[] midOffsets = generateOffsets(seed, 2);
        double midOffsetX = midOffsets[0] * bowing * len * 0.01;
        double midOffsetY = midOffsets[1] * bowing * len * 0.01;

        gc.beginPath();
        gc.moveTo(x1, y1);
        gc.quadraticCurveTo(midX + midOffsetX, midY + midOffsetY, x2, y2);
        gc.stroke();
    }

    /**
     * 基于种子生成确定性随机偏移数组
     */
    private double[] generateOffsets(long seed, int count) {
        Random rng = new Random(seed);
        double[] offsets = new double[count];
        for (int i = 0; i < count; i++) {
            offsets[i] = rng.nextDouble() * 2 - 1; // -1 到 1
        }
        return offsets;
    }

    /**
     * 将线段裁剪到矩形内（Cohen-Sutherland 简化版）
     */
    private double[] clipLineToRect(double x1, double y1, double x2, double y2,
                                    double rx, double ry, double rw, double rh) {
        // 使用参数化裁剪
        double t0 = 0, t1 = 1;
        double dx = x2 - x1, dy = y2 - y1;

        double[] edges = {-dx, dx, -dy, dy};
        double[] bounds = {x1 - rx, rx + rw - x1, y1 - ry, ry + rh - y1};

        for (int i = 0; i < 4; i++) {
            if (Math.abs(edges[i]) < 1e-10) {
                if (bounds[i] < 0) return null;
            } else {
                double t = bounds[i] / edges[i];
                if (edges[i] < 0) {
                    t0 = Math.max(t0, t);
                } else {
                    t1 = Math.min(t1, t);
                }
            }
        }

        if (t0 > t1) return null;

        return new double[]{
            x1 + t0 * dx, y1 + t0 * dy,
            x1 + t1 * dx, y1 + t1 * dy
        };
    }

    /**
     * 将线段裁剪到椭圆内
     */
    private double[] clipLineToEllipse(double x1, double y1, double x2, double y2,
                                       double cx, double cy, double rx, double ry) {
        // 参数化线段: P(t) = (x1 + t*(x2-x1), y1 + t*(y2-y1))
        // 椭圆方程: ((x-cx)/rx)^2 + ((y-cy)/ry)^2 = 1
        double dx = x2 - x1, dy = y2 - y1;
        double ox = x1 - cx, oy = y1 - cy;

        double a = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry);
        double b = 2 * (ox * dx / (rx * rx) + oy * dy / (ry * ry));
        double c = (ox * ox) / (rx * rx) + (oy * oy) / (ry * ry) - 1;

        double disc = b * b - 4 * a * c;
        if (disc < 0) return null;

        double sqrtDisc = Math.sqrt(disc);
        double t0 = (-b - sqrtDisc) / (2 * a);
        double t1 = (-b + sqrtDisc) / (2 * a);

        t0 = Math.max(0, t0);
        t1 = Math.min(1, t1);

        if (t0 > t1) return null;

        return new double[]{
            x1 + t0 * dx, y1 + t0 * dy,
            x1 + t1 * dx, y1 + t1 * dy
        };
    }
}
