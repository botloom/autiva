# Advisor 包

## 概述
本包实现了 Spring AI 的 Advisor 模式，用于在 LLM 请求/响应过程中添加横切关注点。

## 核心类

### LoggingAdvisor
实现 StreamAdvisor 接口，提供 LLM 请求和响应的日志记录功能。

**功能：**
- 记录所有发送给 LLM 的消息（System、User、Assistant、Tool Response）
- 流式完成后统一打印请求和响应，输入输出始终配对出现
- 记录工具调用信息
- 记录错误信息

**日志格式：**
流式响应完成后一次性输出，请求和响应通过 `├──` 分隔线分为上下两区。
每行通过 `String.format("│ %-8s│ %s", label, content)` 格式化后逐行 `log.info()` 输出：
```
┌─ LLM [#1] 14:30:28.456 · 3334ms ──────────────────────────
│ Seq     │ #1
│ Time    │ 14:30:25.123
│ System  │ You are a helpful assistant...
│ User    │ What's the weather today?
│ History │ User:2 Asst:1 Tool:1
├──────────────────────────────────────────────
│ Text    │ The weather today is sunny...
└──────────────────────────────────────────────
```
- 标签列固定 8 字符宽度，左对齐，内容列紧跟 `│` 分隔符
- 工具调用以 `#N` 编号逐行展示，参数截断至 300 字符
- 截断提示格式：`...(+N chars)`
- 所有文本内容在输出前会规范化：换行符替换为空格，多个连续空格压缩为单个空格，确保每条日志都是单行
- 请求信息在流开始时构建为 `List<String>`，响应信息在流完成后追加，统一逐行输出
- 注意：格式化必须用 `String.format()`，不能将 `%-8s` 直接传给 SLF4J（SLF4J 只认 `{}`）
- 日志输出统一使用方法引用：`lines.forEach(log::info)` 和 `lines.forEach(log::error)`，确保逐行输出

**配置：**
- Order: 1（执行顺序）
- 自动注册为 Spring Bean

### AutoMemoryToolsAdvisor
实现 BaseChatMemoryAdvisor 接口，自动将记忆工具注入到 ChatClient 请求中（来自 spring-ai-agent-utils）。

**功能：**
- 在 `before()` 阶段注入记忆系统提示和记忆工具回调
- 支持记忆整合触发器（`memoryConsolidationTrigger`）
- Builder 模式配置：memoriesRootDirectory、memorySystemPrompt、order

**配置：**
- 默认 Order: BaseAdvisor.HIGHEST_PRECEDENCE + 200（在 ToolCallAdvisor 之前）
- 默认系统提示模板：`classpath:/prompt/AUTO_MEMORY_TOOLS_SYSTEM_PROMPT.md`

**Builder 参数：**
- `order(int)`: Advisor 执行顺序
- `memoriesRootDirectory(String)`: 记忆文件根目录（必填）
- `memorySystemPrompt(Resource)`: 记忆系统提示模板
- `memoryConsolidationTrigger(BiPredicate)`: 记忆整合触发器

## 使用方式

### LoggingAdvisor
自动注入到 ChatClient 配置中：
```java
ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(a -> a.advisors(loggingAdvisor))
    .build();
```

### AutoMemoryToolsAdvisor
通过 Builder 创建：
```java
AutoMemoryToolsAdvisor advisor = AutoMemoryToolsAdvisor.builder()
    .memoriesRootDirectory("/path/to/memories")
    .build();
```

## 扩展指南
可以创建其他 Advisor 实现：
1. 实现 StreamAdvisor 或 BaseChatMemoryAdvisor 接口
2. 在对应方法中添加自定义逻辑
3. 注册为 Spring Bean
4. 在 Agent 中注入并添加到 ChatClient

## 注意事项
1. Advisor 的 Order 决定执行顺序
2. 流式处理需要注意线程安全
3. 日志记录可能包含敏感信息，生产环境需谨慎
4. AutoMemoryToolsAdvisor 的 order 必须小于 ToolCallAdvisor（300）
