# Router 包

## 概述
本包实现了前端路由系统，管理页面导航和状态。

## 核心类

### Router
路由器，管理页面导航。

**Spring 注解：** `@Component`

**依赖注入：**
- `IndexController`: 使用 `@Lazy` 注解解决循环依赖
- `RouteConfig`: 路由配置

**核心方法：**
- `navigate(path)`: 导航到指定路径
- `updateButtonBarForRoute(path)`: 更新按钮栏
- `hideAllPages()`: 隐藏所有页面
- `showPage(path)`: 显示指定页面
- `updateSidebar(path)`: 更新侧边栏状态

### RouteConfig
路由配置类，定义所有路由及其行为。

**Spring 注解：** `@Component`

**路由路径常量：**
```java
public static class Path {
    public static final String HOME = "/";
    public static final String SETTINGS = "/settings";
    public static final String SKILLS = "/skills";
    public static final String MCP = "/mcp";
    public static final String TASK = "/task";
}
```

**Route 内部类：**
```java
public record Route(
    String path,                           // 路由路径
    String title,                          // 页面标题
    Class<?> controllerClass,              // 控制器类
    Consumer<IndexController> showAction,  // 显示动作
    Consumer<IndexController> hideAction   // 隐藏动作
) {}
```

## 路由配置

### 注册路由
```java
registerRoute(
    Path.HOME, 
    "主页", 
    HomePageController.class,
    (indexController) -> indexController.getHomePageController().show(),
    (indexController) -> indexController.getHomePageController().hide()
);
```

### 路由表
| 路径 | 标题 | 控制器 |
|------|------|--------|
| / | 主页 | HomePageController |
| /settings | 设置 | SettingsPageController |
| /skills | 技能管理 | SkillPageController |
| /mcp | MCP | McpPageController |
| /task | 任务 | TaskPageController |

## 使用示例

### 导航到页面
```java
indexController.navigate(RouteConfig.Path.SETTINGS);
```

### 切换侧边栏
```java
indexController.toggleSidebar();
```

## 导航流程

```
navigate(path)
    │
    ├── hideAllPages()      // 隐藏所有页面
    │
    ├── showPage(path)      // 显示目标页面
    │
    ├── updateButtonBar()   // 更新按钮栏
    │
    └── updateSidebar()     // 更新侧边栏状态
```

## 设计模式
- 命令模式：将页面操作封装为 Consumer
- 配置模式：集中管理路由配置
- 依赖注入：Router 和 RouteConfig 由 Spring 管理

## 注意事项
1. 路由路径必须唯一
2. 控制器必须实现 PageHolder 接口
3. 导航时会自动隐藏其他页面
4. 侧边栏状态会自动更新
5. Router 和 IndexController 存在循环依赖，使用 `@Lazy` 解决
