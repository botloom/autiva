package cn.bitloom.router;

import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.controller.IndexController;
import cn.bitloom.store.Store;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component
public class Router {

    private static final String ACTIVE_CSS_CLASS = "sidebar__option--active";

    private final IndexController indexController;
    private final RouteConfig routeConfig;
    private final Map<String, Function<IndexController, ButtonBarHolder>> buttonBarHolderMap;
    private String currentRoute;

    public Router(@Lazy IndexController indexController, RouteConfig routeConfig) {
        this.indexController = indexController;
        this.routeConfig = routeConfig;
        this.routeConfig.init();
        this.currentRoute = RouteConfig.Path.HOME;

        this.buttonBarHolderMap = Map.of(
                RouteConfig.Path.HOME, IndexController::getHomePageController,
                RouteConfig.Path.AGENT, IndexController::getAgentPageController,
                RouteConfig.Path.SETTINGS, IndexController::getSettingsPageController,
                RouteConfig.Path.SKILLS, IndexController::getSkillPageController,
                RouteConfig.Path.MCP, IndexController::getMcpPageController,
                RouteConfig.Path.TASK, IndexController::getTaskPageController
        );
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
            Function<IndexController, ButtonBarHolder> holderFunc = this.buttonBarHolderMap.get(path);
            if (holderFunc != null) {
                ButtonBarHolder holder = holderFunc.apply(this.indexController);
                if (holder != null) {
                    config = holder;
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
