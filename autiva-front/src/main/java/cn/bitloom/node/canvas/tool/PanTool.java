package cn.bitloom.node.canvas.tool;

import cn.bitloom.node.canvas.model.*;
import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;

public class PanTool implements CanvasTool {

    private double lastX;
    private double lastY;
    private boolean isPanning = false;

    @Override
    public String getName() { return "pan"; }

    @Override
    public Cursor getCursor() { return Cursor.OPEN_HAND; }

    @Override
    public void onMousePressed(MouseEvent e, CanvasScene scene) {
        lastX = e.getX();
        lastY = e.getY();
        isPanning = true;
    }

    @Override
    public void onMouseDragged(MouseEvent e, CanvasScene scene) {
        if (!isPanning) return;
        double dx = e.getX() - lastX;
        double dy = e.getY() - lastY;
        scene.setPanX(scene.getPanX() + dx);
        scene.setPanY(scene.getPanY() + dy);
        lastX = e.getX();
        lastY = e.getY();
    }

    @Override
    public void onMouseReleased(MouseEvent e, CanvasScene scene) {
        isPanning = false;
    }
}
