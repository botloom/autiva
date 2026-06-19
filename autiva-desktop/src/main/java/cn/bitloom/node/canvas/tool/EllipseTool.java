package cn.bitloom.node.canvas.tool;

import cn.bitloom.node.canvas.model.*;
import javafx.scene.input.MouseEvent;

public class EllipseTool implements CanvasTool {

    private Point startPoint;
    private EllipseElement currentElement;

    @Override
    public String getName() { return "ellipse"; }

    @Override
    public void onMousePressed(MouseEvent e, CanvasScene scene) {
        startPoint = scene.screenToScene(e.getX(), e.getY());
        currentElement = new EllipseElement();
        currentElement.setX(startPoint.x());
        currentElement.setY(startPoint.y());
        currentElement.setWidth(0);
        currentElement.setHeight(0);
        scene.addElement(currentElement);
    }

    @Override
    public void onMouseDragged(MouseEvent e, CanvasScene scene) {
        if (currentElement == null) return;
        Point current = scene.screenToScene(e.getX(), e.getY());
        double x = Math.min(startPoint.x(), current.x());
        double y = Math.min(startPoint.y(), current.y());
        double w = Math.abs(current.x() - startPoint.x());
        double h = Math.abs(current.y() - startPoint.y());
        currentElement.setX(x);
        currentElement.setY(y);
        currentElement.setWidth(w);
        currentElement.setHeight(h);
    }

    @Override
    public void onMouseReleased(MouseEvent e, CanvasScene scene) {
        if (currentElement != null && currentElement.getWidth() < 2 && currentElement.getHeight() < 2) {
            scene.removeElement(currentElement);
        }
        currentElement = null;
    }

    @Override
    public void onDeactivate(CanvasScene scene) {
        currentElement = null;
    }
}
