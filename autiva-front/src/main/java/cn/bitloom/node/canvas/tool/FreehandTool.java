package cn.bitloom.node.canvas.tool;

import cn.bitloom.node.canvas.model.*;
import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;

public class FreehandTool implements CanvasTool {

    private FreehandElement currentElement;

    @Override
    public String getName() { return "freehand"; }

    @Override
    public Cursor getCursor() { return Cursor.CROSSHAIR; }

    @Override
    public void onMousePressed(MouseEvent e, CanvasScene scene) {
        Point scenePoint = scene.screenToScene(e.getX(), e.getY());
        currentElement = new FreehandElement();
        currentElement.setX(scenePoint.x());
        currentElement.setY(scenePoint.y());
        currentElement.addPoint(scenePoint);
        scene.addElement(currentElement);
    }

    @Override
    public void onMouseDragged(MouseEvent e, CanvasScene scene) {
        if (currentElement == null) return;
        Point scenePoint = scene.screenToScene(e.getX(), e.getY());
        currentElement.addPoint(scenePoint);
    }

    @Override
    public void onMouseReleased(MouseEvent e, CanvasScene scene) {
        if (currentElement != null && currentElement.getPoints().size() < 2) {
            scene.removeElement(currentElement);
        }
        currentElement = null;
    }

    @Override
    public void onDeactivate(CanvasScene scene) {
        currentElement = null;
    }
}
