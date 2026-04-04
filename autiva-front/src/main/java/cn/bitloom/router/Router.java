package cn.bitloom.router;

import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.controller.IndexController;
import cn.bitloom.store.Store;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Router {

    private final IndexController indexController;
    private final RouteConfig routeConfig;
    private String currentRoute;

    public Router(@Lazy IndexController indexController, RouteConfig routeConfig) {
        this.indexController = indexController;
        this.routeConfig = routeConfig;
        this.routeConfig.init();
        this.currentRoute = RouteConfig.Path.HOME;
    }

    public void navigate(String path) {
        RouteConfig.Route route = this.routeConfig.getRoute(path);
        if (route == null) {
            log.error("Route not found: {}", path);
            Store.statusText.set("路由未找到: " + path);
            return;
        }

        this.hideAllPages();
        this.showPage(path);
        this.updateButtonBar(path);
        this.currentRoute = path;
    }

    public void updateButtonBarForRoute(String path) {
        this.updateButtonBar(path);
    }

    private void updateButtonBar(String path) {
        if (this.indexController.getButtonBarController() != null) {
            ButtonBarHolder config = null;

            if (RouteConfig.Path.HOME.equals(path)) {
                if (this.indexController.getHomePageController() != null) {
                    config = this.indexController.getHomePageController();
                }
            } else if (RouteConfig.Path.AGENT.equals(path)) {
                if (this.indexController.getAgentPageController() != null) {
                    config = this.indexController.getAgentPageController();
                }
            } else if (RouteConfig.Path.SETTINGS.equals(path)) {
                if (this.indexController.getSettingsPageController() != null) {
                    config = this.indexController.getSettingsPageController();
                }
            } else if (RouteConfig.Path.SKILLS.equals(path)) {
                if (this.indexController.getSkillPageController() != null) {
                    config = this.indexController.getSkillPageController();
                }
            } else if (RouteConfig.Path.MCP.equals(path)) {
                if (this.indexController.getMcpPageController() != null) {
                    config = this.indexController.getMcpPageController();
                }
            }

            this.indexController.getButtonBarController().updateButtons(config);
        }

        this.updateSidebar(path);
    }

    private void updateSidebar(String path) {
        if (this.indexController.getSideBarController() != null) {
            this.indexController.getSideBarController().updateActiveState(path);
        }
    }

    private void hideAllPages() {
        for (RouteConfig.Route route : this.routeConfig.getRoutes().values()) {
            route.hideAction().accept(this.indexController);
        }
    }

    private void showPage(String path) {
        RouteConfig.Route route = this.routeConfig.getRoute(path);
        if (route != null) {
            route.showAction().accept(this.indexController);
        }
    }

}
