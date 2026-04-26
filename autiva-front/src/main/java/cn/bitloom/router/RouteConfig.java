package cn.bitloom.router;

import cn.bitloom.controller.*;
import cn.bitloom.holder.PageHolder;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Getter
@Component
public class RouteConfig {

    public static class Path {
        private Path() {}
        public static final String HOME = "/";
        public static final String AGENT = "/agent";
        public static final String SETTINGS = "/settings";
        public static final String SKILLS = "/skills";
        public static final String MCP = "/mcp";
        public static final String TASK = "/task";
    }

    private final Map<String, Route> routes = new HashMap<>();

    public void init() {
        registerRoute(Path.HOME, "主页", HomePageController.class,
                ic -> safeCall(ic.getHomePageController(), PageHolder::show),
                ic -> safeCall(ic.getHomePageController(), PageHolder::hide));

        registerRoute(Path.AGENT, "智能体", AgentPageController.class,
                ic -> safeCall(ic.getAgentPageController(), PageHolder::show),
                ic -> safeCall(ic.getAgentPageController(), PageHolder::hide));

        registerRoute(Path.SETTINGS, "设置", SettingsPageController.class,
                ic -> safeCall(ic.getSettingsPageController(), PageHolder::show),
                ic -> safeCall(ic.getSettingsPageController(), PageHolder::hide));

        registerRoute(Path.SKILLS, "技能管理", SkillPageController.class,
                ic -> safeCall(ic.getSkillPageController(), PageHolder::show),
                ic -> safeCall(ic.getSkillPageController(), PageHolder::hide));

        registerRoute(Path.MCP, "MCP", McpPageController.class,
                ic -> safeCall(ic.getMcpPageController(), PageHolder::show),
                ic -> safeCall(ic.getMcpPageController(), PageHolder::hide));

        registerRoute(Path.TASK, "任务", TaskPageController.class,
                ic -> safeCall(ic.getTaskPageController(), PageHolder::show),
                ic -> safeCall(ic.getTaskPageController(), PageHolder::hide));
    }

    private void safeCall(PageHolder holder, Consumer<PageHolder> action) {
        if (holder != null) {
            action.accept(holder);
        }
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
