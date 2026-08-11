package cn.bitloom.controller;

import cn.bitloom.agentic.tool.file.FileDiff;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;

/**
 * 编辑器面板通用基类。
 * <p>
 * 仅包含 TOOL_CALLS / TODO 两个通用视图，供 work 模式使用。
 * coder 模式由 {@link CoderEditorPanelController} 继承本类，扩展 TERMINAL / DIFF 视图。
 * <p>
 * coder 专有方法（openTerminal / showDiffInProjectView）
 * 在此声明为空实现，子类 override 注入实际逻辑。IndexController 通过基类引用统一调度。
 * <p>
 * 项目目录树已迁移至 SideBarController 中展示，本面板不再包含 PROJECT 视图。
 */
@Slf4j
@Component
@Primary
public class EditorPanelController implements Initializable {

    public enum ViewType { TERMINAL, FILE, DIFF, TOOL_CALLS, TODO }

    @FXML
    @Getter
    private VBox editorPanel;
    @FXML
    protected StackPane viewContainer;
    @FXML
    protected VBox toolCallsView;
    @FXML
    protected ListView<Node> toolCallsListView;
    @FXML
    protected VBox todoView;
    @FXML
    protected ListView<Node> todoListView;

    private final ObservableList<Node> toolCallNodes = FXCollections.observableArrayList();
    private final ObservableList<Node> todoNodes = FXCollections.observableArrayList();

    // ===== stick-to-bottom 跟随模式 =====
    private boolean toolListStickToBottom = true;
    private boolean todoListStickToBottom = true;
    private boolean toolScrollBarBound = false;
    private boolean todoScrollBarBound = false;

    @Setter
    @Getter
    protected IndexController indexController;

    @Getter
    protected ViewType currentViewType = null;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupRoundedClip();
        setupToolCallsListView();
        setupTodoListView();
    }

    private void setupToolCallsListView() {
        toolCallsListView.setFocusTraversable(false);
        toolCallsListView.setItems(toolCallNodes);
        toolCallsListView.setCellFactory(list -> new ToolListCell());
        setupStickToBottom(toolCallsListView, () -> toolListStickToBottom, v -> toolListStickToBottom = v,
                () -> toolScrollBarBound, v -> toolScrollBarBound = v);
    }

    private void setupTodoListView() {
        todoListView.setFocusTraversable(false);
        todoListView.setItems(todoNodes);
        todoListView.setCellFactory(list -> new ToolListCell());
        setupStickToBottom(todoListView, () -> todoListStickToBottom, v -> todoListStickToBottom = v,
                () -> todoScrollBarBound, v -> todoScrollBarBound = v);
    }

    /**
     * 为 ListView 配置 stick-to-bottom 跟随模式。
     * 用户向上滚动时停止跟随，滚回底部时恢复跟随。
     */
    private void setupStickToBottom(ListView<?> listView,
                                    java.util.function.BooleanSupplier stickGetter,
                                    java.util.function.Consumer<Boolean> stickSetter,
                                    java.util.function.BooleanSupplier boundGetter,
                                    java.util.function.Consumer<Boolean> boundSetter) {
        listView.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.getDeltaY() > 0) {
                stickSetter.accept(false);
            }
        });
        listView.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null && !boundGetter.getAsBoolean()) {
                Platform.runLater(() -> bindVerticalScrollBar(listView, stickSetter, boundGetter, boundSetter));
            }
        });
    }

    private void bindVerticalScrollBar(ListView<?> listView,
                                       java.util.function.Consumer<Boolean> stickSetter,
                                       java.util.function.BooleanSupplier boundGetter,
                                       java.util.function.Consumer<Boolean> boundSetter) {
        if (boundGetter.getAsBoolean()) return;
        Node bar = listView.lookup(".scroll-bar:vertical");
        if (bar instanceof ScrollBar scrollBar) {
            scrollBar.valueProperty().addListener((o, ov, nv) -> {
                if (nv.doubleValue() >= 0.95) {
                    stickSetter.accept(true);
                }
            });
            boundSetter.accept(true);
        }
    }

    /**
     * 滚动指定 ListView 到底部（仅在 stickToBottom 时）
     */
    private void scrollToBottom(ListView<?> listView, boolean stickToBottom) {
        if (!stickToBottom) return;
        Platform.runLater(() -> {
            Node bar = listView.lookup(".scroll-bar:vertical");
            if (bar instanceof ScrollBar scrollBar) {
                scrollBar.setValue(scrollBar.getMax());
            } else {
                int size = listView.getItems().size();
                if (size > 0) {
                    listView.scrollTo(size - 1);
                }
            }
        });
    }

    /**
     * 工具/待办列表通用 cell：直接 setGraphic(node)，Region 撑满宽度
     */
    private static class ToolListCell extends ListCell<Node> {
        @Override
        protected void updateItem(Node node, boolean empty) {
            super.updateItem(node, empty);
            if (empty || node == null) {
                setGraphic(null);
            } else {
                if (node instanceof Region region) {
                    region.setMaxWidth(Double.MAX_VALUE);
                }
                setGraphic(node);
            }
        }
    }

    /**
     * 给 viewContainer 设置圆角裁剪，确保视图的方角都被裁剪到圆角形状
     */
    private void setupRoundedClip() {
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(viewContainer.widthProperty());
        clip.heightProperty().bind(viewContainer.heightProperty());
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        viewContainer.setClip(clip);
    }

    // ===== 视图切换 =====

    /**
     * 隐藏所有视图（子类可 override 扩展更多视图）
     */
    protected void hideAllViews() {
        toolCallsView.setVisible(false);
        toolCallsView.setManaged(false);
        todoView.setVisible(false);
        todoView.setManaged(false);
    }

    /**
     * 显示工具调用视图
     */
    public void showToolCallsView() {
        hideAllViews();
        toolCallsView.setVisible(true);
        toolCallsView.setManaged(true);
        currentViewType = ViewType.TOOL_CALLS;
    }

    /**
     * 显示待办视图
     */
    public void showTodoView() {
        hideAllViews();
        todoView.setVisible(true);
        todoView.setManaged(true);
        currentViewType = ViewType.TODO;
    }

    /**
     * 显示终端视图（通用基类空实现，coder 模式 override）
     */
    public void showTerminalView() {
        // work 模式不支持终端视图
    }

    /**
     * 显示 Diff 视图（通用基类空实现，coder 模式 override）
     */
    public void showDiffView() {
        // work 模式不支持 diff 视图
    }

    /**
     * 显示文件内容视图（通用基类空实现，coder 模式 override）
     */
    public void showFileView() {
        // work 模式不支持文件内容视图
    }

    /**
     * 添加工具调用卡片到工具调用视图。
     * 不自动弹出面板，用户需主动点击工具按钮查看。
     */
    public void addToolCallCard(Node card) {
        toolCallNodes.add(card);
        scrollToBottom(toolCallsListView, toolListStickToBottom);
    }

    /**
     * 添加待办卡片到待办视图，并自动弹出面板
     */
    public void addTodoCard(Node card) {
        todoNodes.add(card);
        if (indexController != null) {
            indexController.ensureEditorVisible();
        }
        show();
        showTodoView();
        scrollToBottom(todoListView, todoListStickToBottom);
    }

    /**
     * 清空工具调用卡片
     */
    public void clearToolCalls() {
        toolCallNodes.clear();
    }

    /**
     * 清空待办卡片
     */
    public void clearTodos() {
        todoNodes.clear();
    }

    // ===== 面板显示/隐藏 =====

    public void show() {
        editorPanel.setVisible(true);
        editorPanel.setManaged(true);
    }

    public void hide() {
        editorPanel.setVisible(false);
        editorPanel.setManaged(false);
    }

    public boolean isVisible() {
        return editorPanel.isVisible();
    }

    // ===== coder 专有方法（通用基类空实现，coder 模式 override） =====

    /**
     * 打开终端（通用基类空实现，coder 模式 override）
     */
    public void openTerminal(Path workingDir) {
        // work 模式不支持终端
    }

    /**
     * 关闭终端会话（通用基类空实现，coder 模式 override）
     */
    public void closeTerminal() {
        // work 模式不支持终端
    }

    /**
     * 在编辑器面板显示文件内容（通用基类空实现，coder 模式 override）
     */
    public void showFileContent(Path filePath) {
        // work 模式不支持文件内容显示
    }

    /**
     * 在项目视图中显示指定文件的 diff（通用基类空实现，coder 模式 override）
     */
    public void showDiffInProjectView(FileDiff diff) {
        // work 模式不支持 diff 显示
    }
}
