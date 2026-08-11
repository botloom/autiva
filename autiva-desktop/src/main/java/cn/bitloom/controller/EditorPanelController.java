package cn.bitloom.controller;

import cn.bitloom.agentic.tool.file.FileDiff;
import cn.bitloom.node.svg.SvgImageView;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
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
 * 采用 Tab 栏管理多视图：面板顶部有 tab 栏，最左侧"+"按钮下拉可添加视图。
 * 工具/Todo 为单例 tab，终端/文件/DIFF（coder 模式）可多开。
 * tab 关闭按钮在左侧。
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
    protected HBox tabBar;
    @FXML
    protected ScrollPane tabScroll;
    @FXML
    protected Button addTabButton;
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

    // ===== Tab 管理 =====
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
        setupRoundedClip();
        setupToolCallsListView();
        setupTodoListView();
        setupAddTabButton();
        setupTabScroll();
    }

    /**
     * tab 栏横向滚动：鼠标滚轮控制水平滚动
     */
    private void setupTabScroll() {
        tabScroll.setFitToHeight(true);
        tabScroll.addEventFilter(ScrollEvent.SCROLL, e -> {
            double delta = (e.getDeltaX() != 0) ? e.getDeltaX() : e.getDeltaY();
            if (delta == 0) return;
            double contentWidth = tabScroll.getContent().getLayoutBounds().getWidth();
            double viewportWidth = tabScroll.getViewportBounds().getWidth();
            double maxScroll = contentWidth - viewportWidth;
            if (maxScroll <= 0) return;
            // delta 是像素，转换为 [0,1] 范围的比例
            double newH = tabScroll.getHvalue() - delta / maxScroll;
            newH = Math.max(0, Math.min(1, newH));
            tabScroll.setHvalue(newH);
            e.consume();
        });
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
     * 绑定"+"按钮的下拉菜单
     */
    private void setupAddTabButton() {
        addTabButton.setOnAction(e -> {
            ContextMenu menu = buildAddTabMenu();
            menu.show(addTabButton, javafx.geometry.Side.BOTTOM, 0, 0);
        });
    }

    /**
     * 构建"+"下拉菜单，子类可 override 扩展选项
     */
    protected ContextMenu buildAddTabMenu() {
        ContextMenu menu = new ContextMenu();
        menu.getItems().add(createMenuItem("工具视图", () -> openSingleTab(ViewType.TOOL_CALLS, "工具视图", toolCallsView)));
        menu.getItems().add(createMenuItem("待办事项", () -> openSingleTab(ViewType.TODO, "待办事项", todoView)));
        return menu;
    }

    protected MenuItem createMenuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
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

    // ===== Tab 管理核心方法 =====

    /**
     * 打开单例 Tab（工具/Todo）：若已存在则激活，否则创建
     */
    protected void openSingleTab(ViewType type, String title, Node content) {
        EditorTab existing = findTabByType(type);
        if (existing != null) {
            selectTab(existing);
            return;
        }
        EditorTab tab = createTab(type, title, content, true);
        addTab(tab);
        selectTab(tab);
    }

    /**
     * 创建 Tab 对象（header + content）
     */
    protected EditorTab createTab(ViewType type, String title, Node content, boolean closeable) {
        String id = type.name().toLowerCase() + "-" + (tabIdCounter++);
        HBox header = buildTabHeader(title, closeable, id);
        return new EditorTab(id, type, title, content, header, new HashMap<>());
    }

    /**
     * 构建 Tab 头部：[关闭按钮] [标题]（关闭按钮在左侧）
     */
    private HBox buildTabHeader(String title, boolean closeable, String tabId) {
        HBox header = new HBox(4);
        header.getStyleClass().add("editor-panel__tab");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setUserData(tabId);

        if (closeable) {
            Button closeBtn = new Button();
            closeBtn.getStyleClass().add("editor-panel__tab-close");
            SvgImageView closeIcon = new SvgImageView();
            closeIcon.setFitWidth(12);
            closeIcon.setFitHeight(12);
            closeIcon.setSvgPath("/cn/bitloom/images/close.svg");
            closeBtn.setGraphic(closeIcon);
            closeBtn.setOnAction(e -> closeTabById(tabId));
            header.getChildren().add(closeBtn);
        }

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("editor-panel__tab-title");
        header.getChildren().add(titleLabel);

        header.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                selectTabById(tabId);
            }
        });
        return header;
    }

    /**
     * 添加 Tab 到 tab 栏和视图容器
     */
    protected void addTab(EditorTab tab) {
        tabs.add(tab);
        tabBar.getChildren().add(tab.header);
        if (!viewContainer.getChildren().contains(tab.content)) {
            viewContainer.getChildren().add(tab.content);
        }
        tab.content.setVisible(false);
        tab.content.setManaged(false);
    }

    /**
     * 切换到指定 Tab
     */
    protected void selectTab(EditorTab tab) {
        for (EditorTab t : tabs) {
            boolean active = (t == tab);
            t.content.setVisible(active);
            t.content.setManaged(active);
            t.header.getStyleClass().removeAll("editor-panel__tab--active");
            if (active) {
                t.header.getStyleClass().add("editor-panel__tab--active");
            }
        }
        activeTab = tab;
        currentViewType = tab.viewType;
    }

    /**
     * 关闭 Tab
     */
    protected void closeTab(EditorTab tab) {
        tabs.remove(tab);
        tabBar.getChildren().remove(tab.header);
        viewContainer.getChildren().remove(tab.content);
        onTabClosed(tab);
        if (activeTab == tab) {
            if (tabs.isEmpty()) {
                activeTab = null;
                currentViewType = null;
            } else {
                selectTab(tabs.get(tabs.size() - 1));
            }
        }
    }

    /**
     * 子类 hook：Tab 关闭时清理资源
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

    private void selectTabById(String id) {
        EditorTab tab = findTabById(id);
        if (tab != null) {
            selectTab(tab);
        }
    }

    private void closeTabById(String id) {
        EditorTab tab = findTabById(id);
        if (tab != null) {
            closeTab(tab);
        }
    }

    // ===== 视图切换 =====

    public void showToolCallsView() {
        openSingleTab(ViewType.TOOL_CALLS, "工具", toolCallsView);
    }

    public void showTodoView() {
        openSingleTab(ViewType.TODO, "Todo", todoView);
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
        openSingleTab(ViewType.TODO, "Todo", todoView);
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

    // ===== Tab 数据结构 =====

    protected static class EditorTab {
        final String id;
        final ViewType viewType;
        final String title;
        final Node content;
        final HBox header;
        final Map<String, Object> userData;

        EditorTab(String id, ViewType viewType, String title, Node content, HBox header, Map<String, Object> userData) {
            this.id = id;
            this.viewType = viewType;
            this.title = title;
            this.content = content;
            this.header = header;
            this.userData = userData;
        }
    }
}
