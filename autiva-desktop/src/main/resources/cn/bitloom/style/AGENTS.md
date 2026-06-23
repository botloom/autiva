# CSS 目录

## 概述
本目录包含应用的样式文件，每个 FXML 对应一个独立的 CSS 文件。

## 设计规范

### 颜色系统
```css
/* 主色调 */
--primary-color: #0071e3;      /* Apple Blue */
--primary-hover: rgba(0, 0, 0, 0.1);

/* 文字颜色 */
--text-primary: #1d1d1f;       /* 主要文字 */
--text-secondary: #86868b;     /* 次要文字 */
--text-inverse: #ffffff;       /* 反色文字 */

/* 背景颜色 */
--bg-primary: #ffffff;         /* 主背景 */
--bg-secondary: #f5f5f7;       /* 次要背景 */
--bg-tertiary: rgba(0, 0, 0, 0.05);

/* 边框颜色 */
--border-color: rgba(0, 0, 0, 0.08);
```

### 字体系统
```css
/* 主字体 */
-font-family: "SF Pro Text", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;

/* 代码字体 */
-font-family: 'SF Mono', Monaco, monospace;
```

### 圆角系统
```css
-border-radius: 8px;    /* 小圆角（按钮、输入框） */
-border-radius: 12px;   /* 中圆角（卡片） */
-border-radius: 18px;   /* 大圆角（消息气泡） */
-border-radius: 25px;   /* 胶囊圆角（发送框） */
```

### 阴影系统
```css
/* 卡片阴影 */
-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.04), 12, 0, 0, 2);

/* 悬停阴影 */
-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.08), 20, 0, 0, 4);
```

## 样式文件列表

### index.css
主容器样式。

**样式类：**
- `.window-container`: 窗口容器

### button-bar.css
底部按钮栏样式。

**样式类：**
- `.button-bar`: 按钮栏容器
- `.button-bar__toggle-btn`: 切换按钮
- `.button-bar__status`: 状态标签
- `.dynamic-btn`: 动态按钮

### side-bar.css
侧边栏样式。

**样式类：**
- `.sidebar`: 侧边栏容器
- `.sidebar__content`: 内容区域
- `.sidebar__option`: 菜单项
- `.sidebar__option--active`: 激活状态
- `.sidebar__icon`: 图标
- `.sidebar__label`: 标签
- `.sidebar__divider`: 分隔线
- `.sidebar__section-label`: 区域标题（如"历史对话"）
- `.sidebar__history-scroll`: 历史对话滚动面板
- `.sidebar__history-list`: 历史对话列表
- `.sidebar__history-item`: 历史对话项
- `.sidebar__history-item--active`: 当前活跃对话高亮
- `.sidebar__history-item-title`: 对话标题
- `.sidebar__history-item-time`: 对话时间

### home-page.css
主页样式。

**样式类：**
- `.home-page`: 主页容器
- `.home-page__icon`: 图标区域
- `.home-page__chat-scroll-pane`: 聊天滚动面板
- `.home-page__send-box`: 发送框容器
- `.home-page__send-field`: 发送输入框
- `.home-page__icon-btn`: 图标按钮
- `.home-page__icon-btn--active`: 图标按钮激活状态（红色背景，用于录音状态）
- `.home-page__icon-btn--stop`: 图标按钮终止状态（红色背景，用于暂停流式生成）
- `.home-page__model-selector`: 模型选择器
- `.home-page__agent-selector`: 智能体选择器（胶囊形状，与 modelSelector 风格一致）
- `.home-page__agent-selector:hover`: 智能体选择器悬停状态
- `.home-page__agent-selector:pressed`: 智能体选择器按下状态
- `.chat-scroll-pane`: 聊天滚动面板
- `.chat-container`: 聊天消息容器
- `.chat-message`: 消息卡片基础样式
- `.chat-message--user`: 用户消息（蓝色渐变，右对齐）
- `.chat-message--assistant`: 助手消息（灰色背景，左对齐）
- `.chat-message--streaming`: 流式输出状态
- `.chat-message--tool`: 工具消息
- `.chat-message--tool-request`: 工具请求（蓝色背景）
- `.chat-message--tool-response`: 工具响应（绿色背景，降级模式）
- `.chat-message--tool-success`: 工具成功响应（绿色背景 #f0fff4）
- `.chat-message--tool-error`: 工具错误响应（红色背景 #fef2f2）
- `.chat-message--tool-warning`: 工具警告响应（黄色背景 #fffbeb）
- `.chat-message__tool-status-dot`: 状态圆点（默认绿色）
- `.chat-message__tool-status-dot--success`: 成功状态圆点（#22c55e）
- `.chat-message__tool-status-dot--error`: 错误状态圆点（#ef4444）
- `.chat-message__tool-status-dot--warning`: 警告状态圆点（#f59e0b）
- `.chat-message__tool-summary`: 摘要文本（12px，灰色 #86868b）
- `.chat-message__tool-data`: 结构化数据区
- `.chat-message__tool-data-item`: 数据条目（浅色背景 pill）
- `.chat-message__tool-data-key`: 数据键名（11px 粗体灰色等宽字体）
- `.chat-message__tool-data-value`: 数据值（11px 深色等宽字体）
- `.chat-message__tool-output-divider`: 输出区分割线
- `.chat-message__tool-output`: 输出区（12px 等宽字体）
- `.chat-message__task-messages .chat-message--tool`: 子智能体内嵌工具消息（无阴影、紧凑内边距、8px圆角）
- `.chat-message__task-messages .chat-message__tool-header`: 子智能体内嵌工具标题（底部1px分割线，与主聊天框工具卡片一致）
- `.chat-message--question`: 问题卡片（紫色渐变）
- `.chat-message__question-option--other`: "其他"选项按钮（虚线边框）
- `.chat-message__question-other-input`: "其他"文本输入框
- `.chat-message__question-other-input:focused`: "其他"文本输入框聚焦状态
- `.chat-message--todo`: 待办事项卡片（黄色渐变）
- `.md-*`: Markdown 渲染样式（段落、标题、代码块、列表、引用等）

### settings-page.css
设置页面样式。

**样式类：**
- `.settings-page`: 设置页容器
- `.settings-page__scroll-pane`: 滚动面板
- `.settings-page__content`: 内容区域
- `.settings-page__section-label`: 分节标签
- `.settings-page__card`: 卡片
- `.settings-page__row`: 行
- `.settings-page__row-title`: 行标题
- `.settings-page__row-subtitle`: 行副标题
- `.settings-page__path-row`: 路径行
- `.settings-page__path-field`: 路径输入框
- `.settings-page__secondary-btn`: 次要按钮
- `.settings-page__qr-stack`: 二维码 StackPane 容器（180x180）
- `.settings-page__qr-image`: 二维码图片（180x180，带阴影）
- `.settings-page__qr-overlay`: 二维码覆盖层（半透明白色背景，180x180）
- `.settings-page__qr-hint`: 二维码提示文案
- `.settings-page__rebind-btn`: 重新加载按钮（蓝色文字，浅灰背景）

### skill-page.css
技能页面样式。

**样式类：**
- `.skill-page`: 技能页容器
- `.skill-page__scroll-pane`: 滚动面板
- `.skill-page__list`: 列表容器
- `.skill-page__empty`: 空状态
- `.skill-page__card`: 技能卡片
- `.skill-page__card-header`: 卡片头部
- `.skill-page__card-title`: 卡片标题
- `.skill-page__card-btn`: 卡片按钮

### task-page.css
任务页面样式。

**样式类：**
- `.task-page`: 任务页容器
- `.task-page__scroll-pane`: 滚动面板
- `.task-page__list`: 列表容器

### agent-page.css
智能体配置页面样式。

**样式类：**
- `.agent-page`: 智能体页容器
- `.agent-page__scroll-pane`: 滚动面板
- `.agent-page__list`: 列表容器
- `.agent-page__empty`: 空状态
- `.agent-page__agent-card`: 智能体卡片
- `.agent-page__agent-header`: 卡片头部
- `.agent-page__agent-title`: 卡片标题
- `.agent-page__file-list`: 文件列表
- `.agent-page__file-card`: 文件卡片
- `.agent-page__file-icon`: 文件图标
- `.agent-page__file-name`: 文件名
- `.agent-page__file-btn`: 文件按钮

### file-editor.css
通用文件编辑器对话框样式，IDEA风格，毛玻璃效果。

**样式类：**
- `.file-editor`: 根容器，浅灰背景 #f5f5f7
- `.file-editor__toolbar`: 顶部工具栏（毛玻璃 rgba(255,255,255,0.72)）
- `.file-editor__toolbar-btn`: 工具栏按钮（系统风格：蓝色文字、8px圆角、浅灰背景）
- `.file-editor__content-area`: 主内容区容器
- `.file-editor__left-bar`: 左侧按钮栏（毛玻璃样式，40px宽）
- `.file-editor__right-bar`: 右侧按钮栏（毛玻璃样式，40px宽）
- `.file-editor__side-btn`: 侧边栏图标按钮（8px圆角，与sidebar__option风格一致）
- `.file-editor__split-pane`: 分割面板（毛玻璃背景）
- `.file-editor__tree-panel`: 文件树面板（浅色背景，8px圆角，微阴影）
- `.file-editor__tree`: 文件树
- `.file-editor__editor-panel`: 编辑器面板（深色背景 #1e1e1e，8px圆角，微阴影）
- `.file-editor__tab-pane`: Tab面板（深色背景 #2d2d30，上方圆角）
- `.file-editor__editor-box`: 编辑器内容容器（下方圆角）
- `.file-editor__line-numbers`: 行号区域（深色背景，灰色文字 #858585）
- `.file-editor__code-editor`: 代码编辑器（深色背景 #1e1e1e，浅色文字 #d4d4d4）
- `.file-editor__empty`: 空状态（下方圆角）
- `.file-editor__footer`: 底部状态栏（蓝色背景 #0071e3）
- `.file-editor__status`: 状态文字（白色）
- `.file-editor__context-menu`: 右键菜单
- `.file-editor__dialog`: 自定义弹窗
- `.file-editor__dialog-title`: 弹窗标题
- `.file-editor__dialog-input`: 弹窗输入框
- `.file-editor__dialog-btn`: 弹窗按钮
- `.file-editor__dialog-btn--primary`: 主要按钮（蓝色）
- `.file-editor__dialog-btn--danger`: 危险按钮（红色）

### md-editor-dialog.css
Markdown 编辑器对话框样式。

**样式类：**
- `.md-editor-dialog`: 对话框容器
- `.md-editor-dialog__header`: 头部
- `.md-editor-dialog__title-field`: 标题输入
- `.md-editor-dialog__btn`: 按钮
- `.md-editor-dialog__split-pane`: 分割面板
- `.md-editor-dialog__textarea`: 文本区域
- `.md-editor-dialog__footer`: 底部栏
- `.md-editor-dialog__word-count`: 字数统计
- `.md-editor-dialog__status`: 状态

### browser-dialog.css
浏览器对话框样式。

**样式类：**
- `.browser-dialog`: 对话框容器
- `.browser-dialog__toolbar`: 工具栏
- `.browser-dialog__nav-btn`: 导航按钮
- `.browser-dialog__url-field`: URL 输入框
- `.browser-dialog__webview`: WebView
- `.browser-dialog__status-bar`: 状态栏
- `.browser-dialog__status`: 状态

### canvas-page.css
画布页面样式，Apple 风格。

**样式类：**
- `.canvas-page`: 画布页容器
- `.canvas-page__floating-toolbar`: 悬浮工具栏（半透明毛玻璃 rgba(255,255,255,0.92)，12px圆角）
- `.canvas-page__tool-btn`: 工具按钮（8px圆角，选中时 Apple Blue 高亮）
- `.canvas-page__property-panel`: 左侧属性面板（悬浮卡片）
- `.canvas-page__panel-title`: 面板标题
- `.canvas-page__prop-label`: 属性标签
- `.canvas-page__color-swatch-row`: 色块行
- `.canvas-page__option-btn`: 选项按钮（6px圆角，选中时 Apple Blue）
- `.canvas-page__slider`: 滑块
- `.canvas-page__layer-panel`: 图层面板
- `.canvas-page__layer-list`: 图层列表

### gep-page.css
基因进化管理页面样式，对齐 AgentPage 设计规范（白底、窄列卡片列表、灰底蓝字按钮）。

**样式类：**
- `.gep-page`: 页面容器（白色背景，padding 24，top-center）
- `.gep-page__scroll-pane`: 滚动面板（pref-width 600, max-width 700, 6px 滚动条）
- `.gep-page__content`: 内容区域（padding 0 32 32 32, spacing 12）
- `.gep-page__section-label`: 分节标签（13px/600/#86868b）
- `.gep-page__card`: 卡片（白色，12px圆角，边框 rgba(0,0,0,0.08)，阴影 12/20）
- `.gep-page__card-title`: 卡片标题（18px/600/#1d1d1f）
- `.gep-page__card-description`: 卡片描述（14px/#86868b）
- `.gep-page__row`: 行（spacing 8, center-left）
- `.gep-page__row-title`: 行标题（15px/500/#1d1d1f）
- `.gep-page__row-subtitle`: 行副标题（13px/#86868b）
- `.gep-page__btn`: 按钮（灰底蓝字，12px/600，8px圆角，#0071e3）
- `.gep-page__btn--danger`: 危险按钮（红色文字 #ff3b30）
- `.gep-page__tag`: 标签（11px/600，4px圆角）
- `.gep-page__tag--REPAIR`: 修复分类（橙色）
- `.gep-page__tag--OPTIMIZE`: 优化分类（绿色）
- `.gep-page__tag--INNOVATE`: 创新分类（紫色）
- `.gep-page__tag--enabled`: 启用状态（绿色）
- `.gep-page__tag--disabled`: 禁用状态（灰色）
- `.gep-page__tag--runtime`: 运行时类型（蓝色）
- `.gep-page__tag--success`: 成功状态（绿色）
- `.gep-page__tag--pending`: 待定状态（橙色）
- `.gep-page__tag--failed`: 失败状态（红色）
- `.gep-page__tag--unknown`: 未知状态（灰色）
- `.gep-page__gene-detail`: 基因展开详情区
- `.gep-page__gene-section-title`: 详情子区标题（13px/600/#86868b）
- `.gep-page__gene-section-item`: 详情子区条目（13px/#424245）
- `.gep-page__gene-code`: 代码块（深色背景，等宽字体）
- `.gep-page__gene-meta`: 元信息文本（13px/#86868b）
- `.gep-page__gene-version-row`: 版本历史行
- `.gep-page__gene-version-text`: 版本历史文本（13px/#424245）
- `.gep-page__gene-version-time`: 版本历史时间（13px/#86868b）
- `.gep-page__strategy-select`: 策略选择器
- `.gep-page__empty`: 空状态（14px/#86868b/padding 40）

### scroll-bar.css
滚动条样式。

**功能：**
- 自定义滚动条外观
- 透明滚动条，悬停时显示

## BEM 命名规范
使用 Block__Element--Modifier 命名规范：
- Block: 组件名（如 `button-bar`）
- Element: 元素（如 `__toggle-btn`）
- Modifier: 状态（如 `--active`）

示例：
```css
.sidebar { }              /* Block */
.sidebar__option { }      /* Element */
.sidebar__option--active { } /* Modifier */
```

## 注意事项
1. 每个页面/对话框有独立的 CSS 文件
2. 使用 BEM 命名规范
3. 遵循 Apple 设计规范
4. 颜色、字体、圆角保持一致性
5. 使用 rgba 实现透明效果
