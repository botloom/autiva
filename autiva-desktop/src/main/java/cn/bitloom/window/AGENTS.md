# Window 包

## 概述
本包实现了窗口管理功能，提供统一的对话框创建和显示机制。窗口使用操作系统默认标题栏装饰，主窗口使用 `StageStyle.DECORATED`，对话框使用 `StageStyle.UNIFIED`。

## 核心类

### WindowManager
窗口管理器，负责统一管理所有对话框和窗口的创建与显示。

**Spring 注解：** `@Component`

**主要方法：**
- `showDialog(String fxmlPath, Window owner, Consumer<T> controllerInitializer)`: 显示对话框窗口，窗口配置从控制器的 DialogHolder 接口读取
- `showDialog(String fxmlPath, Window owner)`: 显示对话框窗口（无控制器初始化）

**使用示例：**
```java
windowManager.<MyDialogController>showDialog(
    "cn/bitloom/view/MyDialog.fxml",
    parentWindow,
    controller -> controller.initData(data)
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

## 系统托盘

主窗口使用 FXTrayIcon（`com.dustinredmond.fxtrayicon:FXTrayIcon`）实现系统托盘功能。

**行为：**
- 点击关闭按钮时，窗口隐藏到系统托盘而非退出
- 双击托盘图标或右键菜单"显示主窗口"可恢复窗口
- 右键菜单提供"退出"选项，点击后真正退出应用
- 若系统不支持托盘（`FXTrayIcon.isSupported()` 返回 false），关闭按钮行为回退为直接退出

**实现位置：** `AutivaApplication` 中的 `setupTrayIcon()` 和 `setupCloseBehavior()` 方法

**托盘菜单项：**
1. 显示主窗口 — 恢复并前置主窗口
2. （分隔线）
3. 退出 — 调用 `exitApp()` 关闭 Spring 上下文并退出 JVM

## 设计模式
- 声明式配置：对话框控制器通过 DialogHolder 声明窗口配置，WindowManager 读取并应用

## 使用场景
1. 打开编辑器对话框
2. 打开配置对话框
3. 打开确认对话框
4. 任何需要模态窗口的场景

## 注意事项
1. 使用 WindowManager 的控制器必须实现 StageAware 接口
2. 对话框控制器应实现 DialogHolder 接口声明窗口配置
3. fxmlPath 必须是 classpath 下的相对路径
4. 所有窗口默认使用 WINDOW_MODAL 模态
5. 窗口图标统一使用 AppConstants.Stage.ICON
6. 主窗口使用 StageStyle.DECORATED，对话框使用 StageStyle.UNIFIED，均使用操作系统默认标题栏
