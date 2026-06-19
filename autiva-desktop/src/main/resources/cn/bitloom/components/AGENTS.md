# Components 目录

## 概述
本目录包含可复用的 UI 组件 FXML 文件。

## 组件列表

### ButtonBar.fxml
底部按钮栏组件。

**功能：**
- 显示侧边栏切换按钮
- 动态按钮容器（根据页面变化）
- 状态标签显示

**控制器：** ButtonBarController

**结构：**
```
HBox (button-bar)
├── Button (sidebarButton) - 侧边栏切换按钮
├── HBox (dynamicButtonContainer) - 动态按钮容器
└── Label (statusBarLabel) - 状态标签
```

**样式类：**
- `.button-bar`: 容器样式
- `.button-bar__toggle-btn`: 切换按钮样式
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
