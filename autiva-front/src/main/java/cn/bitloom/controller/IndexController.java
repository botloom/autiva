package cn.bitloom.controller;

import cn.bitloom.router.Router;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.BorderPane;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.net.URL;
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

}
