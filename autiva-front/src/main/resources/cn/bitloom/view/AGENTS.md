# View 目录

## 概述
本目录包含应用的页面视图 FXML 文件。

## 页面列表

### HomePage.fxml
主页视图，实现聊天交互界面。

**控制器：** HomePageController

**结构：**
```
VBox (homePage)
├── VBox (icon) - 应用图标
│   └── SvgImageView - SVG 图标
├── ScrollPane (chatScrollPane) - 聊天内容滚动容器
│   └── VBox (chatContainer) - 聊天消息容器
└── VBox (searchBox) - 搜索输入框
    ├── TextField (searchField) - 输入框
    └── HBox - 按钮组
        ├── Button - 添加按钮
        ├── Button - 语音按钮
        ├── ComboBox (modelSelector) - 模型选择器（胶囊形状）
        └── Button (searchButton) - 发送按钮
```

**特性：**
- ScrollPane + VBox 替代 WebView 显示聊天内容
- 消息卡片使用纯 JavaFX 组件（UserMessageCard, AssistantMessageCard, ToolMessageCard, QuestionCard, TodoCard）
- 图标在首次发送后动画上移
- 响应式宽度设计
- 搜索框具有悬浮阴影效果（hover 时加深），样式与 MCP 卡片一致
- 模型选择器使用胶囊形状的 ComboBox，直接显示模型名称，点击下拉选择
- 无下拉箭头，宽度自适应模型名称，最小宽度为圆形（37px）
- 语音按钮支持语音输入，点击开始录音（红色），再次点击停止并识别
- 使用本地 Whisper medium 模型进行语音识别，需下载模型到 ~/.autiva/models/
- 识别结果自动转换为简体中文

### SettingsPage.fxml
设置页面视图。

**控制器：** SettingsPageController

**结构：**
```
VBox (settingsPage)
└── ScrollPane
    └── VBox (settings-page__content)
        ├── 浏览器设置卡片
        ├── 下载设置卡片
        └── 关于信息
```

**设置项：**
- 浏览器路径
- 保存目录

**滚动条样式：**
- 宽度 6px，符合 Apple 设计规范
- 半透明圆角滑块，悬停时加深
- 透明轨道和按钮，极简风格

### AgentPage.fxml
智能体配置管理页面视图。

**控制器：** AgentPageController

**结构：**
```
VBox (agentPage)
└── ScrollPane
    └── VBox (agentListContainer) - 智能体列表容器
```

**功能：**
- 显示智能体文件夹列表
- 每个智能体显示其配置文件
- 支持查看和编辑配置文件

**配置文件：**
- 位于 `~/.autiva/workspace` 目录
- 每个智能体一个文件夹
- 文件内容为 Markdown 格式

**滚动条样式：**
- 宽度 6px，符合 Apple 设计规范
- 半透明圆角滑块，悬停时加深
- 透明轨道和按钮，极简风格

### SkillPage.fxml
技能管理页面视图。

**控制器：** SkillPageController

**结构：**
```
VBox (skillPage)
└── ScrollPane
    └── VBox (skillListContainer) - 技能列表容器
```

**功能：**
- 显示技能卡片列表
- 技能卡片动态生成

**滚动条样式：**
- 宽度 6px，符合 Apple 设计规范
- 半透明圆角滑块，悬停时加深
- 透明轨道和按钮，极简风格

### McpPage.fxml
MCP 服务器管理页面视图。

**控制器：** McpPageController

**结构：**
```
VBox (mcpPage)
└── ScrollPane
    └── VBox (mcpListContainer) - 服务器列表容器
```

**滚动条样式：**
- 宽度 6px，符合 Apple 设计规范
- 半透明圆角滑块，悬停时加深
- 透明轨道和按钮，极简风格

### TaskPage.fxml
任务管理页面视图。

**控制器：** TaskPageController

**结构：**
```
VBox (taskPage)
└── ScrollPane
    └── VBox (taskListContainer) - 任务列表容器
```

**功能：**
- 显示定时任务卡片列表
- 每个任务卡片显示任务详情
- 支持触发和删除操作

**任务卡片信息：**
- 任务名称
- 任务类型（一次性任务/周期性任务/Cron任务）
- 任务状态（运行中/已取消）
- 创建时间
- 任务配置信息
- 消息内容

**滚动条样式：**
- 宽度 6px，符合 Apple 设计规范
- 半透明圆角滑块，悬停时加深
- 透明轨道和按钮，极简风格

### WorkflowPage.fxml
工作流管理页面视图。

**控制器：** WorkflowPageController

**结构：**
```
VBox (workflowPage)
└── ScrollPane
    └── VBox (workflowListContainer) - 工作流列表容器
```

## 对话框视图

### FileEditorDialog.fxml
通用文件编辑器对话框，IDEA风格布局，无系统标题栏。

**控制器：** FileEditorController

**结构：**
```
StackPane (window-chrome) - 透明窗口容器（8px padding，为阴影留空间）
└── BorderPane (window-chrome__content) - 主内容区（圆角8px，阴影效果）
    ├── HBox (top) - 工具栏（可拖拽移动窗口）
    │   ├── HBox (iconArea) - 应用图标区域（40px宽，与左侧栏对齐）
    │   ├── HBox (toolbar-buttons) - 工具栏按钮
    │   │   ├── Button - 新建文件
    │   │   └── Button - 新建文件夹
    │   ├── Region (spacer) - 弹性空间（可拖拽移动窗口）
    │   └── HBox (windowControls) - Windows风格窗口控制按钮
    │       ├── Button - 最小化
    │       ├── Button - 最大化/还原
    │       └── Button - 关闭（悬停变红色）
    ├── HBox (center) - 主内容区
    │   ├── VBox (leftBar) - 左侧按钮栏（窄条形）
    │   │   └── ToggleButton (treeToggleBtn) - 文件树切换
    │   ├── SplitPane (splitPane) - 分割面板
    │   │   ├── VBox (treePanel) - 文件树面板
    │   │   │   └── TreeView (fileTree) - 文件树
    │   │   └── VBox (editorPanel) - 编辑器面板
    │   │       ├── TabPane (tabPane) - 文件Tab页
    │   │       └── VBox (emptyState) - 空状态提示
    │   └── VBox (rightBar) - 右侧按钮栏（窄条形）
    │       ├── Button (previewBtn) - Markdown预览按钮（默认隐藏）
    │       └── Button (formatBtn) - 代码格式化按钮（默认隐藏）
    └── HBox (footer) - 底部状态栏
        ├── Label (filePathLabel) - 文件路径
        ├── Region - 弹性空间
        ├── Label (encodingLabel) - 编码
        └── Label (lineColLabel) - 行号列号
```

**窗口特性：**
- 使用 `StageStyle.TRANSPARENT` 创建无系统标题栏窗口
- Scene 背景透明，BorderPane 有圆角和阴影
- 工具栏支持拖拽移动窗口（通过 WindowChromeHelper）
- 双击工具栏切换最大化/还原
- 边缘拖拽调整窗口大小（6px 边缘检测区域，8个方向）
- Windows 风格窗口控制按钮（最小化/最大化/关闭）
- 关闭按钮悬停变红色（#e81123）
- 窗口最小尺寸 600x400
- 从最大化状态拖拽工具栏自动还原窗口
- 使用 WindowChromeHelper 封装通用能力

**样式特性：**
- IDEA风格布局，左侧/右侧/顶部有按钮栏
- 编辑器区域深色背景，其他区域浅色
- 左侧目录树和右侧编辑器都是圆角卡片
- Tab多文件编辑，Tab有图标、文件名、关闭按钮
- 代码编辑器带行号显示
- 自定义新建/重命名/删除弹窗（无系统栏，StageStyle.TRANSPARENT）
- 工具栏按钮与系统风格一致（8px圆角、浅灰背景）
- 引入 window-chrome.css 公共样式

### BrowserDialog.fxml
浏览器对话框。

**控制器：** BrowserDialogController

**结构：**
```
VBox (browser-dialog)
├── HBox (toolbar) - 工具栏
│   ├── Button - 后退
│   ├── Button - 前进
│   ├── Button - 刷新
│   └── TextField (urlField) - URL 输入
├── WebView (browserWebView) - 浏览器
└── HBox (status-bar) - 状态栏
    └── Label (statusLabel) - 状态
```

### chat.html
聊天页面 HTML 模板。

**功能：**
- 显示用户和助手消息
- 支持 Markdown 渲染
- 流式消息更新
- JavaScript API 供 Java 调用
- 自定义滚轮事件处理，提高滚动灵敏度（1.5倍速度）

**滚动优化：**
- 禁用平滑滚动（scroll-behavior: auto）
- 禁用过度滚动（overscroll-behavior: none）
- 自定义 wheel 事件监听器，滚动倍率 1.5
- 使用 requestAnimationFrame 确保流畅滚动

**JavaScript API：**
- `window.chatAPI.appendUserMessage(content)`: 添加用户消息
- `window.chatAPI.appendAssistantMessage(content, isStreaming)`: 添加助手消息
- `window.chatAPI.updateAssistantMessage(content)`: 更新助手消息
- `window.chatAPI.finishAssistantMessage()`: 完成助手消息

## 设计原则
1. 页面默认隐藏（visible="false" managed="false"）
2. 使用 ScrollPane 支持滚动
3. 样式通过独立的 CSS 文件定义
4. 使用 BEM 命名规范

## 注意事项
1. 页面通过 Router 控制显示/隐藏
2. 对话框通过 WindowManager 创建
3. WebView 用于复杂内容渲染
4. 样式文件路径使用相对路径
