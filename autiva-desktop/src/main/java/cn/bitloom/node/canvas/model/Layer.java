package cn.bitloom.node.canvas.model;

import javafx.beans.property.*;
import lombok.Getter;

/**
 * 图层模型。
 * 每个图层包含可见性、透明度和锁定状态。
 */
public class Layer {

    // Getters and setters
    @Getter
    private final String id;
    private final StringProperty name = new SimpleStringProperty();
    private final BooleanProperty visible = new SimpleBooleanProperty(true);
    private final BooleanProperty locked = new SimpleBooleanProperty(false);
    private final DoubleProperty opacity = new SimpleDoubleProperty(1.0);

    public Layer() {
        this.id = "layer_" + System.nanoTime() % 100000;
        this.name.set("Layer");
    }

    public Layer(String id, String name) {
        this.id = id;
        this.name.set(name);
    }

    public Layer(String name) {
        this.id = "layer_" + System.nanoTime() % 100000;
        this.name.set(name);
    }

    public String getName() { return name.get(); }
    public void setName(String name) { this.name.set(name); }
    public StringProperty nameProperty() { return name; }
    public boolean isVisible() { return visible.get(); }
    public void setVisible(boolean visible) { this.visible.set(visible); }
    public BooleanProperty visibleProperty() { return visible; }
    public boolean isLocked() { return locked.get(); }
    public void setLocked(boolean locked) { this.locked.set(locked); }
    public BooleanProperty lockedProperty() { return locked; }
    public double getOpacity() { return opacity.get(); }
    public void setOpacity(double opacity) { this.opacity.set(Math.max(0, Math.min(1, opacity))); }
    public DoubleProperty opacityProperty() { return opacity; }

    @Override
    public String toString() {
        return getName();
    }
}
