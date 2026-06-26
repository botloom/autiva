package cn.bitloom.controller;

import cn.bitloom.agentic.diff.FileDiff;
import cn.bitloom.agentic.project.ProjectInfo;
import cn.bitloom.router.Router;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.BorderPane;
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

        // 编辑器面板默认隐藏（FXML 中已设置 visible=false managed=false）
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

    /**
     * 打开编辑器面板并聚焦终端标签页（供 HomePageController 的"编辑器"按钮调用）
     */
    public void openEditor() {
        if (editorPanelController == null) {
            return;
        }
        Path workingDir = resolveWorkingDir();
        editorPanelController.show();
        editorPanelController.openTerminal(workingDir);
    }

    /**
     * 打开终端面板（委托给编辑器面板）
     */
    public void openTerminal() {
        if (editorPanelController == null) {
            return;
        }
        editorPanelController.openTerminal(resolveWorkingDir());
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
        editorPanelController.showFileContent(file);
    }

    /**
     * 打开 diff 对比视图
     */
    public void showDiffView(FileDiff diff) {
        if (editorPanelController == null) {
            return;
        }
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
