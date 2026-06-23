# Util 包

## 概述
工具类包，提供智能体相关的通用工具。来自 spring-ai-agent-utils 项目。

## 核心类

### MarkdownParser
带 YAML 前置元数据的 Markdown 解析器。

**功能：**
- 解析 `---` 分隔的 YAML 前置元数据
- 提取 Markdown 正文内容
- 返回 frontMatter (Map<String, Object>) 和 content (String)

### AgentEnvironment
智能体环境信息工具。

**功能：**
- 获取工作目录
- 获取 Git 状态
- 获取平台信息（跨平台支持 Windows）

### CommandLineQuestionHandler
命令行用户问题处理器，用于 AskUserQuestionTool 的命令行交互模式。

### GuiQuestionHandler
GUI 用户问题处理器，用于 AskUserQuestionTool 的图形界面交互模式。

**功能：**
- 通过 ToolUIBridge 在 WebView 中显示问题
- 阻塞等待用户在 UI 中选择答案
- 支持超时机制（默认10分钟）
- 使用 CompletableFuture 实现异步等待

**依赖：**
- ToolUIBridge: Java-JavaScript 通信桥接

### GuiTodoEventHandler
GUI 待办事项事件处理器，用于 TodoWriteTool 的图形界面展示。

**功能：**
- 通过 ToolUIBridge 在 WebView 中显示待办事项列表
- 非阻塞，即发即忘模式
- 自动序列化 Todos 对象为 JSON 传递给 JavaScript

### TokenEstimator
Token 估算工具类，用于估算文本的 Token 数量，避免超出上下文窗口限制。

**功能：**
- 估算文本的 Token 数量（使用简单的字符计数方法，1 Token ≈ 3 字符）
- 估算单行的 Token 数量（考虑行号前缀）
- 检查文件大小是否在安全范围内
- 计算基于 Token 预算的最大可读取字符数

**使用场景：**
- ReadTool 大文件保护机制：边读边计算 Token，达到预算立即停止
- 防止超大文件导致内存溢出和 UI 卡死
