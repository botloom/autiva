package cn.bitloom.controller;

import cn.bitloom.holder.PageHolder;
import cn.bitloom.router.RouteConfig;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;

@Component
public class SideBarController implements Initializable, PageHolder {

    private static final String ACTIVE_CSS_CLASS = "sidebar__option--active";

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

    private Map<String, HBox> routeOptionMap;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.hide();

        this.routeOptionMap = new LinkedHashMap<>();
        this.routeOptionMap.put(RouteConfig.Path.HOME, this.homeOption);
        this.routeOptionMap.put(RouteConfig.Path.AGENT, this.agentOption);
        this.routeOptionMap.put(RouteConfig.Path.SETTINGS, this.settingsOption);
        this.routeOptionMap.put(RouteConfig.Path.SKILLS, this.skillOption);
        this.routeOptionMap.put(RouteConfig.Path.MCP, this.mcpOption);
        this.routeOptionMap.put(RouteConfig.Path.TASK, this.taskOption);

        this.routeOptionMap.forEach((path, option) -> {
            option.setOnMouseClicked(event -> {
                if (this.indexController != null) {
                    this.indexController.navigate(path);
                }
            });
        });

        this.homeOption.getStyleClass().add(ACTIVE_CSS_CLASS);
    }

    public void updateActiveState(String path) {
        this.routeOptionMap.values().forEach(option ->
                option.getStyleClass().remove(ACTIVE_CSS_CLASS));

        HBox activeOption = this.routeOptionMap.get(path);
        if (activeOption != null) {
            activeOption.getStyleClass().add(ACTIVE_CSS_CLASS);
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
