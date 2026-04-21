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