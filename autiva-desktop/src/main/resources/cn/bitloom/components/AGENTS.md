# Components 目录

## 概述
本目录包含可复用的 UI 组件 FXML 文件。

## 组件列表

### ButtonBar.fxml
底部按钮栏组件。

**功能：**
- 显示侧边栏切换按钮（panel-left.svg 图标）
- 项目选择 MenuButton（coder 智能体时显示，folder.svg 图标，下拉菜单：选择文件夹+最近项目）
- 分支显示按钮（coder 智能体时显示，git-branch.svg 图标，默认只显示图标，选择项目后显示分支名，disabled）
- 动态按钮容器（根据页面变化）
- 终端按钮（terminal.svg 图标）
- 右侧边栏折叠按钮（panel-right.svg 图标）
- 状态标签显示

**控制器：** ButtonBarController

**结构：**
```
HBox (button-bar)
├── Button (sidebarButton) - 侧边栏切换按钮
├── MenuButton (projectSelectButton) - 项目选择下拉菜单（默认隐藏）
├── Button (branchDisplayButton) - 分支显示按钮（默认隐藏）
├── HBox (dynamicButtonContainer) - 动态按钮容器
├── Button (terminalButton) - 终端按钮
├── Button (toggleRightPanelButton) - 右侧边栏折叠按钮
└── Label (statusBarLabel) - 状态标签
```

**样式类：**
- `.button-bar`: 容器样式
- `.button-bar__action-btn`: 操作按钮样式（37x37，圆角8px）
- `.button-bar__status`: 状态标签样式

### SideBar.fxml
侧边栏组件。

**功能：**
- 页面导航菜单
- 显示/隐藏切换
- 当前页面高亮
- "新聊天"按钮：创建新 session
- 历史对话列表：显示桌面端所有 session，点击切换

**控制器：** SideBarController

**结构：**
```
VBox (sideBar)
└── VBox (sidebar__content)
    ├── HBox (homeOption) - 新聊天
    ├── HBox (agentOption) - 智能体
    ├── HBox (skillOption) - 技能
    ├── HBox (gepOption) - 进化
    ├── HBox (taskOption) - 任务
    ├── Region (sidebar__divider) - 分隔线
    ├── Label - "历史对话"标题
    ├── ScrollPane (historyScrollPane) - 历史对话滚动区域
    │   └── VBox (historyList) - 历史对话列表
    └── HBox (settingsOption) - 设置
```

**样式类：**
- `.sidebar`: 容器样式
- `.sidebar__content`: 内容区域
- `.sidebar__option`: 菜单项样式（固定高度 40px，不会被压缩）
- `.sidebar__option--active`: 激活状态样式
- `.sidebar__icon`: 图标样式
- `.sidebar__label`: 标签样式
- `.sidebar__divider`: 分隔线样式（固定高度 1px，不会被压缩）
- `.sidebar__section-label`: 区域标题样式（如"历史对话"，固定高度 27px，不会被压缩）
- `.sidebar__history-scroll`: 历史对话滚动面板样式
- `.sidebar__history-list`: 历史对话列表样式
- `.sidebar__history-item`: 历史对话项样式
- `.sidebar__history-item--active`: 当前活跃对话高亮样式
- `.sidebar__history-item-title`: 对话标题样式
- `.sidebar__history-item-time`: 对话时间样式

**布局策略：**
- 菜单项（`.sidebar__option`）、分隔线（`.sidebar__divider`）、区域标题（`.sidebar__section-label`）都设置了 `min-height`，确保在历史对话列表变多时不会被压缩
- 只有 `ScrollPane` 会随着内容增长而扩展，其他元素保持固定高度

### EditorPanel.fxml
统一编辑器面板组件，通过 StackPane 管理四个视图：终端、项目、工具调用、待办。默认隐藏，点击 ButtonBar 右侧"终端"/"项目"/"工具"/"待办"按钮 toggle 切换（相同视图再次点击则关闭），嵌入主区域右侧的 SplitPane 中，支持拖拽调整大小。面板无 header 栏，外层容器透明无 padding，直接铺满 mainSplit 区域。Diff 不再独立成视图，而是注入到项目视图右侧内容区（单栏行内高亮）。

**功能：**
- 四视图切换（终端/项目/工具调用/待办），通过 StackPane + visible/managed 控制
- 终端视图（JediTerminalView，支持 ANSI 解析、光标控制）
- 项目视图（SplitPane：左侧文件树 + 右侧文件内容/diff 显示区，双击文件在右侧显示带语法高亮的代码；点击对话框上方 diff 文件卡片在右侧渲染 diff）
- 工具调用视图（ScrollPane + VBox，直接展示 ToolMessageCard，不再通过 ToolGroupCard 分组，样式复用 home-page.css 与聊天区域一致）
- 待办视图（ScrollPane + VBox，展示 TodoCard，样式复用 home-page.css 与聊天区域一致）
- 无关闭按钮，通过 ButtonBar 四个按钮 toggle 切换

**控制器：** EditorPanelController

**结构：**
```
VBox (editorPanel, #1c1c1e 背景 + 12px 圆角 + Rectangle clip 裁剪, 默认隐藏 visible=false managed=false)
└── StackPane (viewContainer, VBox.vgrow=ALWAYS)
    ├── VBox (terminalView, 默认隐藏) - 终端视图
    ├── SplitPane (projectSplit, 默认显示, HORIZONTAL, dividerPositions=0.35) - 项目视图
    │   ├── VBox (treePanel, minWidth=140, prefWidth=240)
    │   │   └── TreeView (fileTree, VBox.vgrow=ALWAYS) - FileTreeCell 富渲染
    │   └── VBox (fileContentPanel, minWidth=200) - 动态注入 CodeArea（文件内容或 diff 渲染）
    │       └── Label (fileContentPlaceholder) "双击文件查看内容"
    ├── VBox (toolCallsView, 默认隐藏) - 工具调用视图
    │   └── ScrollPane (editor-panel__card-scroll, fitToWidth=true)
    │       └── VBox (toolCallsContainer, editor-panel__card-container) - ToolMessageCard 卡片
    └── VBox (todoView, 默认隐藏) - 待办视图
        └── ScrollPane (editor-panel__card-scroll, fitToWidth=true)
            └── VBox (todoContainer, editor-panel__card-container) - TodoCard 卡片
```

**布局说明：**
- 外层 VBox 透明背景 + padding 8 8 8 0（上右下留白，左侧贴 SplitPane 分隔条），留白区域透出 SplitPane 背景色
- viewContainer（StackPane）承载 #1c1c1e 深色背景 + 12px 四角圆角，并通过 Rectangle clip（arcWidth/arcHeight=24）裁剪所有子视图（终端/项目/工具调用/待办）的方角到圆角形状
- 无 header 栏、无标题文字（文件树直接显示，无"项目文件"标题）
- StackPane 通过 visible/managed 切换四视图，单一视图模式
- 项目视图内部 SplitPane 支持左右拖拽调整比例
- 外层 index.fxml 的 SplitPane 支持面板与主区域拖拽调整大小
- **动态内容注入**：fileContentPanel 初始显示占位符 Label，运行时由控制器替换为富内容
  - 文件内容：CodeArea + 行号 + SyntaxHighlighter，外层包 VirtualizedScrollPane
  - Diff 渲染：StackPane(VirtualizedScrollPane<CodeArea> + 顶部悬浮横幅)，单栏行内高亮（ADD 行绿色背景，REMOVE 行红色背景+删除线）

**样式表：** `@../style/editor-panel.css, @../style/home-page.css`（加载两个样式表：editor-panel.css 提供深色主题骨架，home-page.css 提供工具卡片/TodoCard 浅色样式以保证与聊天区域显示完全一致）

**样式类（详见 style/AGENTS.md 的 editor-panel.css 部分）：**
- `.editor-panel`: 容器样式（透明背景，padding 8 8 8 0）
- `.editor-panel__views`: 视图容器 StackPane（#1c1c1e 深色背景，12px 圆角）
- `.editor-panel__view`: 终端视图容器
- `.editor-panel__project-split`: 内部 SplitPane
- `.editor-panel__tree-panel`: 文件树面板 VBox
- `.editor-panel__tree`: TreeView
- `.editor-panel__content-panel`: 右侧内容面板（同时承载 diff 渲染）
- `.editor-panel__placeholder`: 占位符
- 代码与 Diff 区相关：`.editor-panel__code-scroll` / `.editor-panel__code-area` / `.editor-panel__diff-area` / `.lineno`
- 语法高亮 Token：`.syntax-keyword` / `.syntax-string` / `.syntax-comment` / `.syntax-number` / `.syntax-annotation` / `.syntax-literal` / `.syntax-type` / `.syntax-key` / `.syntax-operator`
- Diff 悬浮横幅：`.diff-banner` / `.diff-banner__path` / `.diff-banner__btn` / `.diff-banner__btn--reject` / `.diff-banner__btn--approve`
- Diff 行级（单栏行内高亮）：`.diff-line-add` / `.diff-line-remove-marker` / `.diff-lineno` / `.diff-lineno--single`
- 状态：`.editor-panel__loading-text` / `.editor-panel__error-text` / `.editor-panel__retry-btn`
- 终端：`.jedi-terminal-view`

## 使用方式

在 FXML 中引用组件：
```xml
<fx:include source="components/SideBar.fxml" fx:id="sideBar"/>
<fx:include source="components/ButtonBar.fxml" fx:id="buttonBar"/>
```

## 设计原则
1. 组件独立可复用
2. 每个组件有独立的控制器
3. 样式通过 CSS 文件定义
4. 使用 BEM 命名规范

## 注意事项
1. 组件通过 fx:include 引入或作为独立对话框加载
2. 控制器需要在 index.fxml 中配置（对于 include 方式）
3. 样式文件路径使用相对路径
4. 组件 ID 需要与控制器中的 @FXML 字段对应
