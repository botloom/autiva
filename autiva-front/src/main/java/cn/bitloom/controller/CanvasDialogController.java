package cn.bitloom.controller;

import cn.bitloom.node.canvas.model.CanvasElement;
import cn.bitloom.node.canvas.model.Point;
import cn.bitloom.node.canvas.tool.CanvasTool;
import cn.bitloom.vm.CanvasPageViewModel;
import cn.bitloom.holder.DialogHolder;
import cn.bitloom.node.canvas.CanvasView;
import cn.bitloom.window.WindowManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import cn.bitloom.node.SvgImageView;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.*;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class CanvasDialogController implements WindowManager.StageAware, DialogHolder, Initializable {

    // Dialog fields
    @FXML private BorderPane rootContainer;
    @FXML private Button sendToChatBtn;

    // CanvasPage fields
    @FXML private VBox canvasPage;
    @FXML private AnchorPane canvasAnchor;
    @FXML private HBox floatingToolbar;
    @FXML private ToggleButton selectBtn;
    @FXML private ToggleButton rectangleBtn;
    @FXML private ToggleButton diamondBtn;
    @FXML private ToggleButton ellipseBtn;
    @FXML private ToggleButton arrowBtn;
    @FXML private ToggleButton lineBtn;
    @FXML private ToggleButton freehandBtn;
    @FXML private ToggleButton textBtn;
    @FXML private Separator toolbarSeparator;
    @FXML private StackPane canvasContainer;
    @FXML private VBox propertyPanel;

    // 属性行（用于按工具动态显示/隐藏）
    @FXML private HBox strokeColorRow;
    @FXML private HBox fillColorRow;
    @FXML private Separator propSeparator1;
    @FXML private HBox strokeWidthRow;
    @FXML private HBox lineStyleRow;
    @FXML private HBox roughnessRow;
    @FXML private HBox cornerRow;
    @FXML private Separator propSeparator2;
    @FXML private HBox opacityRow;

    // 描边颜色色块容器
    @FXML private HBox strokeColorBox;
    // 填充颜色色块容器
    @FXML private HBox fillColorBox;

    // 描边宽度
    @FXML private ToggleButton strokeThinBtn;
    @FXML private ToggleButton strokeMediumBtn;
    @FXML private ToggleButton strokeThickBtn;

    // 边框样式
    @FXML private ToggleButton lineSolidBtn;
    @FXML private ToggleButton lineDashedBtn;
    @FXML private ToggleButton lineDottedBtn;

    // 手绘风格
    @FXML private ToggleButton roughNeatBtn;
    @FXML private ToggleButton roughRoughBtn;
    @FXML private ToggleButton roughMessyBtn;

    // 边角
    @FXML private ToggleButton cornerSharpBtn;
    @FXML private ToggleButton cornerRoundBtn;

    // 透明度
    @FXML private Slider opacitySlider;

    @FXML private VBox layerPanel;
    @FXML private ListView<CanvasElement> layerListView;

    private final CanvasPageViewModel viewModel;
    private CanvasView canvasView;

    // 发送到聊天的回调
    private Consumer<String> onSendToChat;

    // 标记是否正在从 ViewModel 更新 UI（防止循环触发）
    private boolean updatingFromViewModel = false;

    // 色块按钮引用
    private ToggleButton[] strokeColorBtns;
    private ToggleButton[] fillColorBtns;

    // 描边颜色定义
    private static final String[] STROKE_COLORS = {"#000000", "#e03131", "#2f9e44", "#1971c2", "#e8590c"};
    // 填充颜色定义：透明 + 50%透明度色
    private static final String[] FILL_COLORS = {"transparent", "rgba(224,49,49,0.5)", "rgba(47,158,68,0.5)", "rgba(25,113,194,0.5)", "rgba(232,89,12,0.5)"};

    // 双击编辑文字相关
    private TextField textEditField;

    @Getter
    private Stage stage;

    @Override
    public double getWidth() {
        return 1100;
    }

    @Override
    public double getHeight() {
        return 750;
    }

    @Override
    public boolean isResizable() {
        return true;
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
        stage.setMinWidth(800);
        stage.setMinHeight(500);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 创建 ToggleGroup 并添加所有工具按钮
        ToggleGroup toolToggleGroup = new ToggleGroup();
        selectBtn.setToggleGroup(toolToggleGroup);
        rectangleBtn.setToggleGroup(toolToggleGroup);
        diamondBtn.setToggleGroup(toolToggleGroup);
        ellipseBtn.setToggleGroup(toolToggleGroup);
        arrowBtn.setToggleGroup(toolToggleGroup);
        lineBtn.setToggleGroup(toolToggleGroup);
        freehandBtn.setToggleGroup(toolToggleGroup);
        textBtn.setToggleGroup(toolToggleGroup);

        // 设置工具按钮图形图标
        setupToolIcons();

        // 设置发送到聊天按钮
        sendToChatBtn.setGraphic(createSvgIcon(ICON_BASE + "send.svg", 16, 16));
        sendToChatBtn.setOnAction(event -> handleSendToChat());

        // 创建画布视图
        canvasView = new CanvasView(viewModel.getScene());
        canvasView.bindToParent(canvasContainer);
        canvasContainer.getChildren().add(canvasView.getCanvas());

        // 确保 canvasContainer 不拦截工具栏/面板区域的鼠标事件
        canvasContainer.setMouseTransparent(false);

        // 设置默认工具
        canvasView.setTool(viewModel.getToolByName("select"));

        // 将 SelectTool 引用设置到渲染器，用于渲染选择框
        canvasView.getRenderer().setSelectTool(viewModel.getSelectTool());

        // 将 SelectTool 引用设置到 CanvasView，用于绘图后自动选中
        canvasView.setSelectTool(viewModel.getSelectTool());

        // 选中状态变化回调
        canvasView.setOnSelectionChange(hasSelection -> {
            viewModel.syncSelectionProperties();
        });

        // 绘图完成后回调：将面板预设属性应用到新元素
        canvasView.setOnElementCreated(() -> {
            viewModel.applyPropertiesToSelection();
            canvasView.getRenderer().markDirty();
        });

        // 双击编辑文字
        setupDoubleClickEdit();

        // 工具切换
        toolToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) {
                oldToggle.setSelected(true);
                return;
            }
            String toolName = getToolNameFromButton(newToggle);
            CanvasTool tool = viewModel.getToolByName(toolName);
            canvasView.setTool(tool);
            viewModel.getCurrentToolName().set(toolName);
            // 切换工具时更新属性面板
            updatePropertyPanelForTool(toolName);
        });

        // ---- 属性面板 ----
        setupStrokeColorSwatches();
        setupFillColorSwatches();
        setupOptionIcons();
        setupStrokeWidthGroup();
        setupLineStyleGroup();
        setupRoughnessGroup();
        setupCornerGroup();

        opacitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (updatingFromViewModel) return;
            viewModel.getSelectedOpacity().set(newVal.doubleValue());
            viewModel.applyPropertiesToSelection();
            canvasView.getRenderer().markDirty();
        });

        // 选中状态监听 - 选中元素时同步属性到面板
        viewModel.hasSelectionProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                viewModel.syncSelectionProperties();
                updatePropertyPanelFromViewModel();
            }
            // 选择工具下，属性面板跟随选中状态
            if ("select".equals(viewModel.getCurrentToolName().get())) {
                updatePropertyPanelForTool("select");
            }
        });

        // ---- 图层面板 ----
        setupLayerPanel();
    }

    // ---- 工具栏图形图标 ----

    private void setupToolIcons() {
        selectBtn.setGraphic(createToolIcon("select"));
        selectBtn.setText("");
        rectangleBtn.setGraphic(createToolIcon("rectangle"));
        rectangleBtn.setText("");
        diamondBtn.setGraphic(createToolIcon("diamond"));
        diamondBtn.setText("");
        ellipseBtn.setGraphic(createToolIcon("ellipse"));
        ellipseBtn.setText("");
        arrowBtn.setGraphic(createToolIcon("arrow"));
        arrowBtn.setText("");
        lineBtn.setGraphic(createToolIcon("line"));
        lineBtn.setText("");
        freehandBtn.setGraphic(createToolIcon("freehand"));
        freehandBtn.setText("");
        textBtn.setGraphic(createToolIcon("text"));
        textBtn.setText("");
    }

    // ---- 属性面板选项图形图标 ----

    private void setupOptionIcons() {
        strokeThinBtn.setGraphic(createOptionIcon("stroke-thin"));
        strokeThinBtn.setText("");
        strokeMediumBtn.setGraphic(createOptionIcon("stroke-medium"));
        strokeMediumBtn.setText("");
        strokeThickBtn.setGraphic(createOptionIcon("stroke-thick"));
        strokeThickBtn.setText("");

        lineSolidBtn.setGraphic(createOptionIcon("line-solid"));
        lineSolidBtn.setText("");
        lineDashedBtn.setGraphic(createOptionIcon("line-dashed"));
        lineDashedBtn.setText("");
        lineDottedBtn.setGraphic(createOptionIcon("line-dotted"));
        lineDottedBtn.setText("");

        roughNeatBtn.setGraphic(createOptionIcon("rough-neat"));
        roughNeatBtn.setText("");
        roughRoughBtn.setGraphic(createOptionIcon("rough-rough"));
        roughRoughBtn.setText("");
        roughMessyBtn.setGraphic(createOptionIcon("rough-messy"));
        roughMessyBtn.setText("");

        cornerSharpBtn.setGraphic(createOptionIcon("corner-sharp"));
        cornerSharpBtn.setText("");
        cornerRoundBtn.setGraphic(createOptionIcon("corner-round"));
        cornerRoundBtn.setText("");
    }

    // ---- 图标加载（使用 SVG 文件） ----

    private static final String ICON_BASE = "/cn/bitloom/images/canvas-";
    private static final int TOOL_ICON_SIZE = 20;
    private static final int OPTION_ICON_SIZE = 18;

    /** 创建工具栏 SVG 图标 */
    private Node createToolIcon(String name) {
        return createSvgIcon(ICON_BASE + name + ".svg", TOOL_ICON_SIZE, TOOL_ICON_SIZE);
    }

    /** 创建属性面板选项 SVG 图标 */
    private Node createOptionIcon(String name) {
        return createSvgIcon(ICON_BASE + name + ".svg", OPTION_ICON_SIZE, OPTION_ICON_SIZE);
    }

    /** 通用 SVG 图标加载 */
    private Node createSvgIcon(String svgPath, double width, double height) {
        SvgImageView view = new SvgImageView();
        view.setFitWidth(width);
        view.setFitHeight(height);
        view.setSvgPath(svgPath);
        return view;
    }

    // ---- 描边颜色色块 ----

    private void setupStrokeColorSwatches() {
        ToggleGroup group = new ToggleGroup();
        strokeColorBtns = new ToggleButton[STROKE_COLORS.length];

        for (int i = 0; i < STROKE_COLORS.length; i++) {
            ToggleButton btn = new ToggleButton();
            btn.setToggleGroup(group);
            btn.setStyle(String.format(
                "-fx-background-color: %s; -fx-min-width: 20; -fx-min-height: 20; " +
                "-fx-max-width: 20; -fx-max-height: 20; -fx-background-radius: 3; " +
                "-fx-border-radius: 3; -fx-border-color: #ddd; -fx-border-width: 1; " +
                "-fx-padding: 0; -fx-cursor: hand;",
                STROKE_COLORS[i]
            ));
            btn.setUserData(STROKE_COLORS[i]);
            final int idx = i;
            btn.setOnAction(e -> {
                if (updatingFromViewModel) return;
                viewModel.getSelectedStrokeColor().set(STROKE_COLORS[idx]);
                viewModel.applyPropertiesToSelection();
                canvasView.getRenderer().markDirty();
            });
            strokeColorBtns[i] = btn;
            strokeColorBox.getChildren().add(btn);
        }
    }

    // ---- 填充颜色色块 ----

    private void setupFillColorSwatches() {
        ToggleGroup group = new ToggleGroup();
        fillColorBtns = new ToggleButton[FILL_COLORS.length];

        for (int i = 0; i < FILL_COLORS.length; i++) {
            ToggleButton btn = new ToggleButton();
            btn.setToggleGroup(group);

            if (i == 0) {
                // 透明背景 - 用斜线表示
                btn.setStyle(
                    "-fx-min-width: 20; -fx-min-height: 20; -fx-max-width: 20; -fx-max-height: 20; " +
                    "-fx-background-radius: 3; -fx-border-radius: 3; -fx-border-color: #ddd; " +
                    "-fx-border-width: 1; -fx-padding: 0; -fx-cursor: hand; " +
                    "-fx-background-color: white; " +
                    "-fx-background-image: url('data:image/svg+xml;utf8,<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"20\" height=\"20\"><line x1=\"0\" y1=\"20\" x2=\"20\" y2=\"0\" stroke=\"%23ccc\" stroke-width=\"1.5\"/></svg>');"
                );
            } else {
                // 50% 透明度色块 - 用实际颜色显示
                String displayColor = STROKE_COLORS[i]; // 用对应的不透明色显示
                btn.setStyle(String.format(
                    "-fx-background-color: %s; -fx-min-width: 20; -fx-min-height: 20; " +
                    "-fx-max-width: 20; -fx-max-height: 20; -fx-background-radius: 3; " +
                    "-fx-border-radius: 3; -fx-border-color: #ddd; -fx-border-width: 1; " +
                    "-fx-padding: 0; -fx-cursor: hand; -fx-opacity: 0.5;",
                    displayColor
                ));
            }

            btn.setUserData(FILL_COLORS[i]);
            final int idx = i;
            btn.setOnAction(e -> {
                if (updatingFromViewModel) return;
                viewModel.getSelectedFillColor().set(FILL_COLORS[idx]);
                viewModel.applyPropertiesToSelection();
                canvasView.getRenderer().markDirty();
            });
            fillColorBtns[i] = btn;
            fillColorBox.getChildren().add(btn);
        }
    }

    // ---- 描边宽度 ----

    private void setupStrokeWidthGroup() {
        ToggleGroup group = new ToggleGroup();
        strokeThinBtn.setToggleGroup(group);
        strokeMediumBtn.setToggleGroup(group);
        strokeThickBtn.setToggleGroup(group);

        strokeThinBtn.setOnAction(e -> {
            if (updatingFromViewModel) return;
            viewModel.getSelectedStrokeWidth().set(1.0);
            viewModel.applyPropertiesToSelection();
            canvasView.getRenderer().markDirty();
        });
        strokeMediumBtn.setOnAction(e -> {
            if (updatingFromViewModel) return;
            viewModel.getSelectedStrokeWidth().set(2.0);
            viewModel.applyPropertiesToSelection();
            canvasView.getRenderer().markDirty();
        });
        strokeThickBtn.setOnAction(e -> {
            if (updatingFromViewModel) return;
            viewModel.getSelectedStrokeWidth().set(4.0);
            viewModel.applyPropertiesToSelection();
            canvasView.getRenderer().markDirty();
        });
    }

    // ---- 边框样式 ----

    private void setupLineStyleGroup() {
        ToggleGroup group = new ToggleGroup();
        lineSolidBtn.setToggleGroup(group);
        lineDashedBtn.setToggleGroup(group);
        lineDottedBtn.setToggleGroup(group);

        lineSolidBtn.setOnAction(e -> {
            if (updatingFromViewModel) return;
            viewModel.getSelectedLineStyle().set("solid");
            viewModel.applyPropertiesToSelection();
            canvasView.getRenderer().markDirty();
        });
        lineDashedBtn.setOnAction(e -> {
            if (updatingFromViewModel) return;
            viewModel.getSelectedLineStyle().set("dashed");
            viewModel.applyPropertiesToSelection();
            canvasView.getRenderer().markDirty();
        });
        lineDottedBtn.setOnAction(e -> {
            if (updatingFromViewModel) return;
            viewModel.getSelectedLineStyle().set("dotted");
            viewModel.applyPropertiesToSelection();
            canvasView.getRenderer().markDirty();
        });
    }

    // ---- 手绘风格 ----

    private void setupRoughnessGroup() {
        ToggleGroup group = new ToggleGroup();
        roughNeatBtn.setToggleGroup(group);
        roughRoughBtn.setToggleGroup(group);
        roughMessyBtn.setToggleGroup(group);

        roughNeatBtn.setOnAction(e -> {
            if (updatingFromViewModel) return;
            viewModel.getSelectedRoughness().set(0.0);
            viewModel.applyPropertiesToSelection();
            canvasView.getRenderer().markDirty();
        });
        roughRoughBtn.setOnAction(e -> {
            if (updatingFromViewModel) return;
            viewModel.getSelectedRoughness().set(1.8);
            viewModel.applyPropertiesToSelection();
            canvasView.getRenderer().markDirty();
        });
        roughMessyBtn.setOnAction(e -> {
            if (updatingFromViewModel) return;
            viewModel.getSelectedRoughness().set(4.0);
            viewModel.applyPropertiesToSelection();
            canvasView.getRenderer().markDirty();
        });
    }

    // ---- 边角 ----

    private void setupCornerGroup() {
        ToggleGroup group = new ToggleGroup();
        cornerSharpBtn.setToggleGroup(group);
        cornerRoundBtn.setToggleGroup(group);

        cornerSharpBtn.setOnAction(e -> {
            if (updatingFromViewModel) return;
            viewModel.getSelectedCornerRadius().set(0);
            viewModel.applyPropertiesToSelection();
            canvasView.getRenderer().markDirty();
        });
        cornerRoundBtn.setOnAction(e -> {
            if (updatingFromViewModel) return;
            viewModel.getSelectedCornerRadius().set(12);
            viewModel.applyPropertiesToSelection();
            canvasView.getRenderer().markDirty();
        });
    }

    // ---- 双击编辑文字 ----

    private void setupDoubleClickEdit() {
        canvasView.getCanvas().setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                handleDoubleClick(e);
            }
        });
    }

    private void handleDoubleClick(javafx.scene.input.MouseEvent e) {
        // 移除已有的编辑框
        removeTextEditField();

        Point scenePoint = viewModel.getScene().screenToScene(e.getX(), e.getY());

        // 查找双击的元素
        CanvasElement hit = null;
        for (int i = viewModel.getScene().getElements().size() - 1; i >= 0; i--) {
            CanvasElement el = viewModel.getScene().getElements().get(i);
            if (el.isVisible() && el.contains(scenePoint)) {
                hit = el;
                break;
            }
        }

        if (hit == null) return;

        // 创建文本编辑框
        textEditField = new TextField();
        textEditField.setText(hit.getText());
        textEditField.setStyle(
            "-fx-font-size: 14px; -fx-padding: 4 6; " +
            "-fx-font-family: 'Segoe Script', 'Bradley Hand', 'Comic Sans MS', cursive; " +
            "-fx-background-color: rgba(255,255,255,0.95); " +
            "-fx-border-color: #0071e3; -fx-border-width: 2; " +
            "-fx-background-radius: 6; -fx-border-radius: 6;"
        );

        // 计算编辑框位置（屏幕坐标）
        double zoom = viewModel.getScene().getZoom();
        double panX = viewModel.getScene().getPanX();
        double panY = viewModel.getScene().getPanY();
        double screenX = hit.getX() * zoom + panX + 10;
        double screenY = hit.getY() * zoom + panY + hit.getHeight() * zoom / 2 - 10;

        textEditField.setLayoutX(screenX);
        textEditField.setLayoutY(screenY);
        textEditField.setPrefWidth(Math.max(80, hit.getWidth() * zoom - 20));
        textEditField.setPrefHeight(28);

        // 保存文字
        final CanvasElement element = hit;
        textEditField.setOnAction(ev -> {
            commitTextEdit(element);
        });
        textEditField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                commitTextEdit(element);
            }
        });

        canvasAnchor.getChildren().add(textEditField);
        textEditField.requestFocus();
        textEditField.selectAll();
    }

    private void commitTextEdit(CanvasElement element) {
        if (textEditField == null) return;
        String newText = textEditField.getText();
        element.setText(newText);
        viewModel.getSelectedText().set(newText);
        viewModel.applyPropertiesToSelection();
        canvasView.getRenderer().markDirty();
        removeTextEditField();
    }

    private void removeTextEditField() {
        if (textEditField != null) {
            canvasAnchor.getChildren().remove(textEditField);
            textEditField = null;
        }
    }

    // ---- 工具方法 ----

    private String getToolNameFromButton(Toggle toggle) {
        if (toggle == selectBtn) return "select";
        if (toggle == rectangleBtn) return "rectangle";
        if (toggle == diamondBtn) return "diamond";
        if (toggle == ellipseBtn) return "ellipse";
        if (toggle == arrowBtn) return "arrow";
        if (toggle == lineBtn) return "line";
        if (toggle == freehandBtn) return "freehand";
        if (toggle == textBtn) return "text";
        return "select";
    }

    private void updatePropertyPanelFromViewModel() {
        updatingFromViewModel = true;
        try {
            // 描边颜色
            String strokeHex = viewModel.getSelectedStrokeColor().get();
            for (int i = 0; i < strokeColorBtns.length; i++) {
                strokeColorBtns[i].setSelected(STROKE_COLORS[i].equals(strokeHex));
            }

            // 填充颜色
            String fillVal = viewModel.getSelectedFillColor().get();
            for (int i = 0; i < fillColorBtns.length; i++) {
                fillColorBtns[i].setSelected(FILL_COLORS[i].equals(fillVal));
            }

            // 描边宽度
            double sw = viewModel.getSelectedStrokeWidth().get();
            strokeThinBtn.setSelected(sw <= 1.0);
            strokeMediumBtn.setSelected(sw > 1.0 && sw <= 2.5);
            strokeThickBtn.setSelected(sw > 2.5);

            // 边框样式
            String ls = viewModel.getSelectedLineStyle().get();
            lineSolidBtn.setSelected("solid".equals(ls));
            lineDashedBtn.setSelected("dashed".equals(ls));
            lineDottedBtn.setSelected("dotted".equals(ls));

            // 手绘风格
            double rough = viewModel.getSelectedRoughness().get();
            roughNeatBtn.setSelected(rough <= 0.1);
            roughRoughBtn.setSelected(rough > 0.1 && rough <= 2.5);
            roughMessyBtn.setSelected(rough > 2.5);

            // 边角
            double cr = viewModel.getSelectedCornerRadius().get();
            cornerSharpBtn.setSelected(cr < 1);
            cornerRoundBtn.setSelected(cr >= 1);

            // 透明度
            opacitySlider.setValue(viewModel.getSelectedOpacity().get());
        } finally {
            updatingFromViewModel = false;
        }
    }

    /**
     * 根据当前工具更新属性面板的显示内容。
     * 每个工具有自己的属性配置，选中工具时即显示对应属性面板。
     * - select: 仅在有选中元素时显示该元素的属性
     * - rectangle/diamond/ellipse: 描边、填充、线宽、样式、手绘、边角、透明度
     * - arrow/line: 描边、线宽、样式、手绘、透明度
     * - freehand: 描边、线宽、透明度
     * - text: 描边（文字颜色）、透明度
     */
    private void updatePropertyPanelForTool(String toolName) {
        boolean showPanel;
        boolean showStrokeColor = false;
        boolean showFillColor = false;
        boolean showStrokeWidth = false;
        boolean showLineStyle = false;
        boolean showRoughness = false;
        boolean showCorner = false;
        boolean showOpacity = false;

        switch (toolName) {
            case "select":
                // 选择工具：仅在有选中元素时显示属性面板
                showPanel = viewModel.hasSelectionProperty().get();
                if (showPanel) {
                    // 选中元素时显示全部属性
                    showStrokeColor = showFillColor = showStrokeWidth = true;
                    showLineStyle = showRoughness = showCorner = showOpacity = true;
                }
                break;
            case "rectangle":
            case "diamond":
            case "ellipse":
                showPanel = true;
                showStrokeColor = showFillColor = showStrokeWidth = true;
                showLineStyle = showRoughness = showCorner = showOpacity = true;
                break;
            case "arrow":
            case "line":
                showPanel = true;
                showStrokeColor = showStrokeWidth = showLineStyle = true;
                showRoughness = showOpacity = true;
                break;
            case "freehand":
                showPanel = true;
                showStrokeColor = showStrokeWidth = showOpacity = true;
                break;
            case "text":
                showPanel = true;
                showStrokeColor = showOpacity = true;
                break;
            default:
                showPanel = false;
        }

        propertyPanel.setVisible(showPanel);
        propertyPanel.setManaged(showPanel);

        // 显示/隐藏属性行
        setRowVisible(strokeColorRow, showStrokeColor);
        setRowVisible(fillColorRow, showFillColor);
        setRowVisible(strokeWidthRow, showStrokeWidth);
        setRowVisible(lineStyleRow, showLineStyle);
        setRowVisible(roughnessRow, showRoughness);
        setRowVisible(cornerRow, showCorner);
        setRowVisible(opacityRow, showOpacity);

        // 分隔线：仅在两侧都有可见行时显示
        boolean hasVisibleBeforeSep1 = showStrokeColor || showFillColor;
        boolean hasVisibleAfterSep1 = showStrokeWidth || showLineStyle || showRoughness || showCorner;
        setRowVisible(propSeparator1, hasVisibleBeforeSep1 && hasVisibleAfterSep1);

        boolean hasVisibleBeforeSep2 = showStrokeWidth || showLineStyle || showRoughness || showCorner;
        boolean hasVisibleAfterSep2 = showOpacity;
        setRowVisible(propSeparator2, hasVisibleBeforeSep2 && hasVisibleAfterSep2);

        // 如果有选中元素，同步属性到面板
        if (showPanel && viewModel.hasSelectionProperty().get()) {
            viewModel.syncSelectionProperties();
            updatePropertyPanelFromViewModel();
        }
    }

    private void setRowVisible(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /**
     * 获取元素类型的中文显示名
     */
    private String getTypeDisplayName(CanvasElement element) {
        return switch (element.getType()) {
            case "rectangle" -> "矩形";
            case "ellipse" -> "椭圆";
            case "diamond" -> "菱形";
            case "line" -> "线条";
            case "arrow" -> "箭头";
            case "text" -> "文字";
            case "freehand" -> "手绘";
            default -> "图形";
        };
    }

    // ---- 图层面板 ----

    private void setupLayerPanel() {
        // 绑定元素列表（倒序显示，最上层的元素在列表最上面）
        layerListView.setItems(viewModel.getScene().getElements());

        // 自定义单元格：显示类型名 + 可见性复选框
        layerListView.setCellFactory(lv -> new CheckBoxListCell<CanvasElement>(CanvasElement::visibleProperty) {
            @Override
            public void updateItem(CanvasElement element, boolean empty) {
                super.updateItem(element, empty);
                if (empty || element == null) {
                    setText(null);
                } else {
                    String displayName = getTypeDisplayName(element);
                    String text = element.getText();
                    if (text != null && !text.isEmpty()) {
                        displayName += " «" + (text.length() > 6 ? text.substring(0, 6) + "…" : text) + "»";
                    }
                    setText(displayName + " " + element.getId());
                    // 监听可见性变化，触发画布重绘
                    element.visibleProperty().addListener((obs, oldVal, newVal) -> {
                        canvasView.getRenderer().markDirty();
                    });
                }
            }
        });

        // 点击图层选中对应元素
        layerListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                viewModel.getSelectTool().getSelectedElements().clear();
                viewModel.getSelectTool().getSelectedElements().add(newVal);
                viewModel.syncSelectionProperties();
                canvasView.getRenderer().markDirty();
            }
        });

        // 右键菜单：上移、下移、置顶、置底、删除
        ContextMenu layerContextMenu = new ContextMenu();
        MenuItem moveUpItem = new MenuItem("上移一层");
        MenuItem moveDownItem = new MenuItem("下移一层");
        MenuItem moveToTopItem = new MenuItem("置顶");
        MenuItem moveToBottomItem = new MenuItem("置底");
        MenuItem deleteItem = new MenuItem("删除");

        moveUpItem.setOnAction(e -> {
            CanvasElement selected = layerListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                viewModel.getScene().moveElementUp(selected);
                layerListView.refresh();
                canvasView.getRenderer().markDirty();
            }
        });
        moveDownItem.setOnAction(e -> {
            CanvasElement selected = layerListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                viewModel.getScene().moveElementDown(selected);
                layerListView.refresh();
                canvasView.getRenderer().markDirty();
            }
        });
        moveToTopItem.setOnAction(e -> {
            CanvasElement selected = layerListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                viewModel.getScene().moveElementToTop(selected);
                layerListView.refresh();
                canvasView.getRenderer().markDirty();
            }
        });
        moveToBottomItem.setOnAction(e -> {
            CanvasElement selected = layerListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                viewModel.getScene().moveElementToBottom(selected);
                layerListView.refresh();
                canvasView.getRenderer().markDirty();
            }
        });
        deleteItem.setOnAction(e -> {
            CanvasElement selected = layerListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                viewModel.getScene().removeElement(selected);
                viewModel.getSelectTool().getSelectedElements().remove(selected);
                viewModel.syncSelectionProperties();
                canvasView.getRenderer().markDirty();
            }
        });

        layerContextMenu.getItems().addAll(moveUpItem, moveDownItem, moveToTopItem, moveToBottomItem, deleteItem);
        layerListView.setContextMenu(layerContextMenu);
    }

    // ---- Send to Chat ----

    public void setOnSendToChat(Consumer<String> callback) {
        this.onSendToChat = callback;
    }

    private void handleSendToChat() {
        String content = serializeCanvasContent();
        if (onSendToChat != null) {
            onSendToChat.accept(content);
        }
        if (stage != null) {
            stage.close();
        }
    }

    /**
     * 将画布内容序列化为人类可读的文本描述，供 LLM 理解。
     */
    private String serializeCanvasContent() {
        List<CanvasElement> elements = viewModel.getScene().getElements();
        if (elements.isEmpty()) {
            return "[画布内容为空]";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[画布内容，共 ").append(elements.size()).append(" 个元素]\n");

        for (int i = 0; i < elements.size(); i++) {
            CanvasElement el = elements.get(i);
            sb.append(i + 1).append(". ");

            String typeName = getTypeDisplayName(el);
            sb.append(typeName);

            // 位置和尺寸
            sb.append(" (位置: ").append((int) el.getX()).append(",").append((int) el.getY());
            sb.append(", 尺寸: ").append((int) el.getWidth()).append("x").append((int) el.getHeight()).append(")");

            // 描边颜色
            if (!"#000000".equals(el.getStrokeColor())) {
                sb.append(", 描边: ").append(el.getStrokeColor());
            }

            // 填充颜色
            if (!"transparent".equals(el.getFillColor())) {
                sb.append(", 填充: ").append(el.getFillColor());
            }

            // 文字内容
            if (el.getText() != null && !el.getText().isEmpty()) {
                sb.append(", 文字: \"").append(el.getText()).append("\"");
            }

            // 连接信息
            if (el.getStartConnectedElementId() != null || el.getEndConnectedElementId() != null) {
                sb.append(", 连接: ");
                if (el.getStartConnectedElementId() != null) {
                    sb.append("起点→").append(el.getStartConnectedElementId());
                }
                if (el.getEndConnectedElementId() != null) {
                    sb.append("终点→").append(el.getEndConnectedElementId());
                }
            }

            sb.append("\n");
        }

        return sb.toString();
    }
}
