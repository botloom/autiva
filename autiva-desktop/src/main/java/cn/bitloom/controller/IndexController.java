package cn.bitloom.controller;

import cn.bitloom.agentic.diff.FileDiff;
import cn.bitloom.agentic.project.ProjectInfo;
import cn.bitloom.controller.EditorPanelController.ViewType;
import cn.bitloom.router.Router;
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
     * 切换变更面板（toggle）：相同视图再次点击则关闭
     */
    public void toggleChangesPanel() {
        if (editorPanelController == null) {
            return;
        }
        if (editorPanelController.isVisible()
                && editorPanelController.getCurrentViewType() == ViewType.CHANGES) {
            closeEditorPanel();
            return;
        }
        ensureEditorVisible();
        editorPanelController.showChangesView();
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
     * 打开 diff 对比视图
     */
    public void showDiffView(FileDiff diff) {
        if (editorPanelController == null) {
            return;
        }
        ensureEditorVisible();
        editorPanelController.showDiffView(diff);
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
