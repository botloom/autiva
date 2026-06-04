package cn.bitloom.node.canvas.tool;

import cn.bitloom.node.canvas.model.*;
import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;

public class TextTool implements CanvasTool {

    @Override
    public String getName() { return "text"; }

    @Override
    public Cursor getCursor() { return Cursor.TEXT; }

    @Override
    public void onMousePressed(MouseEvent e, CanvasScene scene) {
        Point scenePoint = scene.screenToScene(e.getX(), e.getY());
        TextElement textElement = new TextElement();
        textElement.setX(scenePoint.x());
        textElement.setY(scenePoint.y());
        textElement.setWidth(200);
        textElement.setHeight(30);
        textElement.setText("双击编辑");
        scene.addElement(textElement);
    }
}
