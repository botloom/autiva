package cn.bitloom.controller;

import cn.bitloom.node.svg.SvgImageView;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.Getter;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 子 Tab 容器：用于终端/文件等需要内部多 tab 管理的视图。
 * <p>
 * 结构：顶部子 tab 栏（含 "+" 按钮）+ 内容区域。
 * 每个子 tab 头部为 [标题] [关闭X]（关闭按钮在右侧）。
 */
public class SubTabContainer {

    private final VBox root = new VBox();
    private final HBox subTabBar = new HBox();
    private final HBox tabsHBox = new HBox();
    private final ScrollPane tabScroll = new ScrollPane(tabsHBox);
    private final StackPane contentArea = new StackPane();
    private final Button addButton = new Button();
    private final Button closeViewButton = new Button();

    private final ObservableList<SubTab> subTabs = FXCollections.observableArrayList();
    @Getter
    private SubTab activeSubTab = null;
    private int subTabIdCounter = 0;

    private final Consumer<SubTabContainer> onAddTab;
    private final Consumer<SubTab> onSubTabClosed;
    private Runnable onEmpty;
    private Runnable onCloseView;

    public SubTabContainer(Consumer<SubTabContainer> onAddTab,
                           Consumer<SubTab> onSubTabClosed) {
        this.onAddTab = onAddTab;
        this.onSubTabClosed = onSubTabClosed;
        setupUI();
    }

    public void setOnEmpty(Runnable onEmpty) {
        this.onEmpty = onEmpty;
    }

    /**
     * 设置关闭整个视图（terminal/file）的回调。
     */
    public void setOnCloseView(Runnable onCloseView) {
        this.onCloseView = onCloseView;
    }

    private void setupUI() {
        root.getStyleClass().add("editor-panel__sub-tab-container");
        VBox.setVgrow(root, Priority.ALWAYS);

        // 外层 tab 栏：左侧可横向滑动的 tab 区 + 右侧固定按钮区
        subTabBar.getStyleClass().add("editor-panel__sub-tab-bar");
        subTabBar.setAlignment(Pos.CENTER_LEFT);
        subTabBar.setSpacing(2);

        // tab 区：tab 标题超出宽度时横向滚动，不隐藏
        tabsHBox.getStyleClass().add("editor-panel__sub-tabs");
        tabsHBox.setAlignment(Pos.CENTER_LEFT);
        tabsHBox.setSpacing(2);

        tabScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        tabScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        // fitToHeight=true 将内容撑满整个栏高，配合 tabsHBox 的 CENTER_LEFT 使 tab 标签垂直居中。
        // hbar 用 NEVER 不渲染滚动条，避免其占用视口高度导致内容高度随溢出而跳动。
        tabScroll.setFitToHeight(true);
        // minWidth=0 允许 tab 溢出时将滚动区压缩到面板可用宽度
        tabScroll.setMinWidth(0);
        tabScroll.getStyleClass().add("editor-panel__sub-tab-scroll");

        // 增强横向滚动灵敏度：滚轮/触控板纵向滚动转为横向滚动
        tabScroll.addEventFilter(ScrollEvent.SCROLL, e -> {
            double delta = e.getDeltaX() != 0 ? e.getDeltaX() : e.getDeltaY();
            if (delta == 0) return;
            double viewportW = tabScroll.getViewportBounds().getWidth();
            double contentW = tabsHBox.getWidth();
            if (contentW <= viewportW) return;
            double range = contentW - viewportW;
            double step = delta / range;
            double hv = tabScroll.getHvalue() - step;
            tabScroll.setHvalue(Math.max(0, Math.min(1, hv)));
            e.consume();
        });

        // 弹性占位：tab 少时把关闭按钮推到最右，tab 溢出时被压缩到 0
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 关闭整个视图的按钮：样式与添加按钮完全一致（大小/颜色/阴影）
        closeViewButton.getStyleClass().add("editor-panel__sub-tab-add");
        SvgImageView closeViewIcon = new SvgImageView();
        closeViewIcon.setFitWidth(14);
        closeViewIcon.setFitHeight(14);
        closeViewIcon.setSvgPath("/cn/bitloom/images/close.svg");
        closeViewButton.setGraphic(closeViewIcon);
        closeViewButton.setOnAction(e -> {
            if (onCloseView != null) {
                onCloseView.run();
            }
        });

        // "+" 按钮
        addButton.getStyleClass().add("editor-panel__sub-tab-add");
        SvgImageView plusIcon = new SvgImageView();
        plusIcon.setFitWidth(14);
        plusIcon.setFitHeight(14);
        plusIcon.setSvgPath("/cn/bitloom/images/tab-add.svg");
        addButton.setGraphic(plusIcon);
        addButton.setOnAction(e -> {
            if (onAddTab != null) {
                onAddTab.accept(this);
            }
        });

        contentArea.getStyleClass().add("editor-panel__view");
        VBox.setVgrow(contentArea, Priority.ALWAYS);

        subTabBar.getChildren().addAll(tabScroll, addButton, spacer, closeViewButton);
        root.getChildren().addAll(subTabBar, contentArea);
    }

    public Node getView() {
        return root;
    }

    /**
     * 添加子 Tab
     */
    public SubTab addSubTab(String title, Node content) {
        String id = "sub-" + (subTabIdCounter++);
        HBox header = buildSubTabHeader(title, id);
        SubTab tab = new SubTab(id, title, content, header, new java.util.HashMap<>());
        subTabs.add(tab);
        tabsHBox.getChildren().add(header);
        if (!contentArea.getChildren().contains(content)) {
            contentArea.getChildren().add(content);
        }
        content.setVisible(false);
        content.setManaged(false);
        selectSubTab(tab);
        return tab;
    }

    /**
     * 构建子 Tab 头部：[标题] [关闭X]（关闭按钮在右侧）
     */
    private HBox buildSubTabHeader(String title, String tabId) {
        HBox header = new HBox(4);
        header.getStyleClass().add("editor-panel__sub-tab");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setUserData(tabId);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("editor-panel__sub-tab-title");
        header.getChildren().add(titleLabel);

        Button closeBtn = new Button();
        closeBtn.getStyleClass().add("editor-panel__sub-tab-close");
        SvgImageView closeIcon = new SvgImageView();
        closeIcon.setFitWidth(10);
        closeIcon.setFitHeight(10);
        closeIcon.setSvgPath("/cn/bitloom/images/close.svg");
        closeBtn.setGraphic(closeIcon);
        closeBtn.setOnAction(e -> closeSubTabById(tabId));
        header.getChildren().add(closeBtn);

        header.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                selectSubTabById(tabId);
            }
        });
        return header;
    }

    /**
     * 切换到指定子 Tab
     */
    public void selectSubTab(SubTab tab) {
        for (SubTab t : subTabs) {
            boolean active = (t == tab);
            t.content.setVisible(active);
            t.content.setManaged(active);
            t.header.getStyleClass().removeAll("editor-panel__sub-tab--active");
            if (active) {
                t.header.getStyleClass().add("editor-panel__sub-tab--active");
            }
        }
        activeSubTab = tab;
    }

    public void selectSubTabById(String id) {
        SubTab tab = findSubTabById(id);
        if (tab != null) {
            selectSubTab(tab);
        }
    }

    public SubTab findSubTabById(String id) {
        return subTabs.stream().filter(t -> t.id.equals(id)).findFirst().orElse(null);
    }

    /**
     * 查找与 userData 中 key 对应值匹配的子 Tab
     */
    public SubTab findSubTabByUserData(String key, Object value) {
        return subTabs.stream()
                .filter(t -> value.equals(t.userData.get(key)))
                .findFirst().orElse(null);
    }

    /**
     * 关闭子 Tab
     */
    public void closeSubTab(SubTab tab) {
        subTabs.remove(tab);
        tabsHBox.getChildren().remove(tab.header);
        contentArea.getChildren().remove(tab.content);
        if (onSubTabClosed != null) {
            onSubTabClosed.accept(tab);
        }
        if (activeSubTab == tab) {
            if (subTabs.isEmpty()) {
                activeSubTab = null;
            } else {
                selectSubTab(subTabs.get(subTabs.size() - 1));
            }
        }
        // 子 tab 全部关闭时触发 onEmpty 回调
        if (subTabs.isEmpty() && onEmpty != null) {
            onEmpty.run();
        }
    }

    private void closeSubTabById(String id) {
        SubTab tab = findSubTabById(id);
        if (tab != null) {
            closeSubTab(tab);
        }
    }

    public ObservableList<SubTab> getSubTabs() {
        return subTabs;
    }

    public boolean isEmpty() {
        return subTabs.isEmpty();
    }

    /**
     * 更新子 Tab 标题
     */
    public void updateSubTabTitle(SubTab tab, String newTitle) {
        if (tab == null) return;
        for (Node node : tab.header.getChildren()) {
            if (node instanceof Label label) {
                label.setText(newTitle);
                break;
            }
        }
    }

    /**
     * 子 Tab 数据结构
     */
    public static class SubTab {
        public final String id;
        public final String title;
        public final Node content;
        public final HBox header;
        public final Map<String, Object> userData;

        SubTab(String id, String title, Node content, HBox header, Map<String, Object> userData) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.header = header;
            this.userData = userData;
        }
    }
}
