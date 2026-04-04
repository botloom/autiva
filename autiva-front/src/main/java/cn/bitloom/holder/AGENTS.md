# Holder 包

## 概述
本包定义了控制器行为接口，用于规范页面的显示/隐藏行为和按钮配置。

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

## 设计模式
- 策略模式：不同页面有不同的按钮配置
- 接口隔离：分离页面行为和按钮配置

## 注意事项
1. 所有页面控制器应实现 PageHolder
2. 需要自定义按钮的页面应实现 ButtonBarHolder
3. 按钮配置在页面切换时更新
4. 使用 record 简化 ButtonConfig 定义
