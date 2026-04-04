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
-border-radius: 25px;   /* 胶囊圆角（搜索框） */
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

### home-page.css
主页样式。

**样式类：**
- `.home-page`: 主页容器
- `.home-page__icon`: 图标区域
- `.home-page__search-box`: 搜索框容器
- `.home-page__search-field`: 搜索输入框
- `.home-page__icon-btn`: 图标按钮

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

### mcp-page.css
MCP 页面样式。

**样式类：**
- `.mcp-page`: MCP 页容器
- `.mcp-page__scroll-pane`: 滚动面板
- `.mcp-page__list`: 列表容器
- `.mcp-page__card`: 服务器卡片
- `.mcp-page__card-header`: 卡片头部
- `.mcp-page__card-title`: 卡片标题
- `.mcp-page__card-type`: 类型标签
- `.mcp-page__card-btn`: 卡片按钮

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

### mcp-editor-dialog.css
MCP 编辑器对话框样式。

**样式类：**
- `.mcp-editor-dialog`: 对话框容器
- `.mcp-editor-dialog__header`: 头部
- `.mcp-editor-dialog__name-field`: 名称输入
- `.mcp-editor-dialog__btn`: 按钮
- `.mcp-editor-dialog__scroll-pane`: 滚动面板
- `.mcp-editor-dialog__content`: 内容区域
- `.mcp-editor-dialog__section`: 分节
- `.mcp-editor-dialog__section-label`: 分节标签
- `.mcp-editor-dialog__combo`: 下拉框
- `.mcp-editor-dialog__card`: 卡片
- `.mcp-editor-dialog__field-group`: 字段组
- `.mcp-editor-dialog__field-label`: 字段标签
- `.mcp-editor-dialog__field`: 输入字段
- `.mcp-editor-dialog__footer`: 底部栏
- `.mcp-editor-dialog__status`: 状态

### md-editor.css
Markdown 编辑器组件样式。

**样式类：**
- `.md-editor`: 组件容器
- `.md-editor__header`: 头部栏
- `.md-editor__name-field`: 名称输入框
- `.md-editor__btn`: 按钮
- `.md-editor__split-pane`: 分割面板
- `.md-editor__textarea`: 文本区域
- `.md-editor__footer`: 底部栏
- `.md-editor__word-count`: 字数统计
- `.md-editor__status`: 状态

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
