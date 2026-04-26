# Window 包

## 概述
本包实现了窗口管理功能，提供统一的对话框创建和显示机制。窗口 chrome 能力（拖拽、缩放、圆角裁剪、控制按钮）由各窗口自行实现，不抽取公共辅助类。

## 核心类

### WindowManager
窗口管理器，负责统一管理所有对话框和窗口的创建与显示。

**Spring 注解：** `@Component`

**主要方法：**
- `showDialog(String fxmlPath, Window owner, Consumer<T> controllerInitializer)`: 显示对话框窗口，窗口配置从控制器的 DialogHolder 接口读取
- `showDialog(String fxmlPath, Window owner)`: 显示对话框窗口（无控制器初始化）

**使用示例：**
```java
windowManager.<FileEditorController>showDialog(
    "cn/bitloom/view/FileEditorDialog.fxml",
    parentWindow,
    controller -> controller.initRootPath(rootPath)
);
```

**设计理念：** 窗口配置（宽度、高度、是否可调整大小、窗口样式）由对话框控制器通过 DialogHolder 接口声明，WindowManager 只是读取并应用。调用方只需提供 FXML 路径、父窗口和数据初始化回调，不再需要配置窗口 UI 属性。

### StageAware
控制器接口，用于接收 Stage 对象。

**方法：**
- `setStage(Stage stage)`: 设置窗口舞台对象

**实现要求：**
需要使用 WindowManager 的控制器必须实现此接口，以便 WindowManager 能够自动注入 Stage 对象。

### WindowException
窗口异常类，用于封装窗口操作中的异常。

### DesktopPet
已移除。桌面宠物功能已舍弃。

## 窗口 Chrome 能力

每个窗口自行实现 chrome 能力（拖拽移动、边缘缩放、圆角裁剪、控制按钮），不抽取公共辅助类。这样做的好处是：
- 每个窗口的 chrome 逻辑完全自包含，修改一个窗口不影响其他窗口
- 不需要跳转到其他文件就能理解窗口的完整行为
- 不同窗口可以有不同的定制行为（如主窗口关闭按钮最小化到托盘，对话框关闭按钮直接关闭）

### 实现位置

| 窗口 | 实现位置 | 说明 |
|------|---------|------|
| 主窗口 | `AutivaApplication.start()` | 通过 `scene.lookup()` 获取按钮，内联绑定行为 |
| 文件编辑器对话框 | `FileEditorController.setStage()` | 通过 `@FXML` 获取按钮，内联绑定行为 |
| 轻量级弹窗 | `FileEditorController.showInputDialog/showConfirmDialog` | 仅使用 `setupDrag` |
| 微信扫码登录对话框 | `WeixinLoginController.setStage()` | 使用 `WindowChromeHelper.setupDrag`，整个卡片可拖动，右上角关闭按钮 |

### 各窗口需实现的私有方法

每个窗口需要以下私有方法（根据需要选择）：

- `setupDrag(Stage, Node)`: 拖拽移动窗口（支持从最大化状态拖拽还原，双击标题栏切换最大化）
- `setupResize(Stage)`: 边缘拖拽缩放（8px 边缘检测区域，8个方向）
- `setupClip(Region, double)`: 圆角裁剪
- `createMinimizeIcon/createMaximizeIcon/createRestoreIcon/createCloseIcon()`: 控制按钮图标
- `isButtonTarget(MouseEvent)`: 判断事件目标是否为按钮（用于拖拽排除）
- `computeResizeCursor/computeResizeDirection()`: 缩放方向计算

### FXML 结构

窗口控制按钮在 FXML 中声明：

```xml
<HBox fx:id="windowControls" styleClass="window-chrome__controls">
    <Button fx:id="minimizeBtn" styleClass="window-chrome__btn,window-chrome__btn--minimize" contentDisplay="GRAPHIC_ONLY"/>
    <Button fx:id="maximizeBtn" styleClass="window-chrome__btn,window-chrome__btn--maximize" contentDisplay="GRAPHIC_ONLY"/>
    <Button fx:id="closeBtn" styleClass="window-chrome__btn,window-chrome__btn--close" contentDisplay="GRAPHIC_ONLY"/>
</HBox>
```

### 样式类说明
- `window-chrome`: 外层透明容器（8px padding 为阴影留空间）
- `window-chrome__content`: 内层白色圆角卡片（8px圆角，阴影效果）
- `window-chrome__toolbar`: 顶部工具栏（白色背景，36px高度）
- `window-chrome__icon-area`: 左上角图标区域（40px宽，与左侧栏对齐）
- `window-chrome__toolbar-buttons`: 工具栏按钮区域
- `window-chrome__spacer`: 弹性空间（可拖拽移动窗口）
- `window-chrome__controls`: 窗口控制按钮区域（最小化/最大化/关闭）
- `window-chrome__btn`: 基础按钮样式
- `window-chrome__btn--minimize`: 最小化按钮
- `window-chrome__btn--maximize`: 最大化按钮
- `window-chrome__btn--restore`: 还原按钮（最大化后自动切换）
- `window-chrome__btn--close`: 关闭按钮（悬停变红色 #e81123）

## 设计模式
- 声明式配置：对话框控制器通过 DialogHolder 声明窗口配置，WindowManager 读取并应用
- 声明式 UI：窗口控制按钮在 FXML 中声明，行为在代码中内联绑定
- 自包含：每个窗口的 chrome 逻辑完全自包含，不依赖公共辅助类

## 使用场景
1. 打开编辑器对话框
2. 打开配置对话框
3. 打开确认对话框
4. 任何需要模态窗口的场景
5. 主窗口（也使用无系统栏设计）

## 新增对话框指南

新增对话框只需：
1. 创建 FXML 文件，遵循 window-chrome 结构规范（包括窗口控制按钮的声明）
2. 创建控制器，实现 `StageAware` 和 `DialogHolder` 接口
3. 在 FXML 中声明 `minimizeBtn`、`maximizeBtn`、`closeBtn` 按钮
4. 在 `DialogHolder` 中声明窗口配置（只需覆盖需要自定义的属性）
5. 在 `setStage` 中内联绑定按钮行为和窗口 chrome 能力
6. 调用 `windowManager.<ControllerType>showDialog(fxmlPath, owner, controller -> { ... })`

```java
@Component
public class MyDialogController implements WindowManager.StageAware, DialogHolder {
    @FXML private Button minimizeBtn;
    @FXML private Button maximizeBtn;
    @FXML private Button closeBtn;

    @Override
    public boolean isResizable() {
        return true;
    }
    
    @Override
    public StageStyle getStageStyle() {
        return StageStyle.TRANSPARENT;
    }
    
    @Override
    public void setStage(Stage stage) {
        Platform.runLater(() -> {
            if (minimizeBtn != null) {
                minimizeBtn.setGraphic(createMinimizeIcon());
                minimizeBtn.setOnAction(e -> stage.setIconified(true));
            }
            if (maximizeBtn != null) {
                maximizeBtn.setGraphic(createMaximizeIcon());
                maximizeBtn.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));
                stage.maximizedProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal) {
                        maximizeBtn.getStyleClass().remove("window-chrome__btn--maximize");
                        maximizeBtn.getStyleClass().add("window-chrome__btn--restore");
                        maximizeBtn.setGraphic(createRestoreIcon());
                    } else {
                        maximizeBtn.getStyleClass().remove("window-chrome__btn--restore");
                        maximizeBtn.getStyleClass().add("window-chrome__btn--maximize");
                        maximizeBtn.setGraphic(createMaximizeIcon());
                    }
                });
            }
            if (closeBtn != null) {
                closeBtn.setGraphic(createCloseIcon());
                closeBtn.setOnAction(e -> stage.close());
            }
            stage.setMinWidth(600);
            stage.setMinHeight(400);
            setupDrag(stage, toolbar);
            setupResize(stage);
            setupClip(root, 12);
        });
    }
    
    public void initData(String data) {
        // 初始化数据
    }
}

// 调用方
windowManager.<MyDialogController>showDialog(
    "cn/bitloom/view/MyDialog.fxml",
    ownerWindow,
    controller -> controller.initData(data)
);
```

## 注意事项
1. 使用 WindowManager 的控制器必须实现 StageAware 接口
2. 对话框控制器应实现 DialogHolder 接口声明窗口配置
3. 窗口控制按钮在 FXML 中声明，行为在代码中内联绑定
4. 窗口 chrome 逻辑（拖拽、缩放、裁剪、按钮图标）由各窗口自行实现
5. fxmlPath 必须是 classpath 下的相对路径
6. 所有窗口默认使用 WINDOW_MODAL 模态
7. 窗口图标统一使用 AppConstants.Stage.ICON
8. 当 stageStyle 为 StageStyle.TRANSPARENT 时，自动设置 Scene 背景透明
9. 所有窗口均采用无系统标题栏设计
10. 新增窗口只需引入 window-chrome.css 并遵循上述 FXML 结构即可

## 未来扩展
后续将基于 WindowManager 实现 windowTool，供智能体使用，实现动态窗口创建和管理功能。
