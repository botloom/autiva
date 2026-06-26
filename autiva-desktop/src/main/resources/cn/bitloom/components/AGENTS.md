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

### RightSidebar.fxml
右侧边栏组件，管理项目文件树和修改文件列表（diff），是主区域的一部分。

**功能：**
- 项目文件树展示（双击文件在内容面板显示）
- 修改文件列表（diff，点击在内容面板显示 diff 视图）
- 订阅 DiffEvent 自动刷新 diff 列表

**控制器：** RightSidebarController

**结构：**
```
VBox (rightSidebar, 默认隐藏)
└── SplitPane (splitPane, 垂直方向)
    ├── VBox
    │   └── TitledPane "项目文件"
    │       └── TreeView (fileTree)
    └── VBox
        └── TitledPane "修改文件"
            └── ListView (diffList)
```

**样式表：** `@../style/right-sidebar.css`

**样式类：**
- `.right-sidebar`: 容器样式（白色背景，左边框分隔线）
- `.right-sidebar__split`: 分割面板
- `.right-sidebar__titled-pane`: Apple 风格折叠面板
- `.right-sidebar__file-tree`: 文件树
- `.right-sidebar__diff-list`: diff 列表

### ContentPanel.fxml
内容面板组件，管理终端、文件内容、diff 视图的显示，与主区域并列的独立区域。

**功能：**
- 显示文件内容（等宽字体）
- 显示 diff 视图（带审核按钮：确定/撤销）
- 显示终端（JediTerminalView，支持 ANSI 解析、光标控制）
- 加载状态和错误状态显示

**控制器：** ContentPanelController

**结构：**
```
VBox (contentPanel, 默认隐藏)
├── HBox (content-panel__header)
│   ├── Label (contentTitle)
│   ├── Region (弹性间隔)
│   └── Button (closeContentButton)
└── ScrollPane (contentScrollPane)
    └── VBox (contentContainer)
```

**样式表：** `@../style/content-panel.css`

**样式类：**
- `.content-panel`: 容器样式（深色背景 #1e1e1e，12px 圆角）
- `.content-panel__header`: 标题栏（深色背景 #2d2d30，顶部圆角 12 12 0 0）
- `.content-panel__title`: 标题文字（浅色 #d4d4d4）
- `.content-panel__close-btn`: 关闭按钮（Apple 风格圆形，hover 背景 #3d3d3d）
- `.content-panel__container`: 内容容器
- `.content-panel__file-content`: 文件内容（浅色文字 #d4d4d4，等宽字体）
- `.content-panel__diff-content`: diff 内容（浅色文字 #d4d4d4，等宽字体）
- `.content-panel__diff-btn--approve`: 确定按钮（绿色 #34c759）
- `.content-panel__diff-btn--reject`: 撤销按钮（红色 #ff3b30）
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
