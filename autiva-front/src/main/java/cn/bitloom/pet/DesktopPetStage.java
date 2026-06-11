package cn.bitloom.pet;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * 桌面萌宠窗口，透明置顶，支持拖动、单击恢复、右键菜单、悬停提示。
 */
@Slf4j
public class DesktopPetStage {

    private static final int PET_SIZE = 120;

    private final PetRenderer renderer = new PetRenderer();
    private final PetStateManager stateManager;

    private Stage stage;
    private Canvas canvas;
    private Timeline swayTimeline;
    private double swayAngle = 0;

    // 拖动状态
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean isDragging = false;

    // 点击检测
    private long pressTime;

    // 恢复主窗口回调
    private Runnable onRestoreCallback;

    // 退出回调
    private Runnable onExitCallback;

    public DesktopPetStage(PetStateManager stateManager) {
        this.stateManager = stateManager;
    }

    /**
     * 创建并配置萌宠窗口（不显示）。
     *
     * @param owner 主窗口，用于绑定 owner 避免任务栏出现独立图标
     */
    public Stage create(Stage owner) {
        canvas = new Canvas(PET_SIZE, PET_SIZE);

        StackPane root = new StackPane(canvas);
        root.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(root, PET_SIZE, PET_SIZE);
        scene.setFill(Color.TRANSPARENT);

        stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.initOwner(owner);
        stage.setAlwaysOnTop(true);
        stage.setScene(scene);
        stage.setTitle("Autiva Pet");

        // 恢复位置
        PetState state = stateManager.getState();
        if (state.getPosX() >= 0 && state.getPosY() >= 0) {
            stage.setX(state.getPosX());
            stage.setY(state.getPosY());
        } else {
            var screen = javafx.stage.Screen.getPrimary().getVisualBounds();
            stage.setX(screen.getMaxX() - PET_SIZE - 30);
            stage.setY(screen.getMaxY() - PET_SIZE - 60);
        }

        setupInteraction(root);
        setupContextMenu(root);
        setupTooltip(root);
        startSwayAnimation();

        return stage;
    }

    /**
     * 显示萌宠窗口。
     */
    public void show() {
        if (stage != null) {
            renderPet();
            stage.show();
        }
    }

    /**
     * 隐藏萌宠窗口。
     */
    public void hide() {
        if (stage != null) {
            stage.hide();
        }
    }

    /**
     * 刷新渲染（当状态变化时调用）。
     */
    public void refresh() {
        renderPet();
        updateTooltip();
    }

    /**
     * 设置恢复主窗口回调。
     */
    public void setOnRestore(Runnable callback) {
        this.onRestoreCallback = callback;
    }

    /**
     * 设置退出回调。
     */
    public void setOnExit(Runnable callback) {
        this.onExitCallback = callback;
    }

    /**
     * 停止所有动画。
     */
    public void stop() {
        if (swayTimeline != null) {
            swayTimeline.stop();
        }
    }

    // ==================== 交互 ====================

    private void setupInteraction(StackPane root) {
        root.setCursor(javafx.scene.Cursor.HAND);

        root.setOnMousePressed(e -> {
            dragOffsetX = e.getScreenX() - stage.getX();
            dragOffsetY = e.getScreenY() - stage.getY();
            isDragging = false;
            pressTime = System.currentTimeMillis();
            e.consume();
        });

        root.setOnMouseDragged(e -> {
            double newX = e.getScreenX() - dragOffsetX;
            double newY = e.getScreenY() - dragOffsetY;
            stage.setX(newX);
            stage.setY(newY);
            isDragging = true;
            e.consume();
        });

        root.setOnMouseReleased(e -> {
            long elapsed = System.currentTimeMillis() - pressTime;
            // 短按且未拖动 = 单击
            if (!isDragging && elapsed < 300) {
                restoreMainWindow();
            }
            // 保存位置
            stateManager.savePosition(stage.getX(), stage.getY());
            e.consume();
        });
    }

    private void setupContextMenu(StackPane root) {
        ContextMenu menu = new ContextMenu();

        MenuItem restoreItem = new MenuItem("恢复主窗口");
        restoreItem.setOnAction(e -> restoreMainWindow());

        MenuItem detailItem = new MenuItem("生长详情");
        detailItem.setOnAction(e -> showGrowthDetail());

        MenuItem resetItem = new MenuItem("重置萌宠");
        resetItem.setOnAction(e -> {
            stateManager.reset();
            refresh();
        });

        MenuItem exitItem = new MenuItem("退出");
        exitItem.setOnAction(e -> {
            if (onExitCallback != null) {
                onExitCallback.run();
            }
        });

        menu.getItems().addAll(restoreItem, detailItem, resetItem, new javafx.scene.control.SeparatorMenuItem(), exitItem);

        root.setOnContextMenuRequested(e -> {
            menu.show(stage, e.getScreenX(), e.getScreenY());
            e.consume();
        });
    }

    private void setupTooltip(StackPane root) {
        Tooltip tooltip = new Tooltip();
        tooltip.setStyle("-fx-font-size: 12px; -fx-padding: 6 10;");
        Tooltip.install(root, tooltip);
        updateTooltipText(tooltip);

        // 每次显示时更新
        root.setOnMouseEntered(e -> updateTooltipText(tooltip));
    }

    private void updateTooltip() {
        if (stage != null && stage.getScene() != null) {
            StackPane root = (StackPane) stage.getScene().getRoot();
            Tooltip tooltip = (Tooltip) root.getProperties().get("TOOLTIP");
            if (tooltip != null) {
                updateTooltipText(tooltip);
            }
        }
    }

    private void updateTooltipText(Tooltip tooltip) {
        PetState state = stateManager.getState();
        GrowthStage stage = state.getGrowthStage();
        String text = String.format("%s · %s\n消息数: %d · 进度: %.0f%%",
                state.getPetType().getLabel(),
                stage.getLabel(),
                state.getTotalMessages(),
                state.getGrowthProgress() * 100);
        tooltip.setText(text);
    }

    // ==================== 动画 ====================

    private void startSwayAnimation() {
        swayTimeline = new Timeline(new KeyFrame(Duration.millis(50), e -> {
            swayAngle = 3 * Math.sin(System.currentTimeMillis() / 800.0);
            renderPet();
        }));
        swayTimeline.setCycleCount(Animation.INDEFINITE);
        swayTimeline.play();
    }

    // ==================== 渲染 ====================

    private void renderPet() {
        if (canvas == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        PetState state = stateManager.getState();
        renderer.render(gc, state.getPetType(), state.getGrowthStage(), state.getStageProgress(), swayAngle);
    }

    // ==================== 操作 ====================

    private void restoreMainWindow() {
        if (onRestoreCallback != null) {
            onRestoreCallback.run();
        }
    }

    private void showGrowthDetail() {
        PetState state = stateManager.getState();
        GrowthStage stage = state.getGrowthStage();
        String detail = String.format("""
                🌱 萌宠生长详情
                
                植物类型: %s (%s)
                生长阶段: %s
                消息总数: %d
                全局进度: %.1f%%
                阶段进度: %.1f%%
                
                风格维度:
                消息长度: %.2f
                代码比例: %.2f
                Emoji率: %.2f
                消息频率: %.2f
                词汇多样性: %.2f
                """,
                state.getPetType().getLabel(), state.getPetType().getStyleDesc(),
                stage.getLabel(),
                state.getTotalMessages(),
                state.getGrowthProgress() * 100,
                state.getStageProgress() * 100,
                state.getAvgLength(),
                state.getCodeRatio(),
                state.getEmojiRate(),
                state.getFrequency(),
                state.getDiversity());
        log.info(detail);
    }
}
