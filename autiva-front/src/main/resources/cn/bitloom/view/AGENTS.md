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
├── WebView (webView) - 聊天内容显示
└── VBox (searchBox) - 搜索输入框
    ├── TextField (searchField) - 输入框
    └── HBox - 按钮组
        ├── Button - 添加按钮
        ├── Button - 语音按钮
        ├── ComboBox (modelSelector) - 模型选择器（胶囊形状）
        └── Button (searchButton) - 发送按钮
```

**特性：**
- WebView 用于显示聊天内容
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
通用文件编辑器对话框，IDEA风格布局。

**控制器：** FileEditorController

**结构：**
```
BorderPane (file-editor)
├── HBox (toolbar) - 顶部工具栏
│   ├── Button - 新建文件
│   ├── Button - 新建文件夹
│   ├── Region - 弹性空间
│   ├── Button - 保存
│   └── Button - 关闭
├── HBox (center) - 主内容区
│   ├── VBox (leftBar) - 左侧按钮栏（窄条形）
│   │   └── ToggleButton (treeToggleBtn) - 文件树切换
│   ├── SplitPane (splitPane) - 分割面板
│   │   ├── VBox (treePanel) - 文件树面板（浅色背景，右侧圆角）
│   │   │   └── TreeView (fileTree) - 文件树
│   │   └── VBox (editorPanel) - 编辑器面板（深色背景 #1e1e1e，左侧圆角）
│   │       ├── TabPane (tabPane) - 文件Tab页
│   │       └── VBox (emptyState) - 空状态提示
│   └── VBox (rightBar) - 右侧按钮栏（窄条形）
└── HBox (footer) - 底部状态栏（蓝色背景）
    ├── Label (filePathLabel) - 文件路径
    ├── Region - 弹性空间
    ├── Label (encodingLabel) - 编码
    └── Label (lineColLabel) - 行号列号
```

**特性：**
- IDEA风格布局，左侧/右侧/顶部有毛玻璃样式按钮栏
- 编辑器区域深色背景，其他区域浅色
- 左侧目录树和右侧编辑器都是圆角卡片
- 中间分割区域为毛玻璃样式
- Tab多文件编辑，Tab有图标、文件名、关闭按钮
- 代码编辑器带行号显示
- 窗口无标题
- 自定义新建/重命名/删除弹窗（非原生弹窗）
- 工具栏按钮与系统风格一致（蓝色文字、8px圆角、浅灰背景）

### MdEditorDialog.fxml
Markdown 编辑器对话框。

**控制器：** MdEditorDialogController

**结构：**
```
VBox (md-editor-dialog)
├── HBox (header) - 标题栏
│   ├── TextField (titleField) - 标题输入
│   ├── Button - 预览
│   ├── Button - 保存
│   └── Button - 清空
├── SplitPane - 编辑区域
│   ├── TextArea (markdownEditor) - 编辑器
│   └── WebView (previewWebView) - 预览
└── HBox (footer) - 底部栏
    ├── Label (wordCountLabel) - 字数统计
    └── Label (statusLabel) - 状态
```

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

### McpEditorDialog.fxml
MCP 服务器编辑器对话框。

**控制器：** McpEditorDialogController

**结构：**
```
VBox (mcp-editor-dialog)
├── HBox (header) - 标题栏
│   ├── TextField (nameField) - 名称输入
│   ├── Button - 取消
│   └── Button - 保存
├── ScrollPane - 内容区域
│   └── VBox (content)
│       ├── ComboBox (connectionTypeCombo) - 连接类型
│       ├── VBox (stdioSection) - STDIO 配置
│       ├── VBox (sseSection) - SSE 配置
│       └── VBox (httpSection) - HTTP 配置
└── HBox (footer) - 底部栏
    └── Label (statusLabel) - 状态
```

### chat.html
聊天页面 HTML 模板。

**功能：**
- 显示用户和助手消息
- 支持 Markdown 渲染
- 流式消息更新
- JavaScript API 供 Java 调用

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
