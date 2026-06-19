# Holder 包

## 概述
本包定义了控制器行为接口，用于规范页面的显示/隐藏行为、按钮配置和对话框窗口配置。

## 接口

### PageHolder
页面持有者接口，定义页面的基本行为。

**方法：**
- `show()`: 显示页面
- `hide()`: 隐藏页面

**实现示例：**
```java
@Component
public class MyPageController implements PageHolder {
    @FXML private VBox myPage;
    
    @Override
    public void show() {
        this.myPage.setVisible(true);
        this.myPage.setManaged(true);
    }
    
    @Override
    public void hide() {
        this.myPage.setVisible(false);
        this.myPage.setManaged(false);
    }
}
```

### ButtonBarHolder
按钮栏持有者接口，定义页面的按钮配置。

**方法：**
- `getButtonConfigs()`: 获取按钮配置列表

**ButtonConfig 记录类：**
```java
record ButtonConfig(
    String id,           // 按钮 ID
    String text,         // 按钮文本
    String styleClass,   // CSS 样式类
    EventHandler<ActionEvent> actionHandler  // 点击事件处理器
) {}
```

**实现示例：**
```java
@Component
public class SettingsPageController implements ButtonBarHolder {
    
    @Override
    public List<ButtonConfig> getButtonConfigs() {
        return List.of(
            new ButtonConfig(
                "saveButton",
                "保存",
                "button-bar__btn--primary",
                event -> save()
            )
        );
    }
}
```

### DialogHolder
对话框持有者接口，定义对话框控制器的窗口配置。

**核心理念：** 对话框控制器自己声明窗口配置（宽度、高度、是否可调整大小、窗口样式），WindowManager 从控制器读取配置，而非由调用方指定。这与 ButtonBarHolder 的设计哲学一致——**拥有 UI 的组件自己声明配置，框架只是读取配置**。

**方法（均提供默认值）：**
- `getWidth()`: 窗口宽度（默认：AppConstants.Stage.WIDTH）
- `getHeight()`: 窗口高度（默认：AppConstants.Stage.HEIGHT）
- `isResizable()`: 是否可调整大小（默认：false）
- `getStageStyle()`: 窗口样式（默认：StageStyle.UNIFIED）

**实现示例：**
```java
@Component
public class MyDialogController implements WindowManager.StageAware, DialogHolder {
    
    @Override
    public boolean isResizable() {
        return true;
    }
    
    @Override
    public StageStyle getStageStyle() {
        return StageStyle.TRANSPARENT;
    }
}
```

## 使用场景

### 页面导航
Router 使用 PageHolder 接口控制页面显示：
```java
private void hideAllPages() {
    for (RouteConfig.Route route : routeConfig.getRoutes().values()) {
        route.hideAction().accept(indexController);
    }
}

private void showPage(String path) {
    RouteConfig.Route route = routeConfig.getRoute(path);
    route.showAction().accept(indexController);
}
```

### 动态按钮
ButtonBarController 使用 ButtonBarHolder 接口更新按钮：
```java
public void updateButtons(ButtonBarHolder holder) {
    dynamicButtonContainer.getChildren().clear();
    
    if (holder != null) {
        for (ButtonConfig config : holder.getButtonConfigs()) {
            Button button = new Button(config.text());
            button.setId(config.id());
            button.getStyleClass().add(config.styleClass());
            button.setOnAction(config.actionHandler());
            dynamicButtonContainer.getChildren().add(button);
        }
    }
}
```

### 对话框窗口配置
WindowManager 使用 DialogHolder 接口读取对话框的窗口配置：
```java
public <T> void showDialog(String fxmlPath, Window owner, Consumer<T> controllerInitializer) {
    FXMLLoader loader = new FXMLLoader(new ClassPathResource(fxmlPath).getURL());
    Parent root = loader.load();
    T controller = loader.getController();

    double width = AppConstants.Stage.WIDTH;
    double height = AppConstants.Stage.HEIGHT;
    boolean resizable = false;
    StageStyle stageStyle = StageStyle.UNIFIED;

    if (controller instanceof DialogHolder holder) {
        width = holder.getWidth();
        height = holder.getHeight();
        resizable = holder.isResizable();
        stageStyle = holder.getStageStyle();
    }
    // ... 使用配置创建窗口
}
```

## 设计模式
- 策略模式：不同页面有不同的按钮配置
- 接口隔离：分离页面行为、按钮配置和窗口配置
- 声明式配置：控制器声明自己的 UI 配置，框架读取并应用

## 注意事项
1. 所有页面控制器应实现 PageHolder
2. 需要自定义按钮的页面应实现 ButtonBarHolder
3. 对话框控制器应实现 DialogHolder 来声明窗口配置
4. 按钮配置在页面切换时更新
5. 使用 record 简化 ButtonConfig 定义
6. DialogHolder 的所有方法提供默认值，控制器只需覆盖需要自定义的配置
