# Window 包

## 概述
本包实现了窗口管理功能，提供统一的窗口创建和显示机制。

## 核心类

### WindowManager
窗口管理器，负责统一管理所有对话框和窗口的创建与显示。

**Spring 注解：** `@Component`

**主要方法：**
- `showDialog(WindowConfig<T> config)`: 显示对话框窗口
- `configBuilder()`: 创建窗口配置构建器

**使用示例：**
```java
WindowManager.WindowConfig<MdEditorController> config = windowManager.<MdEditorController>configBuilder()
    .fxmlPath("cn/bitloom/components/MdEditor.fxml")
    .title("编辑器")
    .owner(parentWindow)
    .resizable(true)
    .controllerInitializer(controller -> {
        controller.setTitle("文件名");
        controller.setContent(content);
    })
    .build();

windowManager.showDialog(config);
```

### WindowConfig
窗口配置类，使用 Builder 模式构建。

**配置项：**
- `fxmlPath`: FXML 文件路径（必填）
- `title`: 窗口标题
- `owner`: 父窗口
- `width`: 窗口宽度（默认：AppConstants.Stage.WIDTH）
- `height`: 窗口高度（默认：AppConstants.Stage.HEIGHT）
- `resizable`: 是否可调整大小（默认：false）
- `controllerInitializer`: 控制器初始化回调

### StageAware
控制器接口，用于接收 Stage 对象。

**方法：**
- `setStage(Stage stage)`: 设置窗口舞台对象

**实现要求：**
需要使用 WindowManager 的控制器必须实现此接口，以便 WindowManager 能够自动注入 Stage 对象。

### WindowException
窗口异常类，用于封装窗口操作中的异常。

## 设计模式
- Builder 模式：使用 Builder 模式构建 WindowConfig，提供灵活的配置方式
- 策略模式：通过 controllerInitializer 回调，支持不同控制器的初始化策略

## 使用场景
1. 打开编辑器对话框
2. 打开配置对话框
3. 打开确认对话框
4. 任何需要模态窗口的场景

## 注意事项
1. 使用 WindowManager 的控制器必须实现 StageAware 接口
2. fxmlPath 必须是 classpath 下的相对路径
3. 所有窗口默认使用 WINDOW_MODAL 模态
4. 窗口图标统一使用 AppConstants.Stage.ICON
5. 窗口样式统一使用 StageStyle.UNIFIED

## 未来扩展
后续将基于 WindowManager 实现 windowTool，供智能体使用，实现动态窗口创建和管理功能。
