package cn.bitloom.router;

import cn.bitloom.controller.AgentPageController;
import cn.bitloom.controller.IndexController;
import cn.bitloom.controller.HomePageController;
import cn.bitloom.controller.SettingsPageController;
import cn.bitloom.controller.SkillPageController;
import cn.bitloom.controller.McpPageController;
import cn.bitloom.controller.TaskPageController;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Getter
@Component
public class RouteConfig {

    public static class Path {
        public static final String HOME = "/";
        public static final String AGENT = "/agent";
        public static final String SETTINGS = "/settings";
        public static final String SKILLS = "/skills";
        public static final String MCP = "/mcp";
        public static final String TASK = "/task";
    }

    private final Map<String, Route> routes = new HashMap<>();

    public void init() {
        registerRoute(
            Path.HOME, 
            "主页", 
            HomePageController.class,
            (indexController) -> {
                if (indexController.getHomePageController() != null) {
                    indexController.getHomePageController().show();
                }
            },
            (indexController) -> {
                if (indexController.getHomePageController() != null) {
                    indexController.getHomePageController().hide();
                }
            }
        );
        
        registerRoute(
            Path.AGENT, 
            "智能体", 
            AgentPageController.class,
            (indexController) -> {
                if (indexController.getAgentPageController() != null) {
                    indexController.getAgentPageController().show();
                }
            },
            (indexController) -> {
                if (indexController.getAgentPageController() != null) {
                    indexController.getAgentPageController().hide();
                }
            }
        );
        
        registerRoute(
            Path.SETTINGS, 
            "设置", 
            SettingsPageController.class,
            (indexController) -> {
                if (indexController.getSettingsPageController() != null) {
                    indexController.getSettingsPageController().show();
                }
            },
            (indexController) -> {
                if (indexController.getSettingsPageController() != null) {
                    indexController.getSettingsPageController().hide();
                }
            }
        );
        
        registerRoute(
            Path.SKILLS, 
            "技能管理", 
            SkillPageController.class,
            (indexController) -> {
                if (indexController.getSkillPageController() != null) {
                    indexController.getSkillPageController().show();
                }
            },
            (indexController) -> {
                if (indexController.getSkillPageController() != null) {
                    indexController.getSkillPageController().hide();
                }
            }
        );
        
        registerRoute(
            Path.MCP, 
            "MCP", 
            McpPageController.class,
            (indexController) -> {
                if (indexController.getMcpPageController() != null) {
                    indexController.getMcpPageController().show();
                }
            },
            (indexController) -> {
                if (indexController.getMcpPageController() != null) {
                    indexController.getMcpPageController().hide();
                }
            }
        );
        
        registerRoute(
            Path.TASK, 
            "任务", 
            TaskPageController.class,
            (indexController) -> {
                if (indexController.getTaskPageController() != null) {
                    indexController.getTaskPageController().show();
                }
            },
            (indexController) -> {
                if (indexController.getTaskPageController() != null) {
                    indexController.getTaskPageController().hide();
                }
            }
        );
    }

    private void registerRoute(String path, String name, Class<?> controllerClass, 
                              Consumer<IndexController> showAction, Consumer<IndexController> hideAction) {
        routes.put(path, new Route(path, name, controllerClass, showAction, hideAction));
    }

    public Route getRoute(String path) {
        return routes.get(path);
    }

    public record Route(String path, String name, Class<?> controllerClass,
                       Consumer<IndexController> showAction, Consumer<IndexController> hideAction) {

    }
}
