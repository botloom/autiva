package cn.bitloom.node.canvas;

import cn.bitloom.node.canvas.model.CanvasScene;
import cn.bitloom.node.canvas.model.Point;
import cn.bitloom.node.canvas.render.CanvasRenderer;
import cn.bitloom.node.canvas.tool.CanvasTool;
import cn.bitloom.node.canvas.tool.LineTool;
import cn.bitloom.node.canvas.tool.ArrowTool;
import cn.bitloom.node.canvas.tool.PanTool;
import cn.bitloom.node.canvas.tool.SelectTool;
import javafx.animation.AnimationTimer;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;

import java.util.function.Consumer;

/**
 * 画布视图组件。
 * 封装 JavaFX Canvas，处理鼠标/键盘事件，委托给当前工具。
 * 使用 AnimationTimer 驱动渲染循环。
 */
public class CanvasView {

    private final Canvas canvas;
    private final CanvasScene scene;
    private final CanvasRenderer renderer;
    private CanvasTool currentTool;
    private SelectTool selectTool;
    private CanvasTool panTool = new PanTool();
    private boolean spacePressed = false;
    private Consumer<Boolean> onSelectionChange;
    private Runnable onElementCreated;

    public CanvasView(CanvasScene scene) {
        this.scene = scene;
        this.canvas = new Canvas();

        this.renderer = new CanvasRenderer(canvas, scene);

        setupEventHandlers();
        startRenderLoop();

        // 画布尺寸跟随父容器
        canvas.widthProperty().addListener(obs -> renderer.markDirty());
        canvas.heightProperty().addListener(obs -> renderer.markDirty());

        // Canvas 不参与父容器布局计算，避免影响 StackPane/AnchorPane 尺寸
        canvas.setManaged(false);
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public CanvasRenderer getRenderer() {
        return renderer;
    }

    public CanvasScene getScene() {
        return scene;
    }

    public void setTool(CanvasTool tool) {
        if (currentTool != null) {
            currentTool.onDeactivate(scene);
        }
        currentTool = tool;
        if (currentTool != null) {
            currentTool.onActivate(scene);
            canvas.setCursor(currentTool.getCursor());
        }
        // 工具切换时通知选中状态变化
        notifySelectionChange();
        // 线条/箭头工具激活时显示锚点
        boolean isLineTool = tool instanceof LineTool
                          || tool instanceof ArrowTool;
        renderer.setShowAnchors(isLineTool);
        if (!isLineTool) {
            renderer.setSnappedAnchor(null);
        }
    }

    public void setSelectTool(SelectTool selectTool) {
        this.selectTool = selectTool;
    }

    public CanvasTool getCurrentTool() {
        return currentTool;
    }

    /**
     * 设置选中状态变化回调
     */
    public void setOnSelectionChange(Consumer<Boolean> callback) {
        this.onSelectionChange = callback;
    }

    /**
     * 设置元素创建回调
     */
    public void setOnElementCreated(Runnable callback) {
        this.onElementCreated = callback;
    }

    /**
     * 通知选中状态变化
     */
    private void notifySelectionChange() {
        if (onSelectionChange != null && currentTool instanceof SelectTool selectTool) {
            onSelectionChange.accept(!selectTool.getSelectedElements().isEmpty());
        } else if (onSelectionChange != null) {
            onSelectionChange.accept(false);
        }
    }

    /**
     * 绑定到父容器尺寸
     */
    public void bindToParent(javafx.scene.layout.Region parent) {
        // 使用监听器设置 Canvas 尺寸，避免与布局冲突
        parent.widthProperty().addListener((obs, oldVal, newVal) -> {
            canvas.setWidth(newVal.doubleValue());
        });
        parent.heightProperty().addListener((obs, oldVal, newVal) -> {
            canvas.setHeight(newVal.doubleValue());
        });
        // 立即设置当前尺寸
        canvas.setWidth(parent.getWidth());
        canvas.setHeight(parent.getHeight());

        // Canvas 位置跟随父容器
        canvas.layoutXProperty().bind(parent.layoutXProperty());
        canvas.layoutYProperty().bind(parent.layoutYProperty());
    }

    // ---- 事件处理 ----

    private void setupEventHandlers() {
        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnMouseReleased(this::handleMouseReleased);
        canvas.setOnMouseMoved(this::handleMouseMoved);
        canvas.setOnScroll(this::handleScroll);

        canvas.setFocusTraversable(true);
        canvas.setOnKeyPressed(this::handleKeyPressed);
        canvas.setOnKeyReleased(this::handleKeyReleased);
    }

    private void handleMousePressed(MouseEvent e) {
        canvas.requestFocus();
        CanvasTool tool = spacePressed ? panTool : currentTool;
        if (tool != null) {
            tool.onMousePressed(e, scene);
            renderer.markDirty();
            notifySelectionChange();
        }
    }

    private void handleMouseDragged(MouseEvent e) {
        CanvasTool tool = spacePressed ? panTool : currentTool;
        if (tool != null) {
            tool.onMouseDragged(e, scene);
            renderer.markDirty();
        }
    }

    private void handleMouseReleased(MouseEvent e) {
        CanvasTool tool = spacePressed ? panTool : currentTool;
        if (tool != null) {
            int sizeBefore = scene.getElements().size();
            tool.onMouseReleased(e, scene);
            int sizeAfter = scene.getElements().size();

            // 绘图工具完成后，自动选中刚绘制的元素
            if (tool != selectTool && tool != panTool && selectTool != null
                && sizeAfter > 0 && sizeAfter >= sizeBefore) {
                var lastElement = scene.getElements().get(sizeAfter - 1);
                double elSize = Math.sqrt(lastElement.getWidth() * lastElement.getWidth()
                    + lastElement.getHeight() * lastElement.getHeight());
                if (elSize >= 2) {
                    selectTool.getSelectedElements().clear();
                    selectTool.getSelectedElements().add(lastElement);
                    // 先通知 Controller 应用当前属性到新元素，再同步属性
                    // 顺序很重要：先 apply 再 sync，否则 sync 会用默认属性覆盖 ViewModel
                    if (onElementCreated != null) {
                        onElementCreated.run();
                    }
                    notifySelectionChange();
                }
            }

            renderer.markDirty();
        }
    }

    private void handleMouseMoved(MouseEvent e) {
        if (currentTool != null) {
            currentTool.onMouseMoved(e, scene);
        }
        // 选择工具时，根据手柄位置更新光标
        if (currentTool instanceof SelectTool st) {
            Cursor cursor = st.getCursorForPosition(e, scene);
            canvas.setCursor(cursor);
        }
        // 线条/箭头工具时，显示吸附锚点指示器
        if (currentTool instanceof LineTool
            || currentTool instanceof ArrowTool) {
            Point rawPoint = scene.screenToScene(e.getX(), e.getY());
            Point snap = scene.findNearestAnchor(rawPoint, 15.0, null);
            renderer.setSnappedAnchor(snap);
        }
    }

    private void handleScroll(ScrollEvent e) {
        if (!e.isControlDown()) return; // 只有 Ctrl+滚轮才缩放

        // 以鼠标位置为中心缩放
        double oldZoom = scene.getZoom();
        double delta = e.getDeltaY() > 0 ? 1.1 : 0.9;
        double newZoom = Math.max(0.1, Math.min(3.0, oldZoom * delta));

        // 保持鼠标指向的场景坐标不变
        double mouseX = e.getX();
        double mouseY = e.getY();
        double sceneX = (mouseX - scene.getPanX()) / oldZoom;
        double sceneY = (mouseY - scene.getPanY()) / oldZoom;

        scene.setZoom(newZoom);
        scene.setPanX(mouseX - sceneX * newZoom);
        scene.setPanY(mouseY - sceneY * newZoom);

        renderer.markDirty();
    }

    private void handleKeyPressed(KeyEvent e) {
        if (e.getCode() == javafx.scene.input.KeyCode.SPACE) {
            spacePressed = true;
            canvas.setCursor(javafx.scene.Cursor.OPEN_HAND);
            e.consume();
        }
        if (currentTool != null) {
            currentTool.onKeyDown(e, scene);
            renderer.markDirty();
        }
    }

    private void handleKeyReleased(KeyEvent e) {
        if (e.getCode() == javafx.scene.input.KeyCode.SPACE) {
            spacePressed = false;
            if (currentTool != null) {
                canvas.setCursor(currentTool.getCursor());
            }
            e.consume();
        }
        if (currentTool != null) {
            currentTool.onKeyUp(e, scene);
            notifySelectionChange();
        }
    }

    // ---- 渲染循环 ----

    private void startRenderLoop() {
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                renderer.render();
            }
        };
        timer.start();
    }
}
