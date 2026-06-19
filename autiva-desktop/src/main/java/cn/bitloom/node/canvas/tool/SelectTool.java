package cn.bitloom.node.canvas.tool;

import cn.bitloom.node.canvas.model.*;
import javafx.geometry.Bounds;
import javafx.scene.Cursor;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class SelectTool implements CanvasTool {

    private static final double HANDLE_HIT_SIZE = 10.0;
    private static final double ENDPOINT_HIT_SIZE = 12.0;
    private static final double SNAP_THRESHOLD = 15.0;

    private final List<CanvasElement> selectedElements = new ArrayList<>();
    private Point dragStart;
    private Point elementStartPos;
    private boolean isDragging = false;
    private boolean isResizing = false;
    private boolean isDraggingEndpoint = false;
    private ResizeHandle activeHandle = null;
    private EndpointType activeEndpoint = null;
    private double startWidth;
    private double startHeight;

    // 8个缩放手柄位置
    private enum ResizeHandle {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        MIDDLE_LEFT, MIDDLE_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }

    // 线条/箭头端点类型
    private enum EndpointType {
        START, END
    }

    @Override
    public String getName() { return "select"; }

    @Override
    public Cursor getCursor() { return Cursor.DEFAULT; }

    @Override
    public void onMousePressed(MouseEvent e, CanvasScene scene) {
        Point scenePoint = scene.screenToScene(e.getX(), e.getY());

        if (selectedElements.size() == 1) {
            CanvasElement selected = selectedElements.get(0);

            // 优先检查线条/箭头的端点
            if (selected.isConnector()) {
                EndpointType ep = hitTestEndpoint(scenePoint, selected);
                if (ep != null) {
                    activeEndpoint = ep;
                    isDraggingEndpoint = true;
                    dragStart = scenePoint;
                    elementStartPos = new Point(selected.getX(), selected.getY());
                    startWidth = selected.getWidth();
                    startHeight = selected.getHeight();
                    return;
                }
            }

            // 检查是否点击了缩放手柄（非连接型元素）
            if (!selected.isConnector()) {
                ResizeHandle handle = hitTestHandle(scenePoint, selected);
                if (handle != null) {
                    activeHandle = handle;
                    isResizing = true;
                    dragStart = scenePoint;
                    elementStartPos = new Point(selected.getX(), selected.getY());
                    startWidth = selected.getWidth();
                    startHeight = selected.getHeight();
                    return;
                }
            }
        }

        // 查找点击的元素
        CanvasElement hit = null;
        for (int i = scene.getElements().size() - 1; i >= 0; i--) {
            CanvasElement el = scene.getElements().get(i);
            if (el.isVisible() && el.contains(scenePoint)) {
                hit = el;
                break;
            }
        }

        if (hit != null) {
            if (!selectedElements.contains(hit)) {
                selectedElements.clear();
                selectedElements.add(hit);
            }
            dragStart = scenePoint;
            elementStartPos = new Point(hit.getX(), hit.getY());
            isDragging = true;
        } else {
            selectedElements.clear();
        }
    }

    @Override
    public void onMouseDragged(MouseEvent e, CanvasScene scene) {
        if (selectedElements.isEmpty()) return;
        Point scenePoint = scene.screenToScene(e.getX(), e.getY());
        double dx = scenePoint.x() - dragStart.x();
        double dy = scenePoint.y() - dragStart.y();

        if (isDraggingEndpoint && selectedElements.size() == 1) {
            CanvasElement el = selectedElements.get(0);
            dragEndpoint(el, scenePoint, scene);
        } else if (isResizing && selectedElements.size() == 1) {
            CanvasElement el = selectedElements.get(0);
            resizeElement(el, dx, dy);
            scene.updateConnectedLines(el);
        } else if (isDragging) {
            for (CanvasElement el : selectedElements) {
                el.setX(elementStartPos.x() + dx);
                el.setY(elementStartPos.y() + dy);
                scene.updateConnectedLines(el);
            }
        }
    }

    @Override
    public void onMouseReleased(MouseEvent e, CanvasScene scene) {
        isDragging = false;
        isResizing = false;
        isDraggingEndpoint = false;
        activeHandle = null;
        activeEndpoint = null;
    }

    @Override
    public void onMouseMoved(MouseEvent e, CanvasScene scene) {
        // 更新光标样式由 CanvasView 通过 getCursorForPosition 管理
    }

    /**
     * 根据鼠标位置返回对应的光标样式
     */
    public Cursor getCursorForPosition(MouseEvent e, CanvasScene scene) {
        if (selectedElements.size() == 1) {
            CanvasElement selected = selectedElements.get(0);
            Point scenePoint = scene.screenToScene(e.getX(), e.getY());

            // 线条/箭头端点光标
            if (selected.isConnector()) {
                EndpointType ep = hitTestEndpoint(scenePoint, selected);
                if (ep != null) {
                    return Cursor.CROSSHAIR;
                }
            }

            // 缩放手柄光标（非连接型元素）
            if (!selected.isConnector()) {
                ResizeHandle handle = hitTestHandle(scenePoint, selected);
                if (handle != null) {
                    return switch (handle) {
                        case TOP_LEFT, BOTTOM_RIGHT -> Cursor.NW_RESIZE;
                        case TOP_RIGHT, BOTTOM_LEFT -> Cursor.NE_RESIZE;
                        case TOP_CENTER, BOTTOM_CENTER -> Cursor.N_RESIZE;
                        case MIDDLE_LEFT, MIDDLE_RIGHT -> Cursor.E_RESIZE;
                    };
                }
            }
        }
        // 检查是否悬停在元素上
        Point scenePoint = scene.screenToScene(e.getX(), e.getY());
        for (int i = scene.getElements().size() - 1; i >= 0; i--) {
            if (scene.getElements().get(i).isVisible() && scene.getElements().get(i).contains(scenePoint)) {
                return Cursor.MOVE;
            }
        }
        return Cursor.DEFAULT;
    }

    @Override
    public void onKeyDown(KeyEvent e, CanvasScene scene) {
        if (e.getCode() == javafx.scene.input.KeyCode.DELETE ||
            e.getCode() == javafx.scene.input.KeyCode.BACK_SPACE) {
            for (CanvasElement el : new ArrayList<>(selectedElements)) {
                scene.removeElement(el);
            }
            selectedElements.clear();
        }
    }

    @Override
    public void onDeactivate(CanvasScene scene) {
        selectedElements.clear();
    }

    public List<CanvasElement> getSelectedElements() { return selectedElements; }

    /**
     * 获取指定元素的手柄位置（场景坐标）
     * 对于连接型元素（线条/箭头），返回两个端点
     */
    public double[][] getHandlePositions(CanvasElement element) {
        if (element.isConnector()) {
            // 线条/箭头只有两个端点手柄
            return new double[][] {
                {element.getX(), element.getY()},                           // 起点
                {element.getX() + element.getWidth(), element.getY() + element.getHeight()}  // 终点
            };
        }

        Bounds bounds = element.getBounds();
        double x = bounds.getMinX(), y = bounds.getMinY();
        double w = bounds.getWidth(), h = bounds.getHeight();

        return new double[][] {
            {x, y},             // TOP_LEFT
            {x + w/2, y},       // TOP_CENTER
            {x + w, y},         // TOP_RIGHT
            {x, y + h/2},       // MIDDLE_LEFT
            {x + w, y + h/2},   // MIDDLE_RIGHT
            {x, y + h},         // BOTTOM_LEFT
            {x + w/2, y + h},   // BOTTOM_CENTER
            {x + w, y + h},     // BOTTOM_RIGHT
        };
    }

    /**
     * 检测点击位置是否命中缩放手柄（仅非连接型元素）
     */
    private ResizeHandle hitTestHandle(Point point, CanvasElement element) {
        double[][] handles = getHandlePositions(element);
        ResizeHandle[] handleTypes = ResizeHandle.values();

        for (int i = 0; i < handles.length; i++) {
            double hx = handles[i][0], hy = handles[i][1];
            if (Math.abs(point.x() - hx) < HANDLE_HIT_SIZE &&
                Math.abs(point.y() - hy) < HANDLE_HIT_SIZE) {
                return handleTypes[i];
            }
        }
        return null;
    }

    /**
     * 检测点击位置是否命中线条/箭头的端点
     */
    private EndpointType hitTestEndpoint(Point point, CanvasElement element) {
        double startX = element.getX(), startY = element.getY();
        double endX = element.getX() + element.getWidth(), endY = element.getY() + element.getHeight();

        if (Math.abs(point.x() - startX) < ENDPOINT_HIT_SIZE &&
            Math.abs(point.y() - startY) < ENDPOINT_HIT_SIZE) {
            return EndpointType.START;
        }
        if (Math.abs(point.x() - endX) < ENDPOINT_HIT_SIZE &&
            Math.abs(point.y() - endY) < ENDPOINT_HIT_SIZE) {
            return EndpointType.END;
        }
        return null;
    }

    /**
     * 拖拽线条/箭头的端点
     */
    private void dragEndpoint(CanvasElement el, Point scenePoint, CanvasScene scene) {
        // 吸附到最近的锚点（排除自身）
        Point snapPoint = scene.findNearestAnchor(scenePoint, SNAP_THRESHOLD, el);
        Point targetPoint = snapPoint != null ? snapPoint : scenePoint;

        if (activeEndpoint == EndpointType.START) {
            double endX = elementStartPos.x() + startWidth;
            double endY = elementStartPos.y() + startHeight;
            el.setX(targetPoint.x());
            el.setY(targetPoint.y());
            el.setWidth(endX - targetPoint.x());
            el.setHeight(endY - targetPoint.y());
            // 更新连接信息
            if (snapPoint != null) {
                // 查找吸附到哪个元素
                updateEndpointConnection(el, scenePoint, scene, true);
            } else {
                el.setStartConnectedElementId(null);
                el.setStartConnectedAnchorIndex(-1);
            }
        } else {
            el.setWidth(targetPoint.x() - el.getX());
            el.setHeight(targetPoint.y() - el.getY());
            // 更新连接信息
            if (snapPoint != null) {
                updateEndpointConnection(el, scenePoint, scene, false);
            } else {
                el.setEndConnectedElementId(null);
                el.setEndConnectedAnchorIndex(-1);
            }
        }
    }

    /**
     * 更新端点连接信息
     */
    private void updateEndpointConnection(CanvasElement el, Point rawPoint, CanvasScene scene, boolean isStart) {
        double minDist = SNAP_THRESHOLD;
        CanvasElement connectedEl = null;
        int anchorIdx = -1;

        for (CanvasElement other : scene.getElements()) {
            if (other == el || !other.isVisible() || other.isConnector()) continue;
            List<Point> anchors = other.getAnchorPoints();
            for (int i = 0; i < anchors.size(); i++) {
                double dist = rawPoint.distanceTo(anchors.get(i));
                if (dist < minDist) {
                    minDist = dist;
                    connectedEl = other;
                    anchorIdx = i;
                }
            }
        }

        if (connectedEl != null) {
            if (isStart) {
                el.setStartConnectedElementId(connectedEl.getId());
                el.setStartConnectedAnchorIndex(anchorIdx);
            } else {
                el.setEndConnectedElementId(connectedEl.getId());
                el.setEndConnectedAnchorIndex(anchorIdx);
            }
        }
    }

    /**
     * 根据手柄类型调整元素大小
     */
    private void resizeElement(CanvasElement el, double dx, double dy) {
        double newX = elementStartPos.x();
        double newY = elementStartPos.y();
        double newW = startWidth;
        double newH = startHeight;

        switch (activeHandle) {
            case BOTTOM_RIGHT:
                newW = startWidth + dx;
                newH = startHeight + dy;
                break;
            case BOTTOM_LEFT:
                newX = elementStartPos.x() + dx;
                newW = startWidth - dx;
                newH = startHeight + dy;
                break;
            case TOP_RIGHT:
                newY = elementStartPos.y() + dy;
                newW = startWidth + dx;
                newH = startHeight - dy;
                break;
            case TOP_LEFT:
                newX = elementStartPos.x() + dx;
                newY = elementStartPos.y() + dy;
                newW = startWidth - dx;
                newH = startHeight - dy;
                break;
            case MIDDLE_RIGHT:
                newW = startWidth + dx;
                break;
            case MIDDLE_LEFT:
                newX = elementStartPos.x() + dx;
                newW = startWidth - dx;
                break;
            case BOTTOM_CENTER:
                newH = startHeight + dy;
                break;
            case TOP_CENTER:
                newY = elementStartPos.y() + dy;
                newH = startHeight - dy;
                break;
        }

        // 最小尺寸
        if (newW < 5) { newW = 5; if (activeHandle == ResizeHandle.MIDDLE_LEFT || activeHandle == ResizeHandle.TOP_LEFT || activeHandle == ResizeHandle.BOTTOM_LEFT) newX = elementStartPos.x() + startWidth - 5; }
        if (newH < 5) { newH = 5; if (activeHandle == ResizeHandle.TOP_LEFT || activeHandle == ResizeHandle.TOP_RIGHT || activeHandle == ResizeHandle.TOP_CENTER) newY = elementStartPos.y() + startHeight - 5; }

        el.setX(newX);
        el.setY(newY);
        el.setWidth(newW);
        el.setHeight(newH);
    }
}
