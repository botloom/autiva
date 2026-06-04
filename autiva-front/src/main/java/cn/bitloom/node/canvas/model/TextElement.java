package cn.bitloom.node.canvas.model;

import cn.bitloom.node.canvas.render.ElementRenderer;
import javafx.scene.canvas.GraphicsContext;
import javafx.geometry.Bounds;
import javafx.scene.shape.Rectangle;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TextElement extends CanvasElement {

    private String text = "";
    private double fontSize = 20;

    @Override
    public String getType() {
        return "text";
    }

    @Override
    public boolean contains(Point point) {
        return point.x() >= getX() && point.x() <= getX() + getWidth()
            && point.y() >= getY() && point.y() <= getY() + getHeight();
    }

    @Override
    public Bounds getBounds() {
        return new Rectangle(getX(), getY(), getWidth(), getHeight()).getBoundsInLocal();
    }

    @Override
    public void accept(ElementRenderer renderer, GraphicsContext gc) {
        renderer.render(this, gc);
    }

}
