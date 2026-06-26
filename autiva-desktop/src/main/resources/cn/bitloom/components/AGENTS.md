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
统一编辑器面板组件，通过 StackPane 管理三个视图：终端、项目、变更。默认隐藏，点击 ButtonBar 右侧"终端"/"变更"/"项目"按钮 toggle 切换（相同视图再次点击则关闭），嵌入主区域右侧的 SplitPane 中，支持拖拽调整大小。面板无 header 栏，外层容器透明无 padding，直接铺满 mainSplit 区域。

**功能：**
- 三视图切换（终端/项目/变更），通过 StackPane + visible/managed 控制
- 终端视图（JediTerminalView，支持 ANSI 解析、光标控制）
- 项目视图（SplitPane：左侧文件树 + 右侧文件内容显示区）
- 变更视图（SplitPane：左侧变更文件列表 + 右侧 diff 显示区）
- 无关闭按钮，通过 ButtonBar 三个按钮 toggle 切换

**控制器：** EditorPanelController

**结构：**
```
VBox (editorPanel, #1e1e1e 背景 + 12px 圆角 + Rectangle clip 裁剪, 默认隐藏 visible=false managed=false)
└── StackPane (viewContainer, VBox.vgrow=ALWAYS)
    ├── VBox (terminalView, 默认隐藏) - 终端视图
    ├── SplitPane (projectSplit, 默认显示, HORIZONTAL) - 项目视图
    │   ├── VBox (fileTreePanel)
    │   │   └── TreeView (fileTree, VBox.vgrow=ALWAYS)
    │   └── VBox (fileContentPanel)
    │       └── Label (fileContentPlaceholder) "双击文件查看内容"
    └── SplitPane (changesSplit, 默认隐藏, HORIZONTAL) - 变更视图
        ├── VBox (diffListPanel)
        │   └── ListView (diffList, VBox.vgrow=ALWAYS)
        └── VBox (diffViewPanel)
            └── Label (diffPlaceholder) "点击变更文件查看差异"
```

**布局说明：**
- 外层 VBox 透明背景 + padding 8 8 8 0（上右下留白，左侧贴 SplitPane 分隔条），留白区域透出 SplitPane 背景色
- viewContainer（StackPane）承载 #1e1e1e 深色背景 + 12px 四角圆角，并通过 Rectangle clip（arcWidth/arcHeight=24）裁剪所有子视图（终端/项目/变更）的方角到圆角形状
- 无 header 栏、无标题文字（文件树和变更列表直接显示，无"项目文件"/"变更文件"标题）
- StackPane 通过 visible/managed 切换三视图，单一视图模式
- 项目和变更视图内部各有 SplitPane，支持左右拖拽调整比例
- 外层 index.fxml 的 SplitPane 支持面板与主区域拖拽调整大小

**样式表：** `@../style/editor-panel.css`

**样式类：**
- `.editor-panel`: 容器样式（透明背景，padding 8 8 8 0 上右下留白/左贴边）
- `.editor-panel__views`: 视图容器 StackPane（#1e1e1e 深色背景，12px 四角圆角）
- `.editor-panel__view`: 终端视图容器（深色背景 #1e1e1e）
- `.editor-panel__project-split` / `.editor-panel__changes-split`: 内部 SplitPane（透明背景，分隔条 #3d3d3d，hover #007aff）
- `.editor-panel__tree-panel`: 文件树面板 VBox（深色 #252526，右边框 1px #3d3d3d）
- `.editor-panel__tree`: TreeView（透明背景）
- `.editor-panel__tree .tree-cell`: 深色主题单元格
- `.editor-panel__content-panel` / `.editor-panel__diff-view-panel`: 右侧内容面板（深色 #1e1e1e）
- `.editor-panel__placeholder`: 占位符（灰色 #858585，居中）
- `.editor-panel__diff-list`: 变更列表 ListView（深色背景）
- `.editor-panel__code-scroll`: RichTextFX VirtualizedScrollPane 容器
- `.editor-panel__code-area`: 文件内容 CodeArea
- `.editor-panel__diff-area`: diff 视图 StyleClassedTextArea
- `.diff-meta` / `.diff-hunk-header` / `.diff-line-add` / `.diff-line-remove` / `.diff-line-context`: Diff 行级着色
- `.editor-panel__diff-actions`: diff 审核按钮栏
- `.editor-panel__diff-btn--approve` / `.editor-panel__diff-btn--reject`: 审核按钮
- `.editor-panel__loading-text` / `.editor-panel__error-text` / `.editor-panel__retry-btn`: 加载/错误状态
- `.jedi-terminal-view`: 终端视图（深色背景 #1e1e1e）

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
