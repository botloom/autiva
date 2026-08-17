package cn.bitloom.controller;

import cn.bitloom.agentic.tool.file.FileDiff;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * 编辑器面板通用基类。
 * <p>
 * 每个视图类型（终端/工具/Todo/文件/DIFF）以独立卡片形式展示。
 * 每个卡片右上角有浮动的关闭按钮。
 * 终端/文件视图内部使用 {@link SubTabContainer} 管理多实例子 tab。
 * 多个视图可同时存在，一次只显示一个，通过 ButtonBar 按钮切换。
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
    @FXML
    protected Button toolCallsCloseBtn;
    @FXML
    protected Button todoCloseBtn;

    private final ObservableList<Node> toolCallNodes = FXCollections.observableArrayList();
    private final ObservableList<Node> todoNodes = FXCollections.observableArrayList();

    // ===== 视图管理 =====
    protected final ObservableList<EditorTab> tabs = FXCollections.observableArrayList();
    @Getter
    protected EditorTab activeTab = null;
    /** 进入当前视图之前的活跃视图（关闭当前视图时用于回落，避免总是跳到列表末尾） */
    private EditorTab previousActiveTab = null;
    private int tabIdCounter = 0;

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

    /**
     * 当前视图类型变化时的回调（用于同步右上角按钮的蓝色激活态）。
     * 参数为变化后的当前视图类型，null 表示已无激活视图。
     */
    @Setter
    protected Consumer<ViewType> onViewTypeChanged;

    /** 视图类型变化后触发回调 */
    private void notifyViewTypeChanged() {
        if (onViewTypeChanged != null) {
            onViewTypeChanged.accept(currentViewType);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 让 editorPanel 填满 slot 容器
        editorPanel.setMaxWidth(Double.MAX_VALUE);
        editorPanel.setMaxHeight(Double.MAX_VALUE);
        setupRoundedClip();
        setupToolCallsListView();
        setupTodoListView();
        // 工具/Todo 视图 tab 栏的关闭视图按钮
        if (toolCallsCloseBtn != null) {
            toolCallsCloseBtn.setOnAction(e -> closeViewByType(ViewType.TOOL_CALLS));
        }
        if (todoCloseBtn != null) {
            todoCloseBtn.setOnAction(e -> closeViewByType(ViewType.TODO));
        }
    }

    /**
     * 关闭指定类型的视图（tab 栏关闭按钮）。
     * 工具/Todo 为单例复用型：关闭仅隐藏，保留内容下次复用；其余类型销毁。
     */
    protected void closeViewByType(ViewType type) {
        EditorTab tab = findTabByType(type);
        if (tab == null) return;
        if (isSingletonType(type)) {
            hideSingletonTab(tab);
        } else {
            closeTab(tab);
        }
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
     * 工具/Todo 卡片容器 cell。cell 自身填满 ListView 宽度，避免卡片
     * 按内容自然宽度被横向撑开（导致卡片比工具视图宽）。
     */
    private static class ToolListCell extends ListCell<Node> {
        @Override
        protected void updateItem(Node node, boolean empty) {
            super.updateItem(node, empty);
            if (empty || node == null) {
                setGraphic(null);
                return;
            }
            // cell 填满 ListView 内容宽度，宽度由视图决定而非内容
            setMaxWidth(Double.MAX_VALUE);
            if (node instanceof Region region) {
                // 卡片宽度严格绑定 cell 宽度（wrap Label 的 prefWidth 按单行计算，
                // 若不绑定会以内容自然宽度撑大卡片、横向溢出视图）
                region.setMaxWidth(Double.MAX_VALUE);
                region.prefWidthProperty().bind(widthProperty());
            }
            setGraphic(node);
        }
    }

    private void setupRoundedClip() {
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(viewContainer.widthProperty());
        clip.heightProperty().bind(viewContainer.heightProperty());
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        viewContainer.setClip(clip);
    }

    // ===== 视图管理核心方法 =====

    /**
     * 单例视图类型：工具/Todo 卡片复用，关闭仅隐藏不销毁（避免重建闪烁、保留内容）。
     */
    private static boolean isSingletonType(ViewType type) {
        return type == ViewType.TOOL_CALLS || type == ViewType.TODO;
    }

    /**
     * 打开单例视图（工具/Todo）：若已存在则激活复用，否则创建卡片。
     * 不销毁只复用，保证切换不闪烁、内容不重建。同时确保面板在 SplitPane 中可见
     * （上次关闭可能已收起面板）。
     */
    protected void openSingletonTab(ViewType type, Node content) {
        if (indexController != null) {
            indexController.ensureEditorVisible();
        }
        EditorTab existing = findTabByType(type);
        if (existing != null) {
            selectTab(existing);
            return;
        }
        EditorTab tab = createTab(type, content);
        addTab(tab);
        selectTab(tab);
    }

    /**
     * 隐藏单例视图：仅隐藏不销毁，保留内容与 tab 结构以便下次切换复用。
     * 所有视图都关闭后收起面板。
     */
    protected void hideSingletonTab(EditorTab tab) {
        tab.card.setVisible(false);
        tab.card.setManaged(false);
        if (activeTab == tab) {
            activeTab = null;
            currentViewType = null;
            notifyViewTypeChanged();
        }
        if (tabs.stream().noneMatch(t -> t.card.isVisible())) {
            if (indexController != null) {
                indexController.closeEditorPanel();
            } else {
                hide();
            }
        }
    }

    /**
     * 切换单例视图：当前若不是该视图则打开，是则关闭（隐藏），实现右上角按钮开/关切换。
     */
    protected void toggleSingletonView(ViewType type, Node content) {
        if (isCurrentView(type)) {
            EditorTab tab = findTabByType(type);
            if (tab != null) {
                hideSingletonTab(tab);
            }
        } else {
            openSingletonTab(type, content);
        }
    }

    /**
     * 当前展示的视图是否是指定类型。
     */
    public boolean isCurrentView(ViewType type) {
        return currentViewType == type;
    }

    /**
     * 创建视图卡片：用 StackPane 包装内容并恢复其可见性。
     */
    protected EditorTab createTab(ViewType type, Node content) {
        String id = type.name().toLowerCase() + "-" + (tabIdCounter++);
        StackPane card = wrapWithCloseButton(content);
        return new EditorTab(id, type, content, card, new HashMap<>());
    }

    /**
     * 用 StackPane 包装内容。视图的关闭统一由 tab 栏的关闭按钮负责，
     * 因此这里不再为卡片添加浮动关闭按钮。
     */
    private StackPane wrapWithCloseButton(Node content) {
        StackPane card = new StackPane();
        card.getStyleClass().add("editor-panel__card-wrapper");

        // 内容填充整个卡片
        if (content instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
            region.setMaxHeight(Double.MAX_VALUE);
        }
        // 工具/Todo 视图在 FXML 中声明为 visible=false managed=false，
        // 重建卡片时必须恢复可见性，否则内容不会显示
        content.setVisible(true);
        content.setManaged(true);
        card.getChildren().add(content);

        return card;
    }

    /**
     * 添加视图到容器
     */
    protected void addTab(EditorTab tab) {
        tabs.add(tab);
        if (!viewContainer.getChildren().contains(tab.card)) {
            viewContainer.getChildren().add(tab.card);
        }
        // 卡片填满 viewContainer
        tab.card.setMaxWidth(Double.MAX_VALUE);
        tab.card.setMaxHeight(Double.MAX_VALUE);
        tab.card.setVisible(false);
        tab.card.setManaged(false);
    }

    /**
     * 切换到指定视图
     */
    protected void selectTab(EditorTab tab) {
        // 记录进入当前视图前的活跃视图，用于关闭当前视图后回落
        if (activeTab != tab) {
            previousActiveTab = activeTab;
        }
        for (EditorTab t : tabs) {
            boolean active = (t == tab);
            t.card.setVisible(active);
            t.card.setManaged(active);
        }
        activeTab = tab;
        currentViewType = tab.viewType;
        notifyViewTypeChanged();
    }

    /**
     * 关闭视图
     */
    protected void closeTab(EditorTab tab) {
        tabs.remove(tab);
        viewContainer.getChildren().remove(tab.card);
        onTabClosed(tab);
        if (activeTab == tab) {
            if (tabs.isEmpty()) {
                activeTab = null;
                currentViewType = null;
                notifyViewTypeChanged();
                // 所有视图关闭后，从 SplitPane 移除面板，收回空间
                if (indexController != null) {
                    indexController.closeEditorPanel();
                } else {
                    hide();
                }
            } else if (previousActiveTab != null && tabs.contains(previousActiveTab)) {
                // 回落至关闭前活跃的视图，避免总是跳到列表末尾（如常驻的 todo/tool 单例）
                selectTab(previousActiveTab);
            } else {
                // 此前无活跃视图，收起面板而非强行激活列表末尾的视图
                activeTab = null;
                currentViewType = null;
                notifyViewTypeChanged();
                if (indexController != null) {
                    indexController.closeEditorPanel();
                } else {
                    hide();
                }
            }
        }
    }

    /**
     * 子类 hook：视图关闭时清理资源
     */
    protected void onTabClosed(EditorTab tab) {
        // 基类空实现
    }

    protected EditorTab findTabByType(ViewType type) {
        return tabs.stream().filter(t -> t.viewType == type).findFirst().orElse(null);
    }

    protected EditorTab findTabById(String id) {
        return tabs.stream().filter(t -> t.id.equals(id)).findFirst().orElse(null);
    }

    private void closeTabById(String id) {
        EditorTab tab = findTabById(id);
        if (tab != null) {
            closeTab(tab);
        }
    }

    // ===== 视图切换 =====

    public void showToolCallsView() {
        show();
        openSingletonTab(ViewType.TOOL_CALLS, toolCallsView);
    }

    public void showTodoView() {
        show();
        openSingletonTab(ViewType.TODO, todoView);
    }

    /**
     * 右上角工具按钮：当前工具视图开/关切换（打开时复用，关闭仅隐藏不重建）。
     */
    public void toggleToolCallsView() {
        toggleSingletonView(ViewType.TOOL_CALLS, toolCallsView);
    }

    /**
     * 右上角待办按钮：当前待办视图开/关切换（打开时复用，关闭仅隐藏不重建）。
     */
    public void toggleTodoView() {
        toggleSingletonView(ViewType.TODO, todoView);
    }

    public void showTerminalView() {
        // work 模式不支持终端视图
    }

    public void showDiffView() {
        // work 模式不支持 diff 视图
    }

    public void showFileView() {
        // work 模式不支持文件内容视图
    }

    // ===== 卡片管理 =====

    /**
     * 添加工具调用卡片到工具调用视图。
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
        openSingletonTab(ViewType.TODO, todoView);
        scrollToBottom(todoListView, todoListStickToBottom);
    }

    public void clearToolCalls() {
        toolCallNodes.clear();
    }

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

    public void openTerminal(Path workingDir) {
        // work 模式不支持终端
    }

    public void closeTerminal() {
        // work 模式不支持终端
    }

    public void showFileContent(Path filePath) {
        // work 模式不支持文件内容显示
    }

    public void showDiffInProjectView(FileDiff diff) {
        // work 模式不支持 diff 显示
    }

    // ===== 视图数据结构 =====

    protected static class EditorTab {
        final String id;
        final ViewType viewType;
        final Node content;
        final StackPane card;
        final Map<String, Object> userData;

        EditorTab(String id, ViewType viewType, Node content, StackPane card, Map<String, Object> userData) {
            this.id = id;
            this.viewType = viewType;
            this.content = content;
            this.card = card;
            this.userData = userData;
        }
    }
}
