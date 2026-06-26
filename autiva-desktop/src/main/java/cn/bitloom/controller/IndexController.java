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
    private RightSidebarController rightSidebarController;
    @FXML
    @Getter
    private ContentPanelController contentPanelController;

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
        this.rightSidebarController.setIndexController(this);
        this.contentPanelController.setIndexController(this);

        // 右侧边栏和内容面板默认隐藏（FXML 中已设置 visible=false managed=false）
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
     * 切换右侧边栏显示/隐藏
     */
    public void toggleRightSidebar() {
        if (rightSidebarController != null) {
            rightSidebarController.toggle();
        }
    }

    /**
     * 显示右侧边栏
     */
    public void showRightSidebar() {
        if (rightSidebarController != null) {
            rightSidebarController.show();
        }
    }

    /**
     * 隐藏右侧边栏
     */
    public void hideRightSidebar() {
        if (rightSidebarController != null) {
            rightSidebarController.hide();
        }
    }

    /**
     * 显示内容面板
     */
    public void showContentPanel() {
        if (contentPanelController != null) {
            contentPanelController.show();
        }
    }

    /**
     * 隐藏内容面板
     */
    public void hideContentPanel() {
        if (contentPanelController != null) {
            contentPanelController.hide();
        }
    }

    /**
     * 打开终端面板
     */
    public void openTerminal() {
        if (contentPanelController == null) {
            return;
        }
        // 获取工作目录（当前项目路径或用户主目录）
        Path workingDir = null;
        if (homePageController != null && homePageController.getViewModel() != null) {
            ProjectInfo project = homePageController.getViewModel().getCurrentProject();
            if (project != null) {
                workingDir = Path.of(project.path());
            }
        }
        contentPanelController.openTerminal(workingDir);
    }

    /**
     * 关闭终端面板
     */
    public void closeTerminal() {
        if (contentPanelController != null) {
            contentPanelController.closeTerminal();
        }
    }

    /**
     * 在内容面板显示文件内容
     */
    public void showFileInPanel(Path file) {
        if (contentPanelController == null) {
            return;
        }
        contentPanelController.showFileContent(file);
    }

    /**
     * 打开 diff 对比视图
     */
    public void showDiffView(FileDiff diff) {
        if (contentPanelController == null) {
            return;
        }
        contentPanelController.showDiffView(diff);
    }

    /**
     * 更新当前项目（通知右侧边栏构建目录树）
     */
    public void updateCurrentProject(ProjectInfo project) {
        if (rightSidebarController != null) {
            rightSidebarController.setCurrentProject(project);
        }
    }

}
