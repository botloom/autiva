package cn.bitloom.node.canvas.tool;

import cn.bitloom.node.canvas.model.CanvasElement;
import cn.bitloom.node.canvas.model.CanvasScene;
import cn.bitloom.node.canvas.model.LineElement;
import cn.bitloom.node.canvas.model.Point;
import javafx.scene.input.MouseEvent;

import java.util.List;

public class LineTool implements CanvasTool {

    private static final double SNAP_THRESHOLD = 15.0; // 吸附阈值（场景坐标）

    private Point startPoint;
    private LineElement currentElement;
    private CanvasElement startConnectedElement;
    private int startConnectedAnchorIndex;

    @Override
    public String getName() { return "line"; }

    @Override
    public void onMousePressed(MouseEvent e, CanvasScene scene) {
        Point rawPoint = scene.screenToScene(e.getX(), e.getY());
        // 吸附到最近的锚点
        AnchorSnapResult snap = findAnchorSnap(rawPoint, scene, null);
        startPoint = snap.snappedPoint;
        startConnectedElement = snap.connectedElement;
        startConnectedAnchorIndex = snap.anchorIndex;

        currentElement = new LineElement();
        currentElement.setX(startPoint.x());
        currentElement.setY(startPoint.y());
        currentElement.setWidth(0);
        currentElement.setHeight(0);
        // 记录起点连接信息
        if (startConnectedElement != null) {
            currentElement.setStartConnectedElementId(startConnectedElement.getId());
            currentElement.setStartConnectedAnchorIndex(startConnectedAnchorIndex);
        }
        scene.addElement(currentElement);
    }

    @Override
    public void onMouseDragged(MouseEvent e, CanvasScene scene) {
        if (currentElement == null) return;
        Point rawPoint = scene.screenToScene(e.getX(), e.getY());
        // 吸附到最近的锚点（排除自身）
        AnchorSnapResult snap = findAnchorSnap(rawPoint, scene, currentElement);
        Point endPoint = snap.snappedPoint;

        currentElement.setWidth(endPoint.x() - startPoint.x());
        currentElement.setHeight(endPoint.y() - startPoint.y());
        // 更新终点连接信息
        if (snap.connectedElement != null) {
            currentElement.setEndConnectedElementId(snap.connectedElement.getId());
            currentElement.setEndConnectedAnchorIndex(snap.anchorIndex);
        } else {
            currentElement.setEndConnectedElementId(null);
            currentElement.setEndConnectedAnchorIndex(-1);
        }
    }

    @Override
    public void onMouseReleased(MouseEvent e, CanvasScene scene) {
        if (currentElement != null) {
            double len = Math.sqrt(currentElement.getWidth() * currentElement.getWidth()
                + currentElement.getHeight() * currentElement.getHeight());
            if (len < 2) {
                scene.removeElement(currentElement);
            }
        }
        currentElement = null;
    }

    @Override
    public void onDeactivate(CanvasScene scene) {
        currentElement = null;
    }

    /**
     * 查找最近的锚点并返回吸附结果
     */
    private AnchorSnapResult findAnchorSnap(Point rawPoint, CanvasScene scene, CanvasElement excludeElement) {
        AnchorSnapResult result = new AnchorSnapResult();
        result.snappedPoint = rawPoint;
        result.connectedElement = null;
        result.anchorIndex = -1;

        double minDist = SNAP_THRESHOLD;
        for (CanvasElement el : scene.getElements()) {
            if (el == excludeElement || !el.isVisible()) continue;
            List<Point> anchors = el.getAnchorPoints();
            for (int i = 0; i < anchors.size(); i++) {
                double dist = rawPoint.distanceTo(anchors.get(i));
                if (dist < minDist) {
                    minDist = dist;
                    result.snappedPoint = anchors.get(i);
                    result.connectedElement = el;
                    result.anchorIndex = i;
                }
            }
        }
        return result;
    }

    /** 锚点吸附结果 */
    private static class AnchorSnapResult {
        Point snappedPoint;
        CanvasElement connectedElement;
        int anchorIndex;
    }
}
