package cn.bitloom.controller;

import cn.bitloom.controller.EditorPanelController.ViewType;
import cn.bitloom.project.ProjectInfo;
import cn.bitloom.constant.AgentMode;
import cn.bitloom.router.HomePageRouter;
import cn.bitloom.router.Router;
import cn.bitloom.store.Store;
import cn.bitloom.util.MarkdownFxRenderer;
import cn.bitloom.vm.CodeHomePageViewModel;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

@Slf4j
@Component
public class IndexController implements Initializable {

    @FXML
    @Getter
    private BorderPane rootContainer;
    @FXML
    private HBox sidebarHolder;
    @FXML
    private Region sidebarDragHandle;
    @FXML
    private HBox editorHolder;
    @FXML
    private Region editorDragHandle;
    @FXML
    @Getter
    private ButtonBarController buttonBarController;
    @FXML
    @Getter
    private SideBarController sideBarController;
    @FXML
    private VBox homePageSlot;
    @FXML
    private VBox editorPanelSlot;

    @Getter
    private final Router router;
    private final HomePageRouter homePageRouter;

    /** 拖拽条固定宽度（像素）：命中区宽度，足够宽以便轻松触发，又不明显挤占内容空间 */
    private static final double DRAG_HANDLE_WIDTH = 20;
    /** 拖拽把手的视觉尺寸（像素）：与命中区不同，把手仅是一小段居中圆角条，非整条高度 */
    private static final double DRAG_HANDLE_BAR_HEIGHT = 48;
    private static final double DRAG_HANDLE_BAR_WIDTH = 8;
    private static final double SIDEBAR_MIN_WIDTH = 200;
    private static final double SIDEBAR_MAX_WIDTH = 600;
    private static final double EDITOR_MIN_WIDTH = 320;
    private static final double EDITOR_MAX_WIDTH = 960;
    /** 中间主内容区最小宽度（与 FXML minWidth 一致），用于推算右栏可用上限 */
    private static final double MAIN_MIN_WIDTH = 360;
    /** 编辑器首次挂载时按窗口宽度的比例初始化（对齐原 SplitPane 0.72 分割的观感） */
    private static final double EDITOR_INIT_RATIO = 0.28;

    /** 侧边栏内容宽度（像素，拖拽后更新；显示时按此宽度放置） */
    private double savedSidebarWidth = 260.0;
    /** 编辑器面板内容宽度（像素，拖拽后更新；显示时按此宽度放置） */
    private double savedEditorWidth = 480.0;
    /** 用户是否手动拖拽过编辑器宽度（未拖拽时每次挂载按窗口比例重算） */
    private boolean editorWidthCustomized = false;

    public IndexController(@Lazy Router router, HomePageRouter homePageRouter) {
        this.router = router;
        this.homePageRouter = homePageRouter;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.buttonBarController.setIndexController(this);
        this.sideBarController.setIndexController(this);

        // 注入 Markdown 链接处理器：file:// 链接在项目视图中打开
        MarkdownFxRenderer.setLinkHandler(this::handleMarkdownLink);

        // 预加载两套 FXML 并绑定占位容器，初始模式由 Store.currentAgent 决定
        homePageRouter.bind(this, homePageSlot, editorPanelSlot);

        // 三区独立布局：侧边栏/编辑器固定像素宽度，显隐仅增删 BorderPane 槽位，互不影响宽度
        rootContainer.setRight(null);

        // 侧边栏初始隐藏（配合 SideBarController 默认隐藏）
        if (sideBarController != null && sideBarController.getSideBar() != null) {
            rootContainer.setLeft(null);
        }

        setupDragHandles();

        // 窗口缩放时按可用宽度重新夹紧两侧栏宽，保证不与中间区争抢导致溢出
        rootContainer.widthProperty().addListener((obs, oldW, newW) -> {
            if (rootContainer.getLeft() == sidebarHolder) {
                applySidebarWidth();
            }
            if (rootContainer.getRight() == editorHolder) {
                applyEditorWidth();
            }
        });

        this.initializeButtonBar();

        // 智能体切换联动：重建 ButtonBar + 关闭 coder 专有 EditorPanel 视图
        Store.currentAgent.addListener((obs, oldVal, newVal) -> Platform.runLater(() -> {
            if (router != null) {
                router.updateButtonBarForRoute(Store.currentRoute.get());
            }
            EditorPanelController editor = getEditorPanelController();
            if (editor != null && editor.isVisible()
                    && AgentMode.fromAgentId(newVal) != AgentMode.CODE) {
                editor.closeTerminal();
            }
            // 模式切换后重新绑定右上角按钮激活态（active editor 可能已更换）
            bindEditorViewTypeSync();
        }));

        // 初始化完成后绑定右上角按钮激活态联动（异步确保 editor/按钮已就绪）
        Platform.runLater(this::bindEditorViewTypeSync);
    }

    /**
     * 将当前 active 编辑器的视图类型变化同步到右侧 ViewButtonBar 按钮的蓝色激活态：
     * 视图打开时对应按钮蓝色高亮，全部关闭时清除高亮。
     */
    private void bindEditorViewTypeSync() {
        EditorPanelController editor = getEditorPanelController();
        if (editor == null) return;
        editor.setOnViewTypeChanged(this::applyViewButtonState);
        applyViewButtonState(editor.getCurrentViewType());
    }

    /** 根据当前视图类型设置按钮激活态（null 表示无激活视图，清除全部高亮） */
    private void applyViewButtonState(ViewType type) {
        if (buttonBarController == null) return;
        buttonBarController.setViewActive("terminalButton", type == ViewType.TERMINAL);
        buttonBarController.setViewActive("toolCallsButton", type == ViewType.TOOL_CALLS);
        buttonBarController.setViewActive("todoButton", type == ViewType.TODO);
    }

    private void initializeButtonBar() {
        if (this.router != null && this.buttonBarController != null) {
            this.router.updateButtonBarForRoute(cn.bitloom.router.RouteConfig.Path.HOME);
        }
    }

    public void navigate(String path) {
        if (router != null) {
            router.navigate(path);
        }
    }

    /**
     * 隐藏 homePageSlot 容器本身（非 home page 内容），释放垂直空间给其他页面。
     */
    public void hideHomePageSlot() {
        homePageSlot.setVisible(false);
        homePageSlot.setManaged(false);
    }

    /**
     * 显示 homePageSlot 容器本身。
     */
    public void showHomePageSlot() {
        homePageSlot.setVisible(true);
        homePageSlot.setManaged(true);
    }

    public void toggleSidebar() {
        if (sideBarController == null || sideBarController.getSideBar() == null) {
            return;
        }
        if (sideBarController.isSidebarVisible()) {
            // 折叠：宽度已由拖拽实时维护在 savedSidebarWidth，直接摘除槽位即可
            rootContainer.setLeft(null);
            sideBarController.hide();
            // 关闭后清除左侧侧边栏按钮激活态
            buttonBarController.setSidebarActive(false);
        } else {
            ensureSidebarVisible();
            // 展开后点亮左侧侧边栏按钮激活态
            buttonBarController.setSidebarActive(true);
        }
    }

    /**
     * 确保侧边栏在 BorderPane 左槽位可见。
     */
    private void ensureSidebarVisible() {
        if (sideBarController == null || sideBarController.getSideBar() == null) {
            return;
        }
        // 先恢复可见性并同步宽度，保证挂载时以目标宽度一次性布局，不产生闪烁帧
        sideBarController.show();
        applySidebarWidth();
        rootContainer.setLeft(sidebarHolder);
        // 侧边栏占用增加后编辑器可用上限变小，同步收缩避免溢出
        if (rootContainer.getRight() == editorHolder) {
            applyEditorWidth();
        }
    }

    /**
     * 将 savedSidebarWidth 应用到侧边栏容器与内容节点。
     * min/pref/max 三连锁死：无论内容自身 min 多大都不会撑开容器，
     * BorderPane 也无法在空间不足时将其压到 min 以下。
     * 拖拽条尺寸同步代码设置，不依赖 CSS。
     */
    private void applySidebarWidth() {
        double w = clamp(savedSidebarWidth, SIDEBAR_MIN_WIDTH, SIDEBAR_MAX_WIDTH);
        savedSidebarWidth = w;
        double holderW = w + DRAG_HANDLE_WIDTH;
        sidebarHolder.setMinWidth(holderW);
        sidebarHolder.setPrefWidth(holderW);
        sidebarHolder.setMaxWidth(holderW);
        setHandleWidth(sidebarDragHandle);
        var sideBar = sideBarController.getSideBar();
        sideBar.setMinWidth(w);
        sideBar.setPrefWidth(w);
        sideBar.setMaxWidth(w);
    }

    /**
     * 将 savedEditorWidth 应用到编辑器容器与内容节点。
     * 三连锁死后额外按窗口可用宽度收缩上限，保证右栏永不溢出窗口
     * （否则卡片右上角关闭按钮会被顶出可视区）。
     */
    private void applyEditorWidth() {
        double w = clamp(savedEditorWidth, EDITOR_MIN_WIDTH, computeEditorMaxWidth());
        savedEditorWidth = w;
        double holderW = w + DRAG_HANDLE_WIDTH;
        editorHolder.setMinWidth(holderW);
        editorHolder.setPrefWidth(holderW);
        editorHolder.setMaxWidth(holderW);
        setHandleWidth(editorDragHandle);
        editorPanelSlot.setMinWidth(w);
        editorPanelSlot.setPrefWidth(w);
        editorPanelSlot.setMaxWidth(w);
    }

    /** 拖拽条宽度代码化管理，并保证透明区域可拾取鼠标事件 */
    private void setHandleWidth(Region handle) {
        handle.setMinWidth(DRAG_HANDLE_WIDTH);
        handle.setPrefWidth(DRAG_HANDLE_WIDTH);
        handle.setMaxWidth(DRAG_HANDLE_WIDTH);
        handle.setPickOnBounds(true);
        applyDraggableHandle(handle);
    }

    /**
     * 在命中区中央动态绘制一把「竖向居中、固定尺寸」的圆角小把手。
     * 用像素 insets 计算而非 CSS 百分比：CSS 的 background-insets 百分比对拖到
     * 该条高度在布局中不可靠（会铺满整高），这里监听高度每次重算保证真正居中。
     * 默认透明，hover 显示半透明蓝，pressed 加强——视觉反馈交给代码而非 CSS 伪类。
     */
    private void applyDraggableHandle(Region handle) {
        Runnable paint = () -> {
            double h = handle.getHeight();
            if (h <= 0) {
                return;
            }
            double barH = Math.min(DRAG_HANDLE_BAR_HEIGHT, h);
            double y = (h - barH) / 2.0;
            double x = (DRAG_HANDLE_WIDTH - DRAG_HANDLE_BAR_WIDTH) / 2.0;
            Color fill = handle.isPressed()
                    ? Color.rgb(0, 113, 227, 0.6)
                    : handle.isHover() ? Color.rgb(0, 113, 227, 0.45) : Color.TRANSPARENT;
            handle.setBackground(new Background(new BackgroundFill(fill,
                    new CornerRadii(3), new Insets(y, x, y, x))));
        };
        handle.heightProperty().addListener((o, a, b) -> paint.run());
        handle.hoverProperty().addListener((o, a, b) -> paint.run());
        handle.pressedProperty().addListener((o, a, b) -> paint.run());
    }

    /**
     * 编辑器宽度上限：窗口宽度 - 侧边栏占用 - 中间区实际最小宽度。
     * 用中间区真实 minWidth（内容可能声明比常量更大的下限）而非静态常量，
     * 避免拖宽右栏时中间区被压穿、右栏滑出窗口边缘。
     */
    private double computeEditorMaxWidth() {
        double rootW = rootContainer.getWidth();
        if (rootW <= 0) {
            return EDITOR_MAX_WIDTH;
        }
        double sidebarOccupied = rootContainer.getLeft() == sidebarHolder
                ? savedSidebarWidth + DRAG_HANDLE_WIDTH : 0;
        double centerMin = MAIN_MIN_WIDTH;
        if (rootContainer.getCenter() instanceof Region center) {
            centerMin = Math.max(center.minWidth(-1), MAIN_MIN_WIDTH);
        }
        double available = rootW - sidebarOccupied - centerMin;
        return clamp(available, EDITOR_MIN_WIDTH, EDITOR_MAX_WIDTH);
    }

    /**
     * 注册侧边栏右缘 / 编辑器左缘的水平拖拽：按像素调节宽度并夹紧到上下限。
     * 采用「按下时记录起点 + 拖动时绝对偏移」方式，夹紧后不产生漂移。
     */
    private void setupDragHandles() {
        setupWidthDrag(sidebarDragHandle,
                () -> savedSidebarWidth,
                w -> {
                    savedSidebarWidth = clamp(w, SIDEBAR_MIN_WIDTH, SIDEBAR_MAX_WIDTH);
                    applySidebarWidth();
                },
                false);
        // 编辑器拖拽条在左缘：向左拖（负偏移）增宽，故取反
        setupWidthDrag(editorDragHandle,
                () -> savedEditorWidth,
                w -> {
                    editorWidthCustomized = true;
                    savedEditorWidth = clamp(w, EDITOR_MIN_WIDTH, computeEditorMaxWidth());
                    applyEditorWidth();
                },
                true);
    }

    /**
     * 注册侧边栏右缘 / 编辑器左缘的水平拖拽：按像素调节宽度并夹紧到上下限。
     * 拖动事件挂在 Scene 级过滤器上：即使拖拽过程中布局变化导致事件被
     * 其它组件重定向（如 WebView/CodeArea 的 DnD），也能稳定收到全部拖动事件。
     */
    private void setupWidthDrag(Region handle, DoubleSupplier currentWidth,
                                DoubleConsumer applyWidth, boolean invertOffset) {
        final double[] pressX = {0};
        final double[] pressW = {0};
        final boolean[] dragging = {false};
        // scene 级临时处理器（按下时挂载、释放时摘除）
        final javafx.event.EventHandler<javafx.scene.input.MouseEvent>[] dragHandler = new javafx.event.EventHandler[1];
        final javafx.event.EventHandler<javafx.scene.input.MouseEvent>[] releaseHandler = new javafx.event.EventHandler[1];

        handle.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
                return;
            }
            pressX[0] = e.getSceneX();
            pressW[0] = currentWidth.getAsDouble();
            dragging[0] = true;

            javafx.scene.Scene scene = handle.getScene();
            if (scene == null) {
                return;
            }
            dragHandler[0] = ev -> {
                if (!dragging[0]) {
                    return;
                }
                // 鼠标已释放但未收到 RELEASED（如移出窗口）时自动结束
                if (!ev.isPrimaryButtonDown()) {
                    dragging[0] = false;
                    scene.removeEventFilter(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, dragHandler[0]);
                    scene.removeEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, releaseHandler[0]);
                    return;
                }
                double offset = (ev.getSceneX() - pressX[0]) * (invertOffset ? -1 : 1);
                applyWidth.accept(pressW[0] + offset);
            };
            releaseHandler[0] = ev -> {
                dragging[0] = false;
                scene.removeEventFilter(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, dragHandler[0]);
                scene.removeEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, releaseHandler[0]);
            };
            scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, dragHandler[0]);
            scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, releaseHandler[0]);
            e.consume();
        });
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /**
     * Toggle 右侧编辑器面板
     */
    public void toggleEditorPanel() {
        EditorPanelController editor = getEditorPanelController();
        if (editor == null) return;
        if (editor.isVisible()) {
            closeEditorPanel();
        } else {
            ensureEditorVisible();
        }
    }

    // ===== 动态引用（由 HomePageRouter 维护） =====

    /**
     * 当前活跃的首页控制器（coder 或 work）
     */
    public AbstractHomePageController getHomePageController() {
        return homePageRouter.getActiveHomeController();
    }

    /**
     * 当前活跃的编辑器面板控制器（coder 或 work）
     */
    public EditorPanelController getEditorPanelController() {
        return homePageRouter.getActiveEditorController();
    }

    // ===== 编辑器面板管理 =====

    /**
     * 切换终端面板：当前若显示终端则关闭，否则打开（开/关切换语义）。
     */
    public void toggleTerminalPanel() {
        EditorPanelController editor = getEditorPanelController();
        if (editor == null) return;
        if (editor.isCurrentView(ViewType.TERMINAL)) {
            editor.closeTerminal();
        } else {
            ensureEditorVisible();
            editor.openTerminal(resolveWorkingDir());
        }
    }

    /**
     * 切换工具调用面板：单例视图，开/关切换，关闭仅隐藏不重建。
     */
    public void toggleToolCallsPanel() {
        EditorPanelController editor = getEditorPanelController();
        if (editor == null) return;
        editor.toggleToolCallsView();
    }

    /**
     * 切换待办面板：单例视图，开/关切换，关闭仅隐藏不重建。
     */
    public void toggleTodoPanel() {
        EditorPanelController editor = getEditorPanelController();
        if (editor == null) return;
        editor.toggleTodoView();
    }

    /**
     * 关闭编辑器面板（从 BorderPane 右槽位摘除）
     */
    public void closeEditorPanel() {
        EditorPanelController editor = getEditorPanelController();
        if (editor == null) return;
        rootContainer.setRight(null);
        editor.hide();
    }

    /**
     * 确保编辑器面板在 BorderPane 右槽位可见
     */
    public void ensureEditorVisible() {
        EditorPanelController editor = getEditorPanelController();
        if (editor == null) return;
        if (rootContainer.getRight() != editorHolder) {
            // 未手动定制过宽度时按窗口比例初始化，对齐原 SplitPane 分割观感
            if (!editorWidthCustomized && rootContainer.getWidth() > 0) {
                savedEditorWidth = clamp(rootContainer.getWidth() * EDITOR_INIT_RATIO,
                        EDITOR_MIN_WIDTH, EDITOR_MAX_WIDTH);
            }
            // 同步宽度后挂载，保证以目标宽度一次性布局，不产生闪烁帧
            applyEditorWidth();
            rootContainer.setRight(editorHolder);
        }
        editor.show();
    }

    /**
     * 关闭终端会话
     */
    public void closeTerminal() {
        EditorPanelController editor = getEditorPanelController();
        if (editor != null) {
            editor.closeTerminal();
        }
    }

    /**
     * Markdown 链接处理器：file:// 链接在右侧编辑器面板中打开，返回 true 表示已处理。
     * 非 file:// 链接返回 false，回退到默认浏览器打开。
     */
    private boolean handleMarkdownLink(String dest) {
        if (dest == null || !dest.startsWith("file:")) {
            return false;
        }
        try {
            URI uri = new URI(dest);
            // 剥离 fragment（如 #L123），Windows Path 不支持带 fragment 的 URI
            uri = new URI(uri.getScheme(), uri.getSchemeSpecificPart(), null);
            Path filePath = Path.of(uri);
            if (java.nio.file.Files.isRegularFile(filePath)) {
                Platform.runLater(() -> showFileInPanel(filePath));
                return true;
            }
        } catch (Exception e) {
            log.warn("无法解析 file:// 链接: {}", dest, e);
        }
        return false;
    }

    /**
     * 在编辑器面板显示文件内容（侧边栏目录树双击文件时调用）
     */
    public void showFileInPanel(Path file) {
        EditorPanelController editor = getEditorPanelController();
        if (editor == null) return;
        ensureEditorVisible();
        editor.showFileContent(file);
    }

    /**
     * 刷新首页的 diff 审查条（diff 看板中撤销/保留后调用）
     */
    public void refreshDiffReviewBar() {
        AbstractHomePageController home = getHomePageController();
        if (home != null) {
            home.refreshDiffReviewBarFromService();
        }
    }

    /**
     * 将选中文本以 tag 形式加入对话框输入框（编辑器面板 → 对话框联动）
     */
    public void addTextToChat(String text) {
        AbstractHomePageController home = getHomePageController();
        if (home != null) {
            home.appendTextToChat(text);
        }
    }

    /**
     * 将文件选中片段以 tag 形式加入对话框输入框（文件编辑器 → 对话框联动）
     */
    public void addFileRefToChat(Path filePath, int startLine, int endLine) {
        AbstractHomePageController home = getHomePageController();
        if (home != null) {
            home.appendFileRefToChat(filePath, startLine, endLine);
        }
    }

    /**
     * 解析当前工作目录（coder 模式返回当前项目路径，work 模式返回 null）
     */
    private Path resolveWorkingDir() {
        AbstractHomePageController home = getHomePageController();
        if (home != null && home.getViewModel() instanceof CodeHomePageViewModel coderVm) {
            ProjectInfo project = coderVm.getCurrentProject();
            if (project != null) {
                return Path.of(project.path());
            }
        }
        return null;
    }
}
