package cn.bitloom.vm;

import cn.bitloom.node.canvas.model.*;
import cn.bitloom.node.canvas.tool.*;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * 画布页面视图模型。
 * 管理画布状态、工具切换和元素属性。
 */
@Component
@Getter
public class CanvasPageViewModel {

    private final CanvasScene scene = new CanvasScene();

    // 当前工具
    private final StringProperty currentToolName = new SimpleStringProperty("select");

    // 工具实例
    private final SelectTool selectTool = new SelectTool();

    // 缩放显示
    private final DoubleProperty zoomLevel = new SimpleDoubleProperty(100);

    // 选中元素属性
    private final StringProperty selectedStrokeColor = new SimpleStringProperty("#000000");
    private final StringProperty selectedFillColor = new SimpleStringProperty("transparent");
    private final DoubleProperty selectedStrokeWidth = new SimpleDoubleProperty(2.0);
    private final DoubleProperty selectedRoughness = new SimpleDoubleProperty(1.0);
    private final DoubleProperty selectedOpacity = new SimpleDoubleProperty(1.0);
    private final StringProperty selectedLineStyle = new SimpleStringProperty("solid");
    private final StringProperty selectedArrowStyle = new SimpleStringProperty("none");
    private final DoubleProperty selectedCornerRadius = new SimpleDoubleProperty(0);
    private final StringProperty selectedText = new SimpleStringProperty("");
    private final BooleanProperty hasSelection = new SimpleBooleanProperty(false);

    public CanvasPageViewModel() {
        // 监听缩放变化
        scene.zoomProperty().addListener((obs, oldVal, newVal) ->
            zoomLevel.set(Math.round(newVal.doubleValue() * 100)));
    }

    public DoubleProperty zoomLevelProperty() {
        return zoomLevel;
    }

    public BooleanProperty hasSelectionProperty() {
        return hasSelection;
    }

    /**
     * 根据名称获取工具实例
     */
    public CanvasTool getToolByName(String name) {
        return switch (name) {
            case "select" -> selectTool;
            case "rectangle" -> new RectangleTool();
            case "ellipse" -> new EllipseTool();
            case "diamond" -> new DiamondTool();
            case "line" -> new LineTool();
            case "arrow" -> new ArrowTool();
            case "text" -> new TextTool();
            case "freehand" -> new FreehandTool();
            case "pan" -> new PanTool();
            default -> selectTool;
        };
    }

    /**
     * 更新选中元素的属性到 ViewModel
     */
    public void syncSelectionProperties() {
        var selected = selectTool.getSelectedElements();
        hasSelection.set(!selected.isEmpty());
        if (!selected.isEmpty()) {
            CanvasElement el = selected.get(0);
            selectedStrokeColor.set(el.getStrokeColor());
            selectedFillColor.set(el.getFillColor());
            selectedStrokeWidth.set(el.getStrokeWidth());
            selectedRoughness.set(el.getRoughness());
            selectedOpacity.set(el.getOpacity());
            selectedLineStyle.set(el.getLineStyle());
            selectedArrowStyle.set(el.getArrowStyle());
            selectedCornerRadius.set(el.getCornerRadius());
            selectedText.set(el.getText());
        }
    }

    /**
     * 将 ViewModel 属性应用到选中元素
     */
    public void applyPropertiesToSelection() {
        for (CanvasElement el : selectTool.getSelectedElements()) {
            el.setStrokeColor(selectedStrokeColor.get());
            el.setFillColor(selectedFillColor.get());
            el.setStrokeWidth(selectedStrokeWidth.get());
            el.setRoughness(selectedRoughness.get());
            el.setOpacity(selectedOpacity.get());
            el.setLineStyle(selectedLineStyle.get());
            el.setArrowStyle(selectedArrowStyle.get());
            el.setCornerRadius(selectedCornerRadius.get());
            el.setText(selectedText.get());
        }
    }

    /**
     * 缩放控制
     */
    public void zoomIn() {
        scene.setZoom(scene.getZoom() * 1.2);
    }

    public void zoomOut() {
        scene.setZoom(scene.getZoom() / 1.2);
    }

    public void resetZoom() {
        scene.setZoom(1.0);
        scene.setPanX(0);
        scene.setPanY(0);
    }

    /**
     * 清空画布
     */
    public void clearCanvas() {
        scene.clearElements();
    }
}
