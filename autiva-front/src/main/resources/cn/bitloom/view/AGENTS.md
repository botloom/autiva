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
│   └── ImageView - 应用图标
├── ScrollPane (chatScrollPane) - 聊天内容滚动容器
│   └── VBox (chatContainer) - 聊天消息容器
└── VBox (searchBox) - 搜索输入框
    ├── FlowPane (fileTagsPane) - 文件标签容器（默认隐藏，添加文件后显示）
    ├── TextArea (searchField) - 输入框（支持多行、自动换行、自动调整高度）
    └── HBox - 按钮组
        ├── Button (addFileButton) - 添加文件按钮
        ├── ComboBox (modelSelector) - 模型选择器（胶囊形状）
        ├── Button (searchButton) - 发送按钮
        └── Button (stopButton) - 终止按钮（流式生成时显示，红色，点击暂停生成并保留部分响应）
```

**特性：**
- ScrollPane + VBox 替代 WebView 显示聊天内容
- 消息卡片使用纯 JavaFX 组件（UserMessageCard, AssistantMessageCard, ToolMessageCard, QuestionCard, TodoCard）
- 图标在首次发送后动画上移
- 响应式宽度设计
- 搜索框具有悬浮阴影效果（hover 时加深），样式与 MCP 卡片一致
- 输入区域使用 TextArea 替代 TextField，支持多行输入和自动换行
- TextArea 根据内容自动调整高度（最小 48px，最大 200px）
- Enter 键发送消息，Shift+Enter 换行
- 添加文件按钮打开 FileChooser，选中的文件以标签形式显示在输入区域上方
- 文件标签显示文件图标、文件名和关闭按钮，支持逐个移除
- 模型选择器使用胶囊形状的 ComboBox，直接显示模型名称，点击下拉选择
- 无下拉箭头，宽度自适应模型名称，最小宽度为圆形（37px）
- 终止按钮：流式生成时自动切换显示（替代发送按钮），红色圆形按钮，点击暂停当前生成并保留部分响应，暂停后发送按钮重新显示

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
- 钉钉配置（Client ID / Client Secret）
- 微信配置（StackPane 二维码区域，覆盖层设计：已连接绿色对号 / 二维码过期刷新）
- 搜索配置（博查 API Key）
- DeepSeek 配置（API Key / Base URL / Completions Path / 模型）
- 关于

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

### CanvasDialog.fxml
画布弹窗视图，包含画布内容（已合并 CanvasPage）。

**控制器：** cn.bitloom.controller.CanvasDialogController

**结构：**
```
BorderPane (rootContainer) - 主内容区
└── VBox (canvasPage) - 画布内容
    └── AnchorPane (canvasAnchor)
        ├── StackPane (canvasContainer) - 画布容器
        ├── HBox - 悬浮工具栏容器（顶部居中）
        │   └── HBox (floatingToolbar) - 悬浮工具栏
        │       ├── ToggleButton (selectBtn) - 选择工具
        │       ├── ToggleButton (rectangleBtn) - 矩形工具
        │       ├── ToggleButton (diamondBtn) - 菱形工具
        │       ├── ToggleButton (ellipseBtn) - 椭圆工具
        │       ├── ToggleButton (arrowBtn) - 箭头工具
        │       ├── ToggleButton (lineBtn) - 线条工具
        │       ├── ToggleButton (freehandBtn) - 手绘工具
        │       └── ToggleButton (textBtn) - 文字工具
        ├── VBox (propertyPanel) - 左侧属性面板（悬浮卡片）
        │   ├── 描边颜色色块
        │   ├── 填充颜色色块
        │   ├── 线宽选项
        │   ├── 边框样式选项
        │   ├── 手绘风格选项
        │   ├── 边角选项
        │   └── 透明度滑块
        └── VBox (layerPanel) - 右下角图层面板
            ├── Label - "图层" 标题
            └── ListView (layerListView) - 图层列表
```

**特性：**
- 使用 WindowManager 创建弹窗，使用操作系统默认标题栏（StageStyle.UNIFIED）
- 可调整大小，最小 800x500，默认 1100x750
- 工具栏使用 ToggleGroup 实现工具互斥选择，仅包含8个绘图工具按钮
- 悬浮工具栏采用 Apple 风格，半透明毛玻璃背景，圆角12px
- 属性面板按工具动态显示：选中工具时立即显示该工具的属性配置
  - 选择工具：仅在有选中元素时显示全部属性
  - 矩形/菱形/椭圆：描边、填充、线宽、样式、手绘、边角、透明度
  - 箭头/线条：描边、线宽、样式、手绘、透明度
  - 手绘：描边、线宽、透明度
  - 文字：描边（文字颜色）、透明度
- 画布区域使用 StackPane 支持叠加层
- 文字渲染使用手写字体（Segoe Script/Bradley Hand/Comic Sans MS）
- 缩放通过 Ctrl+滚轮操作

### GepPage.fxml
基因进化管理页面视图。

**控制器：** GepPageController

**结构：**
```
VBox (gepPage)
└── ScrollPane
    └── VBox (gep-page__content)
        ├── 进化策略卡片
        │   └── HBox - 策略选择 + 执行按钮
        ├── 基因库卡片
        │   └── VBox (genesContainer) - 基因列表项
        ├── 胶囊库卡片
        │   └── VBox (capsulesContainer) - 胶囊列表项
        └── 进化事件卡片
            └── VBox (eventsContainer) - 事件列表项
```

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
