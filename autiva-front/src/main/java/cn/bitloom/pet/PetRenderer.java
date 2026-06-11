package cn.bitloom.pet;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

/**
 * 多植物 Canvas 渲染器，支持6种植物类型的5个生长阶段渲染。
 * 使用 Canvas 2D + 渐变/阴影模拟伪3D效果。
 */
public class PetRenderer {

    /**
     * 渲染植物到 Canvas。
     *
     * @param gc         GraphicsContext
     * @param type       植物类型
     * @param stage      生长阶段
     * @param progress   阶段内进度（0~1）
     * @param swayAngle  摇曳角度
     */
    public void render(GraphicsContext gc, PetType type, GrowthStage stage, double progress, double swayAngle) {
        double w = gc.getCanvas().getWidth();
        double h = gc.getCanvas().getHeight();
        gc.clearRect(0, 0, w, h);

        // 土壤不随植物摇曳，先绘制
        gc.save();
        gc.translate(w / 2, h * 0.88);
        drawSoil(gc);
        gc.restore();

        // 植物摇曳（整体放大1.4倍，使植物更旺盛）
        gc.save();
        gc.translate(w / 2, h * 0.88);
        gc.scale(1.4, 1.4);
        gc.rotate(swayAngle);

        switch (stage) {
            case SEED -> drawSeed(gc, type, progress);
            case SPROUT -> drawSprout(gc, type, progress);
            case SEEDLING -> drawSeedling(gc, type, progress);
            case YOUNG -> drawYoung(gc, type, progress);
            case MATURE -> drawMature(gc, type, progress);
        }

        gc.restore();
    }

    // ==================== 种子阶段（所有植物通用） ====================

    private void drawSeed(GraphicsContext gc, PetType type, double p) {
        double size = 6 + p * 8;
        RadialGradient g = new RadialGradient(0, 0, -size * 0.2, -size * 1.5, size,
                false, CycleMethod.NO_CYCLE, new Stop(0, Color.PERU), new Stop(1, Color.SADDLEBROWN));
        gc.setFill(g);
        gc.fillOval(-size, -size * 2, size * 2, size * 2);
        gc.setFill(Color.rgb(255, 255, 255, 0.3));
        gc.fillOval(-size * 0.5, -size * 1.7, size * 0.6, size * 0.5);

        // 破壳效果
        if (p > 0.6) {
            double crackP = (p - 0.6) / 0.4;
            gc.setStroke(getSproutColor(type));
            gc.setLineWidth(1.5);
            gc.strokeLine(-size * 0.2, -size * 2, -size * 0.1, -size * 2 - crackP * 8);
            gc.strokeLine(size * 0.2, -size * 2, size * 0.1, -size * 2 - crackP * 6);
        }
    }

    // ==================== 萌芽阶段（所有植物通用） ====================

    private void drawSprout(GraphicsContext gc, PetType type, double p) {
        double stemH = 12 + p * 30;
        double stemW = 3 + p * 2;

        // 茎
        gc.setFill(getStemColor(type));
        gc.fillRoundRect(-stemW / 2, -stemH, stemW, stemH, stemW, stemW);

        // 子叶
        double leafSize = 6 + p * 12;
        gc.save();
        gc.translate(-stemW / 2, -stemH + 3);
        gc.rotate(-20 - p * 15);
        drawLeafShape(gc, getLeafColor(type), leafSize);
        gc.restore();

        gc.save();
        gc.translate(stemW / 2, -stemH + 3);
        gc.rotate(20 + p * 15);
        drawLeafShape(gc, getLeafColor(type), leafSize);
        gc.restore();

        // 嫩芽
        double budSize = 2 + p * 4;
        gc.setFill(getSproutColor(type));
        gc.fillOval(-budSize, -stemH - budSize, budSize * 2, budSize * 2);

        // 水滴
        if (p > 0.3) {
            gc.setFill(Color.rgb(116, 185, 255, 0.6));
            gc.fillOval(leafSize * 0.6 - 2, -stemH + 4, 4, 5);
            gc.setFill(Color.rgb(255, 255, 255, 0.5));
            gc.fillOval(leafSize * 0.6 - 1, -stemH + 5, 2, 2);
        }
    }

    // ==================== 幼苗阶段（开始分化） ====================

    private void drawSeedling(GraphicsContext gc, PetType type, double p) {
        switch (type) {
            case SUNFLOWER -> drawSeedlingSunflower(gc, p);
            case CACTUS -> drawSeedlingCactus(gc, p);
            case IVY -> drawSeedlingIvy(gc, p);
            case BAMBOO -> drawSeedlingBamboo(gc, p);
            case ROSE -> drawSeedlingRose(gc, p);
            case BONSAI -> drawSeedlingBonsai(gc, p);
        }
    }

    private void drawSeedlingSunflower(GraphicsContext gc, double p) {
        double stemH = 42 + p * 30;
        double stemW = 4 + p * 2;
        gc.setFill(Color.FORESTGREEN);
        gc.fillRoundRect(-stemW / 2, -stemH, stemW, stemH, stemW, stemW);
        drawWideLeaf(gc, -stemW / 2, -stemH * 0.3, -42, 12 + p * 12, Color.GREEN);
        drawWideLeaf(gc, stemW / 2, -stemH * 0.4, 42, 10 + p * 10, Color.GREEN);
        drawWideLeaf(gc, -stemW / 2, -stemH * 0.5, -35, 9 + p * 9, Color.DARKGREEN);
        drawWideLeaf(gc, stemW / 2, -stemH * 0.6, 30, 8 + p * 8, Color.DARKGREEN);
        if (p > 0.4) {
            drawWideLeaf(gc, -stemW / 2, -stemH * 0.7, -28, 6 + p * 6, Color.GREEN);
        }
        double budSize = 6 + p * 10;
        if (p < 0.6) {
            gc.setFill(Color.YELLOWGREEN);
            gc.fillOval(-budSize, -stemH - budSize, budSize * 2, budSize * 2);
        } else {
            drawSunflowerHead(gc, stemH, (p - 0.6) / 0.4);
        }
    }

    private void drawSeedlingCactus(GraphicsContext gc, double p) {
        double bodyH = 20 + p * 35;
        double bodyW = 10 + p * 8;

        // 主体
        RadialGradient g = new RadialGradient(0, 0, -bodyW * 0.3, -bodyH * 0.5, bodyW * 1.5,
                false, CycleMethod.NO_CYCLE, new Stop(0, Color.rgb(120, 200, 80)), new Stop(1, Color.rgb(60, 140, 40)));
        gc.setFill(g);
        gc.fillRoundRect(-bodyW / 2, -bodyH, bodyW, bodyH, bodyW / 2, bodyW / 3);

        // 小侧臂
        if (p > 0.5) {
            double armH = 5 + (p - 0.5) * 16;
            double armW = 5 + (p - 0.5) * 6;
            gc.fillRoundRect(bodyW / 2, -bodyH * 0.5 - armH, armW, armH, armW / 2, armW / 3);
        }

        // 纵纹
        gc.setStroke(Color.rgb(40, 100, 30, 0.3));
        gc.setLineWidth(0.8);
        for (int i = 0; i < 3; i++) {
            double x = -bodyW * 0.3 + i * bodyW * 0.3;
            gc.strokeLine(x, -bodyH + bodyW / 3, x, -bodyW / 4);
        }

        // 刺
        if (p > 0.3) {
            drawCactusSpines(gc, bodyW, bodyH, p);
        }
    }

    private void drawSeedlingIvy(GraphicsContext gc, double p) {
        double stemH = 30 + p * 35;
        double stemW = 3 + p;

        // 弯曲的茎
        gc.setStroke(Color.FORESTGREEN);
        gc.setLineWidth(stemW);
        gc.beginPath();
        gc.moveTo(0, 0);
        gc.quadraticCurveTo(p * 15, -stemH * 0.5, p * 5, -stemH);
        gc.stroke();

        // 心形叶子
        double leafSize = 8 + p * 12;
        for (int i = 0; i < 4 + (int) (p * 4); i++) {
            double t = 0.2 + i * 0.12;
            double lx = p * 15 * t * (1 - t) * 2 + p * 5 * t * t;
            double ly = -stemH * t;
            gc.save();
            gc.translate(lx, ly);
            gc.rotate(i % 2 == 0 ? -30 : 30);
            drawHeartLeaf(gc, getLeafColor(PetType.IVY), leafSize * (0.5 + t * 0.5));
            gc.restore();
        }

        // 卷须
        if (p > 0.4) {
            gc.setStroke(Color.rgb(100, 180, 60));
            gc.setLineWidth(1);
            gc.beginPath();
            gc.moveTo(p * 5, -stemH);
            gc.bezierCurveTo(p * 5 + 8, -stemH - 5, p * 5 + 12, -stemH - 2, p * 5 + 10, -stemH - 8);
            gc.stroke();
        }
    }

    private void drawSeedlingBamboo(GraphicsContext gc, double p) {
        double stemH = 35 + p * 40;
        double stemW = 5 + p * 2;

        // 竹节
        int segments = 2 + (int) (p * 2);
        double segH = stemH / segments;
        for (int i = 0; i < segments; i++) {
            double y = -i * segH;
            gc.setFill(Color.rgb(100, 170, 60));
            gc.fillRoundRect(-stemW / 2, y - segH, stemW, segH, stemW / 2, 2);
            // 节环
            gc.setFill(Color.rgb(70, 130, 40));
            gc.fillRoundRect(-stemW / 2 - 1, y - 2, stemW + 2, 4, 2, 2);
        }

        // 竹叶
        if (p > 0.15) {
            drawBambooLeaves(gc, stemW, stemH, p, segments);
        }
    }

    private void drawSeedlingRose(GraphicsContext gc, double p) {
        double stemH = 35 + p * 30;
        double stemW = 3 + p;

        // 带刺的茎
        gc.setFill(Color.FORESTGREEN);
        gc.fillRoundRect(-stemW / 2, -stemH, stemW, stemH, stemW, stemW);

        // 刺
        if (p > 0.2) {
            gc.setFill(Color.rgb(80, 120, 40));
            for (int i = 0; i < 3; i++) {
                double y = -stemH * (0.3 + i * 0.2);
                gc.fillPolygon(
                        new double[]{stemW / 2, stemW / 2 + 4, stemW / 2},
                        new double[]{y - 2, y, y + 2}, 3
                );
            }
        }

        // 叶子
        drawWideLeaf(gc, -stemW / 2, -stemH * 0.35, -38, 10 + p * 10, Color.GREEN);
        drawWideLeaf(gc, stemW / 2, -stemH * 0.45, 38, 9 + p * 9, Color.GREEN);
        drawWideLeaf(gc, -stemW / 2, -stemH * 0.55, -30, 8 + p * 8, Color.DARKGREEN);

        // 花苞
        double budSize = 4 + p * 8;
        gc.setFill(Color.rgb(200, 60, 80));
        gc.fillOval(-budSize * 0.6, -stemH - budSize, budSize * 1.2, budSize * 1.5);
        gc.setFill(Color.rgb(180, 40, 60));
        gc.fillOval(-budSize * 0.3, -stemH - budSize * 0.8, budSize * 0.6, budSize);
    }

    private void drawSeedlingBonsai(GraphicsContext gc, double p) {
        double trunkH = 25 + p * 25;
        double trunkW = 6 + p * 3;

        // 弯曲树干
        gc.setFill(Color.rgb(120, 80, 40));
        gc.beginPath();
        gc.moveTo(-trunkW / 2, 0);
        gc.quadraticCurveTo(-trunkW, -trunkH * 0.5, -trunkW * 0.3, -trunkH);
        gc.lineTo(trunkW * 0.3, -trunkH);
        gc.quadraticCurveTo(trunkW, -trunkH * 0.5, trunkW / 2, 0);
        gc.fill();

        // 小枝
        if (p > 0.2) {
            gc.setStroke(Color.rgb(100, 65, 30));
            gc.setLineWidth(2);
            gc.beginPath();
            gc.moveTo(-trunkW * 0.2, -trunkH * 0.7);
            gc.quadraticCurveTo(-trunkW * 1.5, -trunkH * 0.9, -trunkW * 1.2, -trunkH * 1.1);
            gc.stroke();
        }

        // 小叶团
        if (p > 0.3) {
            gc.setFill(Color.rgb(60, 130, 40, 0.8));
            gc.fillOval(-trunkW * 2, -trunkH * 1.4, trunkW * 1.8, trunkW * 1.4);
            gc.fillOval(-trunkW * 0.5, -trunkH * 1.5, trunkW * 1.5, trunkW * 1.2);
            gc.fillOval(trunkW * 0.3, -trunkH * 1.3, trunkW * 1.2, trunkW * 1.0);
        }
    }

    // ==================== 少年阶段 ====================

    private void drawYoung(GraphicsContext gc, PetType type, double p) {
        switch (type) {
            case SUNFLOWER -> drawYoungSunflower(gc, p);
            case CACTUS -> drawYoungCactus(gc, p);
            case IVY -> drawYoungIvy(gc, p);
            case BAMBOO -> drawYoungBamboo(gc, p);
            case ROSE -> drawYoungRose(gc, p);
            case BONSAI -> drawYoungBonsai(gc, p);
        }
    }

    private void drawYoungSunflower(GraphicsContext gc, double p) {
        double stemH = 60 + p * 15;
        double stemW = 5 + p;
        gc.setFill(Color.FORESTGREEN);
        gc.fillRoundRect(-stemW / 2, -stemH, stemW, stemH, stemW, stemW);
        drawWideLeaf(gc, -stemW / 2, -stemH * 0.2, -45, 22, Color.GREEN);
        drawWideLeaf(gc, stemW / 2, -stemH * 0.3, 45, 20, Color.GREEN);
        drawWideLeaf(gc, -stemW / 2, -stemH * 0.4, -38, 16, Color.GREEN);
        drawWideLeaf(gc, stemW / 2, -stemH * 0.5, 35, 14, Color.GREEN);
        drawWideLeaf(gc, -stemW / 2, -stemH * 0.6, -30, 12, Color.DARKGREEN);
        drawWideLeaf(gc, stemW / 2, -stemH * 0.7, 28, 10, Color.DARKGREEN);
        drawSunflowerHead(gc, stemH, 0.5 + p * 0.5);
    }

    private void drawYoungCactus(GraphicsContext gc, double p) {
        double bodyH = 45 + p * 20;
        double bodyW = 16 + p * 8;

        // 主体
        RadialGradient g = new RadialGradient(0, 0, -bodyW * 0.3, -bodyH * 0.5, bodyW * 1.5,
                false, CycleMethod.NO_CYCLE, new Stop(0, Color.rgb(120, 200, 80)), new Stop(1, Color.rgb(60, 140, 40)));
        gc.setFill(g);
        gc.fillRoundRect(-bodyW / 2, -bodyH, bodyW, bodyH, bodyW / 2, bodyW / 3);

        // 左侧臂
        if (p > 0.2) {
            double armH = 10 + p * 12;
            double armW = 8 + p * 4;
            gc.fillRoundRect(-bodyW / 2 - armW, -bodyH * 0.6 - armH, armW, armH + bodyH * 0.15, armW / 2, armW / 3);
        }

        // 右侧臂
        if (p > 0.5) {
            double armH = 8 + (p - 0.5) * 14;
            double armW = 7 + (p - 0.5) * 4;
            gc.fillRoundRect(bodyW / 2, -bodyH * 0.45 - armH, armW, armH + bodyH * 0.1, armW / 2, armW / 3);
        }

        // 纵纹
        gc.setStroke(Color.rgb(40, 100, 30, 0.3));
        gc.setLineWidth(0.8);
        for (int i = 0; i < 5; i++) {
            double x = -bodyW * 0.4 + i * bodyW * 0.2;
            gc.strokeLine(x, -bodyH + bodyW / 3, x, -bodyW / 4);
        }

        drawCactusSpines(gc, bodyW, bodyH, 1.0);

        // 小花
        if (p > 0.5) {
            drawCactusFlower(gc, 0, -bodyH - 3, (p - 0.5) / 0.5);
        }
    }

    private void drawYoungIvy(GraphicsContext gc, double p) {
        double stemH = 50 + p * 25;

        // 多条攀爬茎
        gc.setStroke(Color.FORESTGREEN);
        gc.setLineWidth(3);
        gc.beginPath();
        gc.moveTo(0, 0);
        gc.bezierCurveTo(10, -stemH * 0.3, -8, -stemH * 0.6, 5, -stemH);
        gc.stroke();

        gc.setLineWidth(2);
        gc.beginPath();
        gc.moveTo(0, -stemH * 0.3);
        gc.bezierCurveTo(15, -stemH * 0.5, 20, -stemH * 0.7, 12, -stemH * 0.85);
        gc.stroke();

        gc.setLineWidth(1.5);
        gc.beginPath();
        gc.moveTo(-2, -stemH * 0.45);
        gc.bezierCurveTo(-12, -stemH * 0.6, -15, -stemH * 0.75, -8, -stemH * 0.88);
        gc.stroke();

        // 心形叶子
        for (int i = 0; i < 7 + (int) (p * 5); i++) {
            double t = 0.12 + i * 0.1;
            double lx = 10 * t * (1 - t) * 4 + (i % 3 - 1) * 6;
            double ly = -stemH * t;
            gc.save();
            gc.translate(lx, ly);
            gc.rotate((i % 2 == 0 ? -25 : 25) + i * 5);
            drawHeartLeaf(gc, Color.rgb(80, 180, 60), 7 + p * 7);
            gc.restore();
        }

        // 卷须
        gc.setStroke(Color.rgb(100, 180, 60));
        gc.setLineWidth(1);
        gc.beginPath();
        gc.moveTo(5, -stemH);
        gc.bezierCurveTo(12, -stemH - 8, 18, -stemH - 4, 15, -stemH - 12);
        gc.stroke();
        gc.beginPath();
        gc.moveTo(-8, -stemH * 0.88);
        gc.bezierCurveTo(-14, -stemH * 0.92, -16, -stemH * 0.86, -14, -stemH * 0.95);
        gc.stroke();
    }

    private void drawYoungBamboo(GraphicsContext gc, double p) {
        double stemH = 55 + p * 25;
        double stemW = 6 + p * 2;

        int segments = 4 + (int) (p * 2);
        double segH = stemH / segments;
        for (int i = 0; i < segments; i++) {
            double y = -i * segH;
            gc.setFill(Color.rgb(100, 170, 60));
            gc.fillRoundRect(-stemW / 2, y - segH, stemW, segH, stemW / 2, 2);
            gc.setFill(Color.rgb(70, 130, 40));
            gc.fillRoundRect(-stemW / 2 - 1, y - 2, stemW + 2, 4, 2, 2);
        }

        drawBambooLeaves(gc, stemW, stemH, 1.0, segments);
    }

    private void drawYoungRose(GraphicsContext gc, double p) {
        double stemH = 50 + p * 20;
        double stemW = 4 + p;

        // 茎
        gc.setFill(Color.FORESTGREEN);
        gc.fillRoundRect(-stemW / 2, -stemH, stemW, stemH, stemW, stemW);

        // 刺
        gc.setFill(Color.rgb(80, 120, 40));
        for (int i = 0; i < 4; i++) {
            double y = -stemH * (0.2 + i * 0.18);
            gc.fillPolygon(
                    new double[]{stemW / 2, stemW / 2 + 5, stemW / 2},
                    new double[]{y - 2, y, y + 2}, 3
            );
        }

        // 叶子
        drawWideLeaf(gc, -stemW / 2, -stemH * 0.25, -42, 16, Color.GREEN);
        drawWideLeaf(gc, stemW / 2, -stemH * 0.35, 42, 14, Color.GREEN);
        drawWideLeaf(gc, -stemW / 2, -stemH * 0.45, -35, 12, Color.GREEN);
        drawWideLeaf(gc, stemW / 2, -stemH * 0.55, 32, 11, Color.DARKGREEN);
        drawWideLeaf(gc, -stemW / 2, -stemH * 0.65, -28, 9, Color.DARKGREEN);

        // 半开玫瑰
        drawRoseHead(gc, stemH, 0.3 + p * 0.4);
    }

    private void drawYoungBonsai(GraphicsContext gc, double p) {
        double trunkH = 40 + p * 20;
        double trunkW = 8 + p * 4;

        // 弯曲树干
        gc.setFill(Color.rgb(120, 80, 40));
        gc.beginPath();
        gc.moveTo(-trunkW / 2, 0);
        gc.quadraticCurveTo(-trunkW * 1.2, -trunkH * 0.4, -trunkW * 0.3, -trunkH);
        gc.lineTo(trunkW * 0.3, -trunkH);
        gc.quadraticCurveTo(trunkW * 1.2, -trunkH * 0.4, trunkW / 2, 0);
        gc.fill();

        // 分枝
        gc.setStroke(Color.rgb(100, 65, 30));
        gc.setLineWidth(3);
        gc.beginPath();
        gc.moveTo(-trunkW * 0.2, -trunkH * 0.7);
        gc.quadraticCurveTo(-trunkW * 2, -trunkH * 0.9, -trunkW * 1.5, -trunkH * 1.2);
        gc.stroke();
        gc.beginPath();
        gc.moveTo(trunkW * 0.2, -trunkH * 0.6);
        gc.quadraticCurveTo(trunkW * 1.8, -trunkH * 0.8, trunkW * 1.3, -trunkH * 1.1);
        gc.stroke();

        // 叶团
        gc.setFill(Color.rgb(50, 120, 35, 0.85));
        gc.fillOval(-trunkW * 2.5, -trunkH * 1.6, trunkW * 2.2, trunkW * 1.7);
        gc.fillOval(-trunkW * 0.8, -trunkH * 1.7, trunkW * 2, trunkW * 1.5);
        gc.fillOval(trunkW * 0.5, -trunkH * 1.5, trunkW * 2, trunkW * 1.4);
        gc.fillOval(-trunkW * 1.5, -trunkH * 1.4, trunkW * 1.5, trunkW * 1.2);
        // 浅色叶团
        gc.setFill(Color.rgb(70, 145, 45, 0.8));
        gc.fillOval(-trunkW * 2, -trunkH * 1.5, trunkW * 1.8, trunkW * 1.4);
        gc.fillOval(trunkW * 0.8, -trunkH * 1.4, trunkW * 1.6, trunkW * 1.3);

        // 苔藓
        if (p > 0.4) {
            gc.setFill(Color.rgb(80, 150, 50, 0.5));
            gc.fillOval(-trunkW * 0.8, -3, trunkW * 1.6, 5);
        }
    }

    // ==================== 成熟阶段 ====================

    private void drawMature(GraphicsContext gc, PetType type, double p) {
        switch (type) {
            case SUNFLOWER -> drawMatureSunflower(gc, p);
            case CACTUS -> drawMatureCactus(gc, p);
            case IVY -> drawMatureIvy(gc, p);
            case BAMBOO -> drawMatureBamboo(gc, p);
            case ROSE -> drawMatureRose(gc, p);
            case BONSAI -> drawMatureBonsai(gc, p);
        }
    }

    private void drawMatureSunflower(GraphicsContext gc, double p) {
        double stemH = 72 + p * 10;
        double stemW = 5 + p;
        gc.setFill(Color.FORESTGREEN);
        gc.fillRoundRect(-stemW / 2, -stemH, stemW, stemH, stemW, stemW);
        drawWideLeaf(gc, -stemW / 2, -stemH * 0.15, -48, 24, Color.GREEN);
        drawWideLeaf(gc, stemW / 2, -stemH * 0.25, 48, 22, Color.GREEN);
        drawWideLeaf(gc, -stemW / 2, -stemH * 0.35, -42, 18, Color.GREEN);
        drawWideLeaf(gc, stemW / 2, -stemH * 0.45, 40, 16, Color.GREEN);
        drawWideLeaf(gc, -stemW / 2, -stemH * 0.55, -35, 14, Color.DARKGREEN);
        drawWideLeaf(gc, stemW / 2, -stemH * 0.65, 32, 12, Color.DARKGREEN);
        drawWideLeaf(gc, -stemW / 2, -stemH * 0.75, -28, 10, Color.DARKGREEN);
        drawSunflowerHead(gc, stemH, 1.0);
        drawSmile(gc, stemH);
    }

    private void drawMatureCactus(GraphicsContext gc, double p) {
        double bodyH = 60 + p * 15;
        double bodyW = 22 + p * 6;

        // 主体
        RadialGradient g = new RadialGradient(0, 0, -bodyW * 0.3, -bodyH * 0.5, bodyW * 1.5,
                false, CycleMethod.NO_CYCLE, new Stop(0, Color.rgb(120, 200, 80)), new Stop(1, Color.rgb(60, 140, 40)));
        gc.setFill(g);
        gc.fillRoundRect(-bodyW / 2, -bodyH, bodyW, bodyH, bodyW / 2, bodyW / 3);

        // 双侧臂
        double armH = 18 + p * 8;
        double armW = 10 + p * 4;
        gc.fillRoundRect(-bodyW / 2 - armW, -bodyH * 0.55 - armH, armW, armH + bodyH * 0.15, armW / 2, armW / 3);
        gc.fillRoundRect(bodyW / 2, -bodyH * 0.4 - armH * 0.8, armW, armH * 0.8 + bodyH * 0.1, armW / 2, armW / 3);

        // 纵纹
        gc.setStroke(Color.rgb(40, 100, 30, 0.3));
        gc.setLineWidth(0.8);
        for (int i = 0; i < 7; i++) {
            double x = -bodyW * 0.4 + i * bodyW * 0.13;
            gc.strokeLine(x, -bodyH + bodyW / 3, x, -bodyW / 4);
        }

        drawCactusSpines(gc, bodyW, bodyH, 1.0);
        drawCactusFlower(gc, 0, -bodyH - 3, 1.0);

        // 笑脸
        double faceY = -bodyH * 0.5;
        gc.setFill(Color.rgb(40, 80, 25));
        gc.fillOval(-4, faceY - 2, 3, 3.5);
        gc.fillOval(2, faceY - 2, 3, 3.5);
        gc.setFill(Color.rgb(255, 255, 255, 0.5));
        gc.fillOval(-3.5, faceY - 1.5, 1.2, 1.2);
        gc.fillOval(2.5, faceY - 1.5, 1.2, 1.2);
        gc.setStroke(Color.rgb(40, 80, 25));
        gc.setLineWidth(1.5);
        gc.beginPath();
        gc.arc(0, faceY + 2, 5, 3, 200, 140);
        gc.stroke();
    }

    private void drawMatureIvy(GraphicsContext gc, double p) {
        double stemH = 65 + p * 15;

        // 多条攀爬茎
        gc.setStroke(Color.FORESTGREEN);
        gc.setLineWidth(3.5);
        gc.beginPath();
        gc.moveTo(0, 0);
        gc.bezierCurveTo(12, -stemH * 0.3, -10, -stemH * 0.6, 6, -stemH);
        gc.stroke();

        gc.setLineWidth(2.5);
        gc.beginPath();
        gc.moveTo(2, -stemH * 0.25);
        gc.bezierCurveTo(18, -stemH * 0.45, 22, -stemH * 0.65, 14, -stemH * 0.85);
        gc.stroke();

        gc.setLineWidth(2);
        gc.beginPath();
        gc.moveTo(-2, -stemH * 0.4);
        gc.bezierCurveTo(-15, -stemH * 0.55, -18, -stemH * 0.75, -10, -stemH * 0.9);
        gc.stroke();

        gc.setLineWidth(1.5);
        gc.beginPath();
        gc.moveTo(4, -stemH * 0.5);
        gc.bezierCurveTo(20, -stemH * 0.65, 25, -stemH * 0.8, 18, -stemH * 0.92);
        gc.stroke();

        // 密集心形叶子
        for (int i = 0; i < 14 + (int) (p * 6); i++) {
            double t = 0.08 + i * 0.065;
            double lx = (i % 3 - 1) * 9 + Math.sin(i * 1.5) * 6;
            double ly = -stemH * t;
            gc.save();
            gc.translate(lx, ly);
            gc.rotate((i % 2 == 0 ? -20 : 20) + i * 3);
            drawHeartLeaf(gc, Color.rgb(70 + i * 2, 170 + i, 50), 8 + p * 5);
            gc.restore();
        }

        // 卷须
        gc.setStroke(Color.rgb(100, 180, 60));
        gc.setLineWidth(1);
        gc.beginPath();
        gc.moveTo(6, -stemH);
        gc.bezierCurveTo(14, -stemH - 10, 20, -stemH - 5, 17, -stemH - 15);
        gc.stroke();
        gc.beginPath();
        gc.moveTo(-10, -stemH * 0.9);
        gc.bezierCurveTo(-18, -stemH * 0.95, -22, -stemH * 0.88, -20, -stemH);
        gc.stroke();
    }

    private void drawMatureBamboo(GraphicsContext gc, double p) {
        double stemH = 70 + p * 15;
        double stemW = 7 + p * 2;

        int segments = 6 + (int) (p * 2);
        double segH = stemH / segments;
        for (int i = 0; i < segments; i++) {
            double y = -i * segH;
            gc.setFill(Color.rgb(100, 170, 60));
            gc.fillRoundRect(-stemW / 2, y - segH, stemW, segH, stemW / 2, 2);
            gc.setFill(Color.rgb(70, 130, 40));
            gc.fillRoundRect(-stemW / 2 - 1, y - 2, stemW + 2, 4, 2, 2);
        }

        drawBambooLeaves(gc, stemW, stemH, 1.0, segments);
    }

    private void drawMatureRose(GraphicsContext gc, double p) {
        double stemH = 60 + p * 12;
        double stemW = 4 + p;

        // 茎
        gc.setFill(Color.FORESTGREEN);
        gc.fillRoundRect(-stemW / 2, -stemH, stemW, stemH, stemW, stemW);

        // 刺
        gc.setFill(Color.rgb(80, 120, 40));
        for (int i = 0; i < 5; i++) {
            double y = -stemH * (0.15 + i * 0.15);
            gc.fillPolygon(
                    new double[]{stemW / 2, stemW / 2 + 5, stemW / 2},
                    new double[]{y - 2, y, y + 2}, 3
            );
        }

        // 叶子
        drawWideLeaf(gc, -stemW / 2, -stemH * 0.2, -45, 18, Color.GREEN);
        drawWideLeaf(gc, stemW / 2, -stemH * 0.3, 45, 16, Color.GREEN);
        drawWideLeaf(gc, -stemW / 2, -stemH * 0.4, -38, 14, Color.GREEN);
        drawWideLeaf(gc, stemW / 2, -stemH * 0.5, 35, 12, Color.GREEN);
        drawWideLeaf(gc, -stemW / 2, -stemH * 0.6, -30, 11, Color.DARKGREEN);
        drawWideLeaf(gc, stemW / 2, -stemH * 0.7, 28, 9, Color.DARKGREEN);

        // 盛开玫瑰
        drawRoseHead(gc, stemH, 1.0);
    }

    private void drawMatureBonsai(GraphicsContext gc, double p) {
        double trunkH = 50 + p * 12;
        double trunkW = 10 + p * 4;

        // 弯曲树干
        gc.setFill(Color.rgb(120, 80, 40));
        gc.beginPath();
        gc.moveTo(-trunkW / 2, 0);
        gc.quadraticCurveTo(-trunkW * 1.5, -trunkH * 0.4, -trunkW * 0.3, -trunkH);
        gc.lineTo(trunkW * 0.3, -trunkH);
        gc.quadraticCurveTo(trunkW * 1.5, -trunkH * 0.4, trunkW / 2, 0);
        gc.fill();

        // 多分枝
        gc.setStroke(Color.rgb(100, 65, 30));
        gc.setLineWidth(3);
        gc.beginPath();
        gc.moveTo(-trunkW * 0.2, -trunkH * 0.7);
        gc.quadraticCurveTo(-trunkW * 2.5, -trunkH * 0.9, -trunkW * 2, -trunkH * 1.3);
        gc.stroke();
        gc.beginPath();
        gc.moveTo(trunkW * 0.2, -trunkH * 0.6);
        gc.quadraticCurveTo(trunkW * 2.2, -trunkH * 0.8, trunkW * 1.8, -trunkH * 1.2);
        gc.stroke();
        gc.setLineWidth(2);
        gc.beginPath();
        gc.moveTo(0, -trunkH * 0.85);
        gc.quadraticCurveTo(trunkW * 0.5, -trunkH * 1.2, 0, -trunkH * 1.4);
        gc.stroke();

        // 密叶团
        Color leafColor = Color.rgb(45, 115, 30, 0.9);
        Color leafLight = Color.rgb(65, 140, 40, 0.85);
        Color leafHighlight = Color.rgb(85, 160, 55, 0.8);
        gc.setFill(leafColor);
        gc.fillOval(-trunkW * 3, -trunkH * 1.7, trunkW * 2.5, trunkW * 2);
        gc.fillOval(trunkW * 1, -trunkH * 1.6, trunkW * 2.2, trunkW * 1.8);
        gc.fillOval(-trunkW * 0.8, -trunkH * 1.8, trunkW * 1.8, trunkW * 1.5);
        gc.fillOval(-trunkW * 1.8, -trunkH * 1.5, trunkW * 1.6, trunkW * 1.3);
        gc.setFill(leafLight);
        gc.fillOval(-trunkW * 2.5, -trunkH * 1.6, trunkW * 2, trunkW * 1.6);
        gc.fillOval(trunkW * 1.3, -trunkH * 1.5, trunkW * 1.8, trunkW * 1.4);
        gc.fillOval(-trunkW * 0.3, -trunkH * 1.7, trunkW * 1.5, trunkW * 1.2);
        gc.setFill(leafHighlight);
        gc.fillOval(-trunkW * 2, -trunkH * 1.5, trunkW * 1.5, trunkW * 1.2);
        gc.fillOval(trunkW * 1.5, -trunkH * 1.4, trunkW * 1.3, trunkW * 1.1);

        // 苔藓
        gc.setFill(Color.rgb(80, 150, 50, 0.5));
        gc.fillOval(-trunkW, -4, trunkW * 2, 6);
        gc.fillOval(-trunkW * 0.5, -2, trunkW, 4);
    }

    // ==================== 共用组件 ====================

    private void drawSoil(GraphicsContext gc) {
        gc.setFill(Color.SANDYBROWN);
        gc.fillOval(-28, -6, 56, 12);
        gc.setFill(Color.rgb(139, 119, 42, 0.5));
        gc.fillOval(-20, -4, 40, 8);
    }

    private void drawLeafShape(GraphicsContext gc, Color color, double size) {
        RadialGradient g = new RadialGradient(0, 0, -size * 0.2, -size * 0.2, size,
                false, CycleMethod.NO_CYCLE, new Stop(0, color.brighter()), new Stop(1, color));
        gc.setFill(g);
        gc.fillOval(-size * 0.3, -size * 0.6, size * 0.6, size * 1.2);
        gc.setStroke(color.darker());
        gc.setLineWidth(0.8);
        gc.strokeLine(0, -size * 0.5, 0, size * 0.5);
    }

    private void drawWideLeaf(GraphicsContext gc, double x, double y, double angle, double size, Color color) {
        gc.save();
        gc.translate(x, y);
        gc.rotate(angle);
        RadialGradient g = new RadialGradient(0, 0, -size * 0.1, 0, size,
                false, CycleMethod.NO_CYCLE, new Stop(0, color.brighter()), new Stop(1, color));
        gc.setFill(g);
        gc.beginPath();
        gc.moveTo(0, 0);
        gc.quadraticCurveTo(size * 0.5, -size * 0.3, size, 0);
        gc.quadraticCurveTo(size * 0.5, size * 0.3, 0, 0);
        gc.fill();
        gc.setStroke(color.darker());
        gc.setLineWidth(0.8);
        gc.strokeLine(0, 0, size * 0.9, 0);
        gc.restore();
    }

    private void drawHeartLeaf(GraphicsContext gc, Color color, double size) {
        gc.setFill(color);
        gc.beginPath();
        double s = size * 0.5;
        gc.moveTo(0, s * 0.3);
        gc.bezierCurveTo(-s, -s * 0.3, -s * 0.5, -s, 0, -s * 0.4);
        gc.bezierCurveTo(s * 0.5, -s, s, -s * 0.3, 0, s * 0.3);
        gc.fill();
        // 叶脉
        gc.setStroke(color.darker());
        gc.setLineWidth(0.6);
        gc.strokeLine(0, -s * 0.3, 0, s * 0.2);
    }

    private void drawSunflowerHead(GraphicsContext gc, double stemH, double bloomP) {
        double centerR = 6 + bloomP * 8;
        int petalCount = (int) (6 + bloomP * 10);
        double petalR = 3 + bloomP * 5;
        double petalDist = centerR + petalR * 0.8;

        gc.setFill(Color.GOLD);
        for (int i = 0; i < petalCount; i++) {
            double angle = Math.toRadians(i * 360.0 / petalCount);
            double px = Math.cos(angle) * petalDist;
            double py = -stemH - centerR + Math.sin(angle) * petalDist;
            gc.fillOval(px - petalR, py - petalR, petalR * 2, petalR * 2);
        }

        if (bloomP > 0.5) {
            gc.setFill(Color.YELLOW);
            for (int i = 0; i < petalCount; i++) {
                double angle = Math.toRadians(i * 360.0 / petalCount + 360.0 / petalCount / 2);
                double outerR = petalDist + 2;
                double px = Math.cos(angle) * outerR;
                double py = -stemH - centerR + Math.sin(angle) * outerR;
                gc.fillOval(px - petalR * 0.7, py - petalR * 0.7, petalR * 1.4, petalR * 1.4);
            }
        }

        RadialGradient centerGrad = new RadialGradient(0, 0, -centerR * 0.2, -stemH - centerR * 0.8, centerR,
                false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.DARKGOLDENROD),
                new Stop(0.7, Color.rgb(139, 90, 0)),
                new Stop(1, Color.rgb(100, 60, 0)));
        gc.setFill(centerGrad);
        gc.fillOval(-centerR, -stemH - centerR * 2, centerR * 2, centerR * 2);

        gc.setFill(Color.rgb(80, 50, 0, 0.4));
        for (int i = 0; i < 8; i++) {
            double dotAngle = Math.toRadians(i * 45);
            double dotR = centerR * 0.5;
            gc.fillOval(Math.cos(dotAngle) * dotR - 1, -stemH - centerR + Math.sin(dotAngle) * dotR - centerR - 1, 2.5, 2.5);
        }
    }

    private void drawSmile(GraphicsContext gc, double stemH) {
        double centerR = 14;
        double faceY = -stemH - centerR;
        gc.setFill(Color.rgb(60, 30, 0));
        gc.fillOval(-4, faceY - 2, 3, 3.5);
        gc.fillOval(2, faceY - 2, 3, 3.5);
        gc.setFill(Color.rgb(255, 255, 255, 0.6));
        gc.fillOval(-3.5, faceY - 1.5, 1.2, 1.2);
        gc.fillOval(2.5, faceY - 1.5, 1.2, 1.2);
        gc.setStroke(Color.rgb(60, 30, 0));
        gc.setLineWidth(1.5);
        gc.beginPath();
        gc.arc(0, faceY + 1, 5, 3.5, 200, 140);
        gc.stroke();
        gc.setFill(Color.rgb(255, 150, 150, 0.25));
        gc.fillOval(-8, faceY + 1, 5, 3);
        gc.fillOval(4, faceY + 1, 5, 3);
    }

    private void drawCactusSpines(GraphicsContext gc, double bodyW, double bodyH, double p) {
        gc.setStroke(Color.rgb(200, 200, 150));
        gc.setLineWidth(1);
        int spineCount = (int) (4 + p * 4);
        for (int i = 0; i < spineCount; i++) {
            double y = -bodyH * (0.15 + i * 0.1);
            // 左刺
            gc.strokeLine(-bodyW / 2, y, -bodyW / 2 - 4, y - 2);
            // 右刺
            gc.strokeLine(bodyW / 2, y, bodyW / 2 + 4, y - 2);
        }
    }

    private void drawCactusFlower(GraphicsContext gc, double x, double y, double p) {
        double size = 3 + p * 5;
        int petals = 5;
        gc.setFill(Color.rgb(255, 100, 120));
        for (int i = 0; i < petals; i++) {
            double angle = Math.toRadians(i * 360.0 / petals);
            double px = x + Math.cos(angle) * size * 0.6;
            double py = y + Math.sin(angle) * size * 0.6;
            gc.fillOval(px - size * 0.4, py - size * 0.4, size * 0.8, size * 0.8);
        }
        gc.setFill(Color.YELLOW);
        gc.fillOval(x - size * 0.25, y - size * 0.25, size * 0.5, size * 0.5);
    }

    private void drawBambooLeaves(GraphicsContext gc, double stemW, double stemH, double p, int segments) {
        gc.setFill(Color.rgb(80, 160, 50));
        for (int i = 1; i < segments; i++) {
            double y = -i * (stemH / segments);
            // 左叶
            gc.save();
            gc.translate(-stemW / 2, y);
            gc.rotate(-20 - i * 5);
            gc.beginPath();
            gc.moveTo(0, 0);
            gc.quadraticCurveTo(-8, -3, -15, 0);
            gc.quadraticCurveTo(-8, 2, 0, 0);
            gc.fill();
            gc.restore();
            // 右叶
            gc.save();
            gc.translate(stemW / 2, y);
            gc.rotate(20 + i * 5);
            gc.beginPath();
            gc.moveTo(0, 0);
            gc.quadraticCurveTo(8, -3, 15, 0);
            gc.quadraticCurveTo(8, 2, 0, 0);
            gc.fill();
            gc.restore();
        }
    }

    private void drawRoseHead(GraphicsContext gc, double stemH, double bloomP) {
        double centerR = 4 + bloomP * 6;
        int layers = (int) (2 + bloomP * 3);

        // 花瓣层
        for (int layer = layers; layer >= 1; layer--) {
            double layerR = centerR * (0.5 + layer * 0.25);
            int petalCount = 5 + layer;
            double petalSize = layerR * 0.5;
            Color petalColor = Color.rgb(
                    180 + layer * 15,
                    30 + layer * 10,
                    50 + layer * 10
            );
            gc.setFill(petalColor);
            for (int i = 0; i < petalCount; i++) {
                double angle = Math.toRadians(i * 360.0 / petalCount + layer * 15);
                double px = Math.cos(angle) * layerR * 0.6;
                double py = -stemH - centerR + Math.sin(angle) * layerR * 0.6;
                gc.fillOval(px - petalSize, py - petalSize, petalSize * 2, petalSize * 2);
            }
        }

        // 花心
        gc.setFill(Color.rgb(200, 180, 50));
        gc.fillOval(-centerR * 0.3, -stemH - centerR * 1.3, centerR * 0.6, centerR * 0.6);
    }

    // ==================== 颜色辅助 ====================

    private Color getSproutColor(PetType type) {
        return switch (type) {
            case CACTUS -> Color.rgb(120, 200, 80);
            case ROSE -> Color.rgb(200, 80, 100);
            case BAMBOO -> Color.rgb(100, 180, 60);
            case IVY -> Color.rgb(80, 180, 60);
            case BONSAI -> Color.rgb(100, 160, 50);
            default -> Color.FORESTGREEN;
        };
    }

    private Color getStemColor(PetType type) {
        return switch (type) {
            case BAMBOO -> Color.rgb(100, 170, 60);
            case ROSE -> Color.FORESTGREEN;
            case BONSAI -> Color.rgb(120, 80, 40);
            default -> Color.FORESTGREEN;
        };
    }

    private Color getLeafColor(PetType type) {
        return switch (type) {
            case CACTUS -> Color.rgb(100, 190, 70);
            case IVY -> Color.rgb(70, 170, 50);
            case BAMBOO -> Color.rgb(80, 160, 50);
            case ROSE -> Color.GREEN;
            case BONSAI -> Color.rgb(60, 130, 40);
            default -> Color.LIMEGREEN;
        };
    }
}
