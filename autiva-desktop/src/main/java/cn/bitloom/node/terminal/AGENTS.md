# Terminal 包

## 概述
本包实现了基于 Pty4J + JediTermFX 的交互式终端，支持完整的 Shell 交互（包括 vim、top 等全屏程序）。

## 依赖
- `org.jetbrains.pty4j:pty4j:0.13.2`：底层 PTY 进程管理
- `com.techsenger.jeditermfx:jeditermfx-ui:1.1.0`：JediTerm 的 JavaFX 移植版，提供终端 UI 渲染（排除其传递的 pty4j 依赖，使用项目 0.13.2 版本）

## 核心类

### PtySession
PTY 会话封装，管理 Pty4J 的 PtyProcess 生命周期。

**字段：**
- `sessionId`: 会话唯一ID（UUID）
- `ptyProcess`: Pty4J 进程
- `stdin/stdout/stderr`: 输入输出流
- `closed`: 关闭标志

**核心方法：**
- `writeInput(String input)`: 发送输入到 PTY
- `getInputStream()`: 获取标准输出流（阻塞读取，供 TtyConnector 使用）
- `setWinSize(int cols, int rows)`: 调整终端窗口大小
- `isAlive()`: 检查进程是否存活
- `getExitCode()`: 获取退出码
- `close()`: 关闭 PTY 会话

**平台支持：**
- Windows: PowerShell（powershell.exe -NoLogo）
- macOS: zsh（/bin/zsh -l）
- Linux: bash（/bin/bash -l）

### PtyTerminalService
PTY 终端服务（Spring `@Component`），管理 PTY 会话的创建、查询和销毁。

**核心方法：**
- `PtySession createSession(Path workingDir)`: 创建新的 PTY 会话
- `PtySession getSession(String sessionId)`: 获取会话
- `void writeInput(String sessionId, String input)`: 发送输入
- `void closeSession(String sessionId)`: 关闭会话
- `void closeAllSessions()`: 关闭所有会话（应用退出时调用）

### PtySessionTtyConnector
PtySession 与 JediTermFX 之间的 TtyConnector 适配器，实现 `com.techsenger.jeditermfx.core.TtyConnector` 接口。

**职责：**
- 将 PtySession 的输入输出适配为 JediTermFX 要求的 TtyConnector 接口
- 处理终端大小调整（resize）
- 管理 PTY 连接状态

**字段：**
- `session`: PtySession
- `reader`: InputStreamReader（UTF-8 解码 PTY 字节流为字符流）

**核心方法：**
- `read(char[], int, int)`: 阻塞读取 PTY 输出到 JediTermFX（委托给 InputStreamReader）
- `write(byte[])`: 写入输入到 PTY（UTF-8 解码后调用 session.writeInput）
- `write(String)`: 写入字符串到 PTY
- `resize(TermSize)`: 调整终端大小（调用 session.setWinSize）
- `isConnected()`: 检查连接状态（基于 session.isAlive）
- `waitFor()`: 等待进程退出并返回退出码（轮询 isAlive）
- `ready()`: 检查是否有可读数据（非阻塞）
- `getName()`: 返回 "PtySession-" + sessionId
- `close()`: 关闭连接（调用 session.close）

### JediTerminalView
基于 JediTermFX 的终端视图组件（继承 BorderPane），封装 `JediTermFxWidget`，提供完整的终端交互能力。

**依赖库：** `com.techsenger.jeditermfx:jeditermfx-ui:1.1.0`（JediTerm 的 JavaFX 移植版）

**特性：**
- 支持 ANSI 颜色码解析和渲染（完整 VT100/xterm 仿真）
- 支持光标控制、清屏、滚动等终端控制序列
- 支持 Xterm 256 色
- 支持 vim、top 等全屏程序（AltSendsEscape 默认开启）
- 默认 80x24 终端大小
- 使用 DefaultSettingsProvider 配置（深色主题：#1e1e1e 背景）
- 复制/粘贴快捷键：macOS Cmd+C/V，Windows/Linux Ctrl+Shift+C/V（避免与中断信号冲突）
- 字体：Windows Consolas / macOS Menlo / Linux Monospaced，14px

**字段：**
- `widget`: JediTermFxWidget
- `session`: PtySession
- `settingsProvider`: SettingsProvider

**核心方法：**
- `startSession(PtySession session)`: 启动终端会话（创建 JediTermFxWidget + PtySessionTtyConnector，setCenter(widget.getPane())，调用 widget.start()）
- `closeSession()`: 关闭终端会话（widget.close() + session.close()）
- `isRunning()`: 检查终端是否正在运行
- `requestFocus()`: 请求焦点（转发到 widget.getPreferredFocusableNode()）
- `getSelectedText()`: 获取终端当前选中的文本（可能为 null 或空字符串）。通过 `selectedTextProperty().get()` 获取，兼容 JediTermFX 1.1.0（该版本 `TerminalPanel.getSelectedText()` 为 private，但 `selectedTextProperty()` 是 public）
- `selectedTextProperty()`: 返回终端选中文本的 `ReadOnlyStringProperty`，可在 widget 就绪后监听选择变化。仅在会话启动后可用，启动前返回 null。供 EditorPanelController 右键菜单"添加到对话框"获取选中文本

**构造函数：**
- `JediTerminalView()`: 使用 DefaultSettingsProvider
- `JediTerminalView(SettingsProvider)`: 自定义配置

**样式类：** `jedi-terminal-view`（深色背景 #1e1e1e，底部圆角 0 0 12 12）

## 设计模式
- 适配器模式：PtySessionTtyConnector 将 PtySession 适配为 JediTermFX 的 TtyConnector
- 封装模式：PtySession 封装 Pty4J 的复杂性，JediTerminalView 封装 JediTermFxWidget
- 服务模式：PtyTerminalService 统一管理会话

## 注意事项
1. JediTermFX 依赖 pty4j，pom.xml 中排除其 pty4j 依赖使用项目的 0.13.2 版本
2. JediTermFxWidget 内部管理终端读取线程（EmulatorTask），在 `widget.start()` 时启动，`widget.close()` 时停止
3. JediTermFxWidget 不是 JavaFX Node，通过 `widget.getPane()` 获取 StackPane 添加到场景图
4. Windows 使用 ConPTY，可能初始化失败，需要错误处理
5. 终端大小变化时 JediTermFX 自动调用 TtyConnector.resize(TermSize) 通知 PTY
6. JediTerminalView 通过 getPreferredFocusableNode() 确保终端正确获取键盘焦点
7. PtySessionTtyConnector 使用 UTF-8 编解码，与 JediTermFX 默认行为一致
