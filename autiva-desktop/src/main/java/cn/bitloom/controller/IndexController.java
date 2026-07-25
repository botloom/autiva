package cn.bitloom.controller;

import cn.bitloom.agentic.diff.FileDiff;
import cn.bitloom.agentic.project.ProjectInfo;
import cn.bitloom.controller.EditorPanelController.ViewType;
import cn.bitloom.router.Router;
import cn.bitloom.store.Store;
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

import java.io.File;
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
    @Getter
    private ButtonBarController buttonBarController;
    @FXML
    @Getter
    private SideBarController sideBarController;
    @FXML
    @Getter
    private HomePageController homePageController;
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
    private GepPageController gepPageController;
    @FXML
    @Getter
    private TaskPageController taskPageController;
    @FXML
    @Getter
    private EditorPanelController editorPanelController;

    @Getter
    private final Router router;

    private double savedDividerPos = 0.72;

    public IndexController(@Lazy Router router) {
        this.router = router;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.buttonBarController.setIndexController(this);
        this.sideBarController.setIndexController(this);
        this.homePageController.setIndexController(this);
        this.agentPageController.setIndexController(this);
        this.settingsPageController.setIndexController(this);
        this.skillPageController.setIndexController(this);
        this.gepPageController.setIndexController(this);
        this.taskPageController.setIndexController(this);
        this.editorPanelController.setIndexController(this);

        // 编辑器面板初始从 SplitPane 移除（默认隐藏）
        VBox editorPanel = editorPanelController.getEditorPanel();
        if (mainSplit.getItems().contains(editorPanel)) {
            mainSplit.getItems().remove(editorPanel);
        }

        this.initializeButtonBar();

        // 智能体切换联动：重建 ButtonBar 按钮 + 关闭 coder 专有 EditorPanel 视图（终端/项目）
        // SideBar 历史列表刷新由 SideBarController 自己监听触发，这里不重复
        Store.currentAgent.addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                // 1. 重建 ButtonBar（按当前智能体类型决定按钮集合）
                if (router != null) {
                    router.updateButtonBarForRoute(Store.currentRoute.get());
                }
                // 2. 切换到非 coder 时若 EditorPanel 正显示 TERMINAL/PROJECT 视图，自动关闭
                if (editorPanelController != null && editorPanelController.isVisible()
                        && !"coder".equals(newVal)) {
                    ViewType vt = editorPanelController.getCurrentViewType();
                    if (vt == ViewType.TERMINAL || vt == ViewType.PROJECT) {
                        closeEditorPanel();
                    }
                }
            });
        });
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

    public void toggleSidebar() {
        if (sideBarController != null) {
            if (sideBarController.isSidebarVisible()) {
                sideBarController.hide();
            } else {
                sideBarController.show();
            }
        }
    }

    // ===== 编辑器面板管理 =====

    /**
     * 切换终端面板（toggle）：相同视图再次点击则关闭
     */
    public void toggleTerminalPanel() {
        if (editorPanelController == null) {
            return;
        }
        if (editorPanelController.isVisible()
                && editorPanelController.getCurrentViewType() == ViewType.TERMINAL) {
            closeEditorPanel();
            return;
        }
        ensureEditorVisible();
        editorPanelController.openTerminal(resolveWorkingDir());
    }

    /**
     * 切换项目面板（toggle）：相同视图再次点击则关闭
     */
    public void toggleProjectPanel() {
        if (editorPanelController == null) {
            return;
        }
        if (editorPanelController.isVisible()
                && editorPanelController.getCurrentViewType() == ViewType.PROJECT) {
            closeEditorPanel();
            return;
        }
        ensureEditorVisible();
        editorPanelController.showProjectView();
    }

    /**
     * 切换工具调用面板（toggle）：相同视图再次点击则关闭
     */
    public void toggleToolCallsPanel() {
        if (editorPanelController == null) {
            return;
        }
        if (editorPanelController.isVisible()
                && editorPanelController.getCurrentViewType() == ViewType.TOOL_CALLS) {
            closeEditorPanel();
            return;
        }
        ensureEditorVisible();
        editorPanelController.showToolCallsView();
    }

    /**
     * 切换待办面板（toggle）：相同视图再次点击则关闭
     */
    public void toggleTodoPanel() {
        if (editorPanelController == null) {
            return;
        }
        if (editorPanelController.isVisible()
                && editorPanelController.getCurrentViewType() == ViewType.TODO) {
            closeEditorPanel();
            return;
        }
        ensureEditorVisible();
        editorPanelController.showTodoView();
    }

    /**
     * 关闭编辑器面板（保存 divider 位置并从 SplitPane 移除）
     */
    public void closeEditorPanel() {
        if (editorPanelController == null) {
            return;
        }
        VBox editorPanel = editorPanelController.getEditorPanel();
        if (mainSplit.getItems().contains(editorPanel)) {
            if (mainSplit.getDividers().size() > 0) {
                savedDividerPos = mainSplit.getDividerPositions()[0];
            }
            mainSplit.getItems().remove(editorPanel);
        }
        editorPanelController.hide();
    }

    /**
     * 确保编辑器面板在 SplitPane 中可见
     */
    private void ensureEditorVisible() {
        VBox editorPanel = editorPanelController.getEditorPanel();
        if (!mainSplit.getItems().contains(editorPanel)) {
            mainSplit.getItems().add(editorPanel);
            Platform.runLater(() -> mainSplit.setDividerPosition(0, savedDividerPos));
        }
        editorPanelController.show();
    }

    /**
     * 关闭终端会话
     */
    public void closeTerminal() {
        if (editorPanelController != null) {
            editorPanelController.closeTerminal();
        }
    }

    /**
     * 在编辑器面板显示文件内容
     */
    public void showFileInPanel(Path file) {
        if (editorPanelController == null) {
            return;
        }
        ensureEditorVisible();
        editorPanelController.showFileContent(file);
    }

    /**
     * 在项目视图中显示指定文件的 diff（点击对话框上方的 diff 文件卡片时调用）
     */
    public void showDiffInProjectView(FileDiff diff) {
        if (editorPanelController == null) {
            return;
        }
        ensureEditorVisible();
        editorPanelController.showDiffInProjectView(diff);
    }

    /**
     * 更新当前项目（通知编辑器面板构建目录树）
     */
    public void updateCurrentProject(ProjectInfo project) {
        if (editorPanelController != null) {
            editorPanelController.setCurrentProject(project);
        }
    }

    /**
     * 将选中文本追加到对话框输入框（编辑器面板 → 对话框联动）
     */
    public void addTextToChat(String text) {
        if (homePageController != null) {
            homePageController.appendTextToChat(text);
        }
    }

    /**
     * 将文件添加到对话框附件（编辑器面板拖拽 → 对话框联动）
     */
    public void addFileToChat(File file) {
        if (homePageController != null) {
            homePageController.addAttachedFile(file);
        }
    }

    /**
     * 解析当前工作目录（当前项目路径或 null）
     */
    private Path resolveWorkingDir() {
        if (homePageController != null && homePageController.getViewModel() != null) {
            ProjectInfo project = homePageController.getViewModel().getCurrentProject();
            if (project != null) {
                return Path.of(project.path());
            }
        }
        return null;
    }

}
