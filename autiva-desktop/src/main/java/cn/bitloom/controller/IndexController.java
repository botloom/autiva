package cn.bitloom.controller;

import cn.bitloom.agentic.tool.file.FileDiff;
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
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;

@Slf4j
@Component
public class IndexController implements Initializable {

    @FXML
    @Getter
    private BorderPane rootContainer;
    @FXML
    private SplitPane mainSplit;
    @FXML
    private SplitPane leftSplit;
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
    @FXML
    @Getter
    private AgentPageController agentPageController;
    @FXML
    @Getter
    private SettingsPageController settingsPageController;
    @FXML
    @Getter
    private SkillPageController skillPageController;
    @FXML
    @Getter
    private TaskPageController taskPageController;

    @Getter
    private final Router router;
    private final HomePageRouter homePageRouter;

    private double savedDividerPos = 0.72;
    /** 侧边栏期望的像素宽度（窗口缩放时保持不变，仅拖拽改动） */
    private double sidebarPixels = 250;
    private boolean sidebarWidthListenerInstalled = false;
    private javafx.scene.control.SplitPane.Divider trackedDivider = null;

    public IndexController(@Lazy Router router, HomePageRouter homePageRouter) {
        this.router = router;
        this.homePageRouter = homePageRouter;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.buttonBarController.setIndexController(this);
        this.sideBarController.setIndexController(this);
        this.agentPageController.setIndexController(this);
        this.settingsPageController.setIndexController(this);
        this.skillPageController.setIndexController(this);
        this.taskPageController.setIndexController(this);

        // 注入 Markdown 链接处理器：file:// 链接在项目视图中打开
        MarkdownFxRenderer.setLinkHandler(this::handleMarkdownLink);

        // 预加载两套 FXML 并绑定占位容器，初始模式由 Store.currentAgent 决定
        homePageRouter.bind(this, homePageSlot, editorPanelSlot);

        // 编辑器面板初始从 SplitPane 移除（默认隐藏）
        mainSplit.getItems().remove(editorPanelSlot);

        // 侧边栏初始从内部 SplitPane 移除（配合 SideBarController 默认隐藏）
        if (sideBarController != null && sideBarController.getSideBar() != null) {
            leftSplit.getItems().remove(sideBarController.getSideBar());
        }

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
        }));
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
        var sideBar = sideBarController.getSideBar();
        if (sideBarController.isSidebarVisible()) {
            // 折叠：记录当前侧边栏像素宽度并从内部 SplitPane 移除
            syncSidebarPixels();
            leftSplit.getItems().remove(sideBar);
            sideBarController.hide();
        } else {
            ensureSidebarVisible();
        }
    }

    /**
     * 确保侧边栏在内部 SplitPane 中可见，并按其像素宽度定位分隔线。
     */
    private void ensureSidebarVisible() {
        if (sideBarController == null || sideBarController.getSideBar() == null) {
            return;
        }
        var sideBar = sideBarController.getSideBar();
        installSidebarListeners();
        if (!leftSplit.getItems().contains(sideBar)) {
            leftSplit.getItems().add(0, sideBar);
            Platform.runLater(this::applySidebarPixels);
        }
        sideBarController.show();
    }

    /**
     * 安装监听：容器宽度变化时维持侧边栏固定像素宽度；分隔线位置变化时记录用户拖拽后的像素宽度。
     */
    private void installSidebarListeners() {
        if (!sidebarWidthListenerInstalled) {
            sidebarWidthListenerInstalled = true;
            leftSplit.widthProperty().addListener((obs, o, w) -> {
                if (w != null && w.doubleValue() > 0 && leftSplit.getItems().size() >= 2) {
                    applySidebarPixels();
                }
            });
        }
        trackSidebarDivider();
    }

    /**
     * 跟随当前 divider 位置的绝对值变化，记录为侧边栏像素宽度。
     */
    private void trackSidebarDivider() {
        if (leftSplit.getDividers().isEmpty()) {
            return;
        }
        var divider = leftSplit.getDividers().get(0);
        divider.positionProperty().addListener((obs, o, n) -> {
            double w = leftSplit.getWidth();
            if (w > 0 && n != null) {
                sidebarPixels = n.doubleValue() * w;
            }
        });
    }

    /**
     * 按当前侧边栏期望像素宽度设置分隔线位置（使窗口缩放时侧边栏宽度不变）。
     */
    private void applySidebarPixels() {
        if (leftSplit.getItems().size() < 2 || leftSplit.getDividers().isEmpty()) {
            return;
        }
        double w = leftSplit.getWidth();
        if (w <= 0) {
            return;
        }
        double d = Math.min(0.95, sidebarPixels / w);
        leftSplit.setDividerPosition(0, d);
    }

    /**
     * 将当前分隔线位置同步为侧边栏像素宽度。
     */
    private void syncSidebarPixels() {
        double w = leftSplit.getWidth();
        if (!leftSplit.getDividers().isEmpty() && w > 0) {
            sidebarPixels = leftSplit.getDividerPositions()[0] * w;
        }
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
     * 打开终端面板
     */
    public void toggleTerminalPanel() {
        EditorPanelController editor = getEditorPanelController();
        if (editor == null) return;
        ensureEditorVisible();
        editor.openTerminal(resolveWorkingDir());
    }

    /**
     * 打开工具调用面板
     */
    public void toggleToolCallsPanel() {
        EditorPanelController editor = getEditorPanelController();
        if (editor == null) return;
        ensureEditorVisible();
        editor.showToolCallsView();
    }

    /**
     * 打开待办面板
     */
    public void toggleTodoPanel() {
        EditorPanelController editor = getEditorPanelController();
        if (editor == null) return;
        ensureEditorVisible();
        editor.showTodoView();
    }

    /**
     * 关闭编辑器面板（保存 divider 位置并从 SplitPane 移除）
     */
    public void closeEditorPanel() {
        EditorPanelController editor = getEditorPanelController();
        if (editor == null) return;
        if (mainSplit.getItems().contains(editorPanelSlot)) {
            if (!mainSplit.getDividers().isEmpty()) {
                savedDividerPos = mainSplit.getDividerPositions()[0];
            }
            mainSplit.getItems().remove(editorPanelSlot);
        }
        editor.hide();
    }

    /**
     * 确保编辑器面板在 SplitPane 中可见
     */
    public void ensureEditorVisible() {
        EditorPanelController editor = getEditorPanelController();
        if (editor == null) return;
        if (!mainSplit.getItems().contains(editorPanelSlot)) {
            mainSplit.getItems().add(editorPanelSlot);
            Platform.runLater(() -> mainSplit.setDividerPosition(0, savedDividerPos));
        }
        // editorPanelSlot 在 FXML 中初始 visible=false/managed=false，需要显式恢复
        editorPanelSlot.setVisible(true);
        editorPanelSlot.setManaged(true);
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
     * 在项目视图中显示指定文件的 diff（点击对话框上方的 diff 文件卡片时调用）
     */
    public void showDiffInProjectView(FileDiff diff) {
        EditorPanelController editor = getEditorPanelController();
        if (editor == null) return;
        ensureEditorVisible();
        editor.showDiffInProjectView(diff);
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
