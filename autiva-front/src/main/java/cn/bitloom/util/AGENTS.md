# Util 包

## 概述
本包提供了各种工具类，包括浏览器管理、线程池管理和 Spring 上下文工具。

## 核心类

### BrowserManager
浏览器管理器，封装 Playwright 浏览器操作。

**功能：**
- 启动 Chrome 浏览器（CDP 模式）
- 打开新页面并导航
- 关闭浏览器

**核心方法：**
- `open(url)`: 打开 URL，返回 Page 对象
- `close()`: 关闭浏览器和相关资源

**配置：**
- 使用 CDP 协议连接 Chrome
- 默认端口：9222
- 浏览器路径从 Store.browserPath 获取

**使用示例：**
```java
Page page = BrowserManager.open("https://example.com");
// 操作页面...
BrowserManager.close();
```

### ExecutorManager
线程池管理器，提供应用级别的线程池。

**线程池：**
- `platformTaskExecutor`: 平台任务线程池（5 线程）
- `teammateExecutor`: 子智能体线程池（5 线程）

**核心方法：**
- `getPlatformTaskExecutor()`: 获取平台任务线程池
- `getTeammateExecutor()`: 获取子智能体线程池
- `close()`: 关闭所有线程池

**使用示例：**
```java
ExecutorService executor = ExecutorManager.getPlatformTaskExecutor();
executor.submit(() -> {
    // 执行任务...
});

// 应用关闭时
ExecutorManager.close();
```

### SpringContextUtil
Spring 上下文工具，提供静态方法获取 Bean。

**核心方法：**
- `getBean(Class<T>)`: 按类型获取 Bean
- `getBean(String)`: 按名称获取 Bean
- `getBean(String, Class<T>)`: 按名称和类型获取 Bean

**使用示例：**
```java
MyService service = SpringContextUtil.getBean(MyService.class);
Object bean = SpringContextUtil.getBean("myBean");
MyService service2 = SpringContextUtil.getBean("myService", MyService.class);
```

**实现原理：**
- 实现 ApplicationContextAware 接口
- Spring 自动注入 ApplicationContext
- 提供静态方法访问

### AlertUtil
对话框工具类，提供统一的 Alert 对话框方法。

**核心方法：**
- `showInfo(String message)`: 显示信息对话框
- `showInfo(String title, String message, Window owner)`: 显示信息对话框（带标题和所有者）
- `showWarning(String message)`: 显示警告对话框
- `showError(String message)`: 显示错误对话框
- `showConfirm(String message)`: 显示确认对话框，返回用户是否点击了确定
- `showConfirm(String title, String message, Window owner)`: 显示确认对话框（带标题和所有者）

**设计说明：**
- 统一了所有控制器中的 Alert 创建逻辑
- 消除了各控制器中重复的 showAlert 方法
- 确认对话框返回 boolean，简化调用方的判断逻辑

### MarkdownUtil
Markdown 渲染工具类，将 Markdown 文本转换为 HTML。

**核心方法：**
- `renderMarkdown(String)`: 将 Markdown 渲染为 HTML 字符串

**依赖：**
- commonmark: Markdown 解析和 HTML 渲染

### MarkdownFxRenderer
Markdown → JavaFX Node 渲染器，将 Markdown 文本直接渲染为 JavaFX 节点树，无需 WebView。

**核心方法：**
- `render(String markdown)`: 将 Markdown 渲染为 VBox（包含 JavaFX Node 子树）

**支持的 Markdown 元素：**
- 段落 → TextFlow（行间距1.6倍，增强可读性）
- 标题（h1-h6）→ TextFlow（不同字号/粗细/边框装饰）
  - h1: 28px，底部双线边框
  - h2: 22px，底部单线边框
  - h3-h6: 18px-14px，递减字号
- 粗体/斜体 → Text（FontWeight/FontPosture）
- 行内代码 → Text（等宽字体 + 红色文字 + 浅红背景）
- 代码块 → VBox（语言标签 + 复制按钮 + 语法高亮代码）
  - 支持 Java/Python/JavaScript/TypeScript 语法高亮
  - 深色主题（One Dark 风格）
  - 关键字、字符串、数字、注释不同颜色
- 有序/无序列表 → VBox + HBox（蓝色标记符，加粗）
- 引用 → HBox（左侧蓝色竖条 + 浅蓝背景 + 圆角）
- 链接 → Hyperlink（蓝色文字，悬停下划线）
- 图片 → Text（显示标题或占位符）
- 分隔线 → Separator
- HTML 块 → TextFlow（等宽字体）
- 表格 → VBox（表头行 + 数据行，支持样式美化）

**实现原理：**
- 使用 commonmark Parser 解析 Markdown 为 AST
- 支持 GFM 表格扩展（commonmark-ext-gfm-tables）
- 递归遍历 AST，为每种节点类型创建对应的 JavaFX 节点
- 块级元素（Paragraph, Heading, CodeBlock 等）创建容器节点
- 行内元素（Text, Emphasis, Code, Link 等）创建 Text/Hyperlink 节点
- 代码块支持语法高亮（基于正则表达式实现）
- 代码块支持复制到剪贴板功能
- 根容器 VBox 和表格 ScrollPane 均设置 `setFocusTraversable(false)`，防止点击时焦点环导致内容缩进偏移

**样式类：**
- `.md-content`: Markdown 容器
- `.md-paragraph`: 段落
- `.md-heading`, `.md-heading-1` ~ `.md-heading-6`: 标题
- `.md-code-block`: 代码块容器
- `.md-code-header`: 代码块头部
- `.md-code-lang`: 语言标签
- `.md-code-copy`: 复制按钮
- `.md-code-content`: 代码内容
- `.md-code-text`: 普通代码文本
- `.md-code-keyword`: 关键字（紫色）
- `.md-code-string`: 字符串（绿色）
- `.md-code-number`: 数字（橙色）
- `.md-code-comment`: 注释（灰色斜体）
- `.md-inline-code`: 行内代码
- `.md-list`: 列表容器
- `.md-list-item`: 列表项
- `.md-list-marker`: 列表标记符
- `.md-blockquote`: 引用块
- `.md-blockquote-bar`: 引用块左侧竖条
- `.md-blockquote-content`: 引用块内容
- `.md-link`: 链接
- `.md-thematic-break`: 分隔线
- `.md-table`: 表格容器
- `.md-table-header-row`: 表头行
- `.md-table-header-cell`: 表头单元格
- `.md-table-row`: 数据行
- `.md-table-cell`: 数据单元格

**依赖：**
- commonmark: Markdown 解析（AST 生成）
- commonmark-ext-gfm-tables: GFM 表格扩展支持

### SyntaxHighlighter
语法高亮工具类，基于正则表达式实现代码语法高亮。

**功能：**
- 根据文件名自动识别语言类型
- 计算文本的语法高亮样式范围（StyleSpans）
- 支持 Java/Python/JS/TS/Go/Rust/SQL/Shell/Bash/YAML/JSON/Markdown/XML/HTML 等语言

**核心方法：**
- `computeHighlighting(String fileName, String text)`: 计算语法高亮，返回 StyleSpans<Collection<String>>

**高亮类型：**
- `syntax-text`: 默认文本（浅灰 #abb2bf，所有未被其他规则匹配的文本都带此类）
- `syntax-keyword`: 关键字（紫色 #c678dd，加粗）
- `syntax-string`: 字符串（绿色 #98c379）
- `syntax-number`: 数字（橙色 #d19a66）
- `syntax-comment`: 注释（灰色 #5c6370，斜体）
- `syntax-annotation`: 注解（黄色 #e5c07b）
- `syntax-bracket`: 括号（浅灰 #abb2bf）
- `syntax-operator`: 运算符（青色 #56b6c2）
- `syntax-md-header`: Markdown标题（蓝色 #61afef，加粗）
- `syntax-md-bold`: Markdown粗体（黄色 #e5c07b，加粗）
- `syntax-md-italic`: Markdown斜体（紫色 #c678dd，斜体）
- `syntax-md-link`: Markdown链接（蓝色 #61afef，下划线）
- `syntax-md-codeblock`: Markdown代码块（绿色 #98c379）
- `syntax-md-list`: Markdown列表（橙色 #d19a66）
- `syntax-md-blockquote`: Markdown引用（灰色 #5c6370，斜体）

**实现原理：**
- 使用正则表达式匹配不同语法元素
- 每种语言有独立的正则模式（关键字、字符串、注释等）
- Python/JS/Go 等语言支持三引号字符串（`"""..."""` 和 `'''...'''`），三引号正则优先于单引号/双引号匹配
- `#` 注释（HASHCOMMENT）仅在 Python/Shell/YAML 类型中应用注释样式
- Markdown 使用独立的 `computeMarkdownHighlighting` 方法处理，避免访问不存在的正则组
- 所有未被语法规则匹配的文本都带有 `syntax-text` 默认样式类，确保在深色背景上文本可见（#abb2bf）
- 返回 StyleSpans 对象，可直接应用到 RichTextFX StyleClassedTextArea
- 不支持的语言返回空样式

**依赖：**
- RichTextFX: org.fxmisc.richtext

## 设计模式
- 工具类模式：静态方法，无需实例化
- 单例模式：ApplicationContext 全局唯一

## 注意事项
1. BrowserManager 使用静态初始化块，应用启动时自动初始化
2. ExecutorManager 线程池需要在应用关闭时手动关闭
3. SpringContextUtil 只能在 Spring 容器初始化后使用
4. 浏览器操作是阻塞的，注意线程管理