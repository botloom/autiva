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
统一编辑器面板组件，合并原 RightSidebar 和 ContentPanel 的功能。默认隐藏，点击"编辑器"按钮后显示，像终端一样嵌入主区域。通过 TabPane 统一管理终端、变更列表、文件内容、diff 视图 4 类标签页。

**功能：**
- 内嵌文件树（可折叠，双击文件打开文件标签页）
- 终端标签页（JediTerminalView，支持 ANSI 解析、光标控制）
- 变更列表标签页（ListView<FileDiff>，订阅 DiffEvent 自动刷新，点击打开 diff 标签页）
- 文件内容标签页（RichTextFX CodeArea + 行号，按需打开）
- diff 视图标签页（RichTextFX StyleClassedTextArea + 审核按钮栏）
- 关闭按钮（隐藏整个面板，终端会话保持）

**控制器：** EditorPanelController

**结构：**
```
VBox (editorPanel, 默认隐藏 visible=false managed=false)
├── HBox (editor-panel__header)
│   ├── Button (toggleFileTreeButton) - 切换文件树按钮
│   ├── Region (弹性间隔)
│   └── Button (closeButton) - 关闭面板按钮
└── HBox (editor-panel__body, VBox.vgrow=ALWAYS)
    ├── VBox (fileTreePanel, 可折叠)
    │   ├── Label "项目文件"
    │   └── TreeView (fileTree, VBox.vgrow=ALWAYS)  ← 直接在 VBox 中，无 TitledPane 嵌套
    └── TabPane (tabPane, HBox.hgrow=ALWAYS, tabClosingPolicy=ALL_TABS)
```

**布局说明：** TreeView 直接放在 VBox 中并设置 `VBox.vgrow="ALWAYS"`，彻底消除了旧 RightSidebar 中 `TitledPane + SplitPane` 嵌套导致的高度传递失败问题。文件树面板可折叠（点击 toggleFileTreeButton 切换显隐），折叠后 TabPane 占满宽度。

**标签页类型：**
| 标签页 | id 格式 | closable | 内容 |
| ---- | ---- | ---- | ---- |
| 终端 | `terminal` | false | JediTerminalView |
| 变更 | `changes` | false | ListView<FileDiff> |
| 文件 | 文件路径绝对值 | true | VirtualizedScrollPane<CodeArea> |
| diff | `diff:` + diffId | true | VirtualizedScrollPane<StyleClassedTextArea> + 审核按钮栏 |

**样式表：** `@../style/editor-panel.css`

**样式类：**
- `.editor-panel`: 容器样式（深色背景 #1e1e1e，12px 圆角）
- `.editor-panel__header`: 标题栏（深色背景 #2d2d30，顶部圆角 12 12 0 0）
- `.editor-panel__icon-btn`: 切换文件树按钮（透明背景，hover #3d3d3d，圆形）
- `.editor-panel__close-btn`: 关闭按钮（同 icon-btn 样式）
- `.editor-panel__body`: 内容区 HBox
- `.editor-panel__tree-panel`: 文件树面板 VBox（深色 #252526，右边框 1px #3d3d3d）
- `.editor-panel__tree-title`: "项目文件" 标题（12px 加粗 #d4d4d4）
- `.editor-panel__tree`: TreeView（透明背景）
- `.editor-panel__tree .tree-cell`: 深色主题单元格（bg #1e1e1e，text #d4d4d4，hover #2d2d30，selected #094771）
- `.editor-panel__tabs`: TabPane（透明背景）
- `.editor-panel__tabs .tab-header-area`: 标签栏背景 #2d2d30
- `.editor-panel__tabs .tab`: 标签项（hover #2d2d30，selected 底边 #007aff）
- `.editor-panel__tabs .tab-label`: 标签文字 #d4d4d4，selected 白色
- `.editor-panel__code-scroll`: RichTextFX VirtualizedScrollPane 容器（透明背景，自定义滚动条）
- `.editor-panel__code-area`: 文件内容 CodeArea（等宽字体 SF Mono/Menlo/Consolas 12px，浅色 #d4d4d4）
- `.editor-panel__diff-area`: diff 视图 StyleClassedTextArea（等宽字体 12px，浅色 #d4d4d4）
- `.editor-panel__code-area .lineno` / `.editor-panel__diff-area .lineno`: 行号列（灰色 #858585，11px）
- `.diff-meta`: diff 元信息行（灰色 #858585）
- `.diff-hunk-header`: Hunk 头（蓝色背景 rgba(0,122,255,0.14)，蓝字 #4da3ff）
- `.diff-line-add`: 新增行（绿色背景 rgba(52,199,89,0.16)，绿字 #6cd97e）
- `.diff-line-remove`: 删除行（红色背景 rgba(255,59,48,0.16)，红字 #ff8a80）
- `.diff-line-context`: 上下文行（浅色文字 #d4d4d4）
- `.editor-panel__diff-actions`: diff 审核按钮栏
- `.editor-panel__diff-btn--approve`: 确定按钮（绿色 #34c759）
- `.editor-panel__diff-btn--reject`: 撤销按钮（红色 #ff3b30）
- `.editor-panel__loading-text`: 加载状态文字
- `.editor-panel__error-text`: 错误状态文字
- `.editor-panel__retry-btn`: 重试按钮
- `.jedi-terminal-view`: 终端视图（深色背景 #1e1e1e，底部圆角 0 0 12 12）

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
