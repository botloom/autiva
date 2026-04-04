package cn.bitloom.controller;

import cn.bitloom.holder.PageHolder;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.ResourceBundle;

@Component
public class SideBarController implements Initializable, PageHolder {

    @FXML
    private VBox sideBar;
    @FXML
    private HBox homeOption;
    @FXML
    private HBox agentOption;
    @FXML
    private HBox settingsOption;
    @FXML
    private HBox skillOption;
    @FXML
    private HBox mcpOption;
    @FXML
    private HBox taskOption;

    @Getter
    @Setter
    private IndexController indexController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.hide();
        this.homeOption.setOnMouseClicked(event -> {
            if (this.indexController != null) {
                this.indexController.navigate(cn.bitloom.router.RouteConfig.Path.HOME);
            }
        });
        this.agentOption.setOnMouseClicked(event -> {
            if (this.indexController != null) {
                this.indexController.navigate(cn.bitloom.router.RouteConfig.Path.AGENT);
            }
        });
        this.settingsOption.setOnMouseClicked(event -> {
            if (this.indexController != null) {
                this.indexController.navigate(cn.bitloom.router.RouteConfig.Path.SETTINGS);
            }
        });
        this.skillOption.setOnMouseClicked(event -> {
            if (this.indexController != null) {
                this.indexController.navigate(cn.bitloom.router.RouteConfig.Path.SKILLS);
            }
        });
        this.mcpOption.setOnMouseClicked(event -> {
            if (this.indexController != null) {
                this.indexController.navigate(cn.bitloom.router.RouteConfig.Path.MCP);
            }
        });
        this.taskOption.setOnMouseClicked(event -> {
            if (this.indexController != null) {
                this.indexController.navigate(cn.bitloom.router.RouteConfig.Path.TASK);
            }
        });

        this.homeOption.getStyleClass().add("sidebar__option--active");
    }

    public void updateActiveState(String path) {
        this.homeOption.getStyleClass().remove("sidebar__option--active");
        this.agentOption.getStyleClass().remove("sidebar__option--active");
        this.settingsOption.getStyleClass().remove("sidebar__option--active");
        this.skillOption.getStyleClass().remove("sidebar__option--active");
        this.mcpOption.getStyleClass().remove("sidebar__option--active");
        this.taskOption.getStyleClass().remove("sidebar__option--active");

        if (cn.bitloom.router.RouteConfig.Path.HOME.equals(path)) {
            this.homeOption.getStyleClass().add("sidebar__option--active");
        } else if (cn.bitloom.router.RouteConfig.Path.AGENT.equals(path)) {
            this.agentOption.getStyleClass().add("sidebar__option--active");
        } else if (cn.bitloom.router.RouteConfig.Path.SETTINGS.equals(path)) {
            this.settingsOption.getStyleClass().add("sidebar__option--active");
        } else if (cn.bitloom.router.RouteConfig.Path.SKILLS.equals(path)) {
            this.skillOption.getStyleClass().add("sidebar__option--active");
        } else if (cn.bitloom.router.RouteConfig.Path.MCP.equals(path)) {
            this.mcpOption.getStyleClass().add("sidebar__option--active");
        } else if (cn.bitloom.router.RouteConfig.Path.TASK.equals(path)) {
            this.taskOption.getStyleClass().add("sidebar__option--active");
        }
    }

    @Override
    public void show() {
        this.sideBar.setVisible(true);
        this.sideBar.setManaged(true);
    }

    @Override
    public void hide() {
        this.sideBar.setVisible(false);
        this.sideBar.setManaged(false);
    }

    public boolean isSidebarVisible() {
        return this.sideBar != null && this.sideBar.isVisible();
    }

}
