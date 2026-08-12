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
     * 关闭指定类型的单例视图（工具/Todo 视图 tab 栏的关闭按钮）。
     */
    protected void closeViewByType(ViewType type) {
        EditorTab tab = findTabByType(type);
        if (tab != null) {
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
     * 打开单例视图（工具/Todo）：若已存在则激活，否则创建卡片
     */
    protected void openSingleTab(ViewType type, Node content) {
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
        for (EditorTab t : tabs) {
            boolean active = (t == tab);
            t.card.setVisible(active);
            t.card.setManaged(active);
        }
        activeTab = tab;
        currentViewType = tab.viewType;
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
                // 所有视图关闭后，从 SplitPane 移除面板，收回空间
                if (indexController != null) {
                    indexController.closeEditorPanel();
                } else {
                    hide();
                }
            } else {
                selectTab(tabs.get(tabs.size() - 1));
            }
        }
    }

    /**
     * 关闭全部已打开的视图（用于右上角按钮切换时先清空旧视图）。
     * 关闭最后一个 tab 时会收起面板，调用方如需继续打开新视图需自行保证面板可见。
     */
    protected void closeAllTabs() {
        var snapshot = new java.util.ArrayList<>(tabs);
        for (EditorTab tab : snapshot) {
            closeTab(tab);
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
        openSingleTab(ViewType.TOOL_CALLS, toolCallsView);
    }

    public void showTodoView() {
        show();
        openSingleTab(ViewType.TODO, todoView);
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
        openSingleTab(ViewType.TODO, todoView);
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
