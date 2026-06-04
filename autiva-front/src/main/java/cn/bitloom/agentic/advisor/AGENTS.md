# Advisor 包

## 概述
本包实现了 Spring AI 的 Advisor 模式，用于在 LLM 请求/响应过程中添加横切关注点。

## 核心类

### LoggingAdvisor
实现 StreamAdvisor 和 CallAdvisor 接口，同时支持流式调用（`.stream()`）和同步调用（`.call()`），提供 LLM 请求和响应的日志记录功能。

**功能：**
- 记录所有发送给 LLM 的消息（System、User、Assistant、Tool Response）
- 流式完成后统一打印请求和响应，输入输出始终配对出现
- 记录工具调用信息
- 记录错误信息

**日志格式：**
流式和同步调用完成后均一次性输出，请求和响应通过 `├──` 分隔线分为上下两区。同步模式下直接获取完整响应后打印，流式模式下在 `doOnComplete` 中统一打印。
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
- 使用 Lombok `@Builder` 手动实例化，`requestSeq` 为实例字段

### AutoMemoryToolsAdvisor（已废弃）
此 Advisor 已被移除。记忆工具现在通过 `defaultTools()` 直接注册，核心记忆通过 `AgentManager.buildSystemPrompt()` 静态注入。动态上下文注入由 ProactiveContextAdvisor 负责。

### ProactiveContextAdvisor
实现 StreamAdvisor 和 CallAdvisor 接口，在每次 LLM 请求前自动注入动态上下文，让智能体"不需要主动查也能看到"关键信息。

**功能：**
- 自动注入近期日志摘要（情景记忆，最近2天）
- 基于用户消息自动搜索相关记忆（自动召回）
- 检测进化信号并注入提示（引导智能体使用进化工具）

**依赖组件：**
- `JournalManager`: 每日日志管理
- `MemorySearchService`: 记忆搜索服务
- `EvolutionHintProvider`: 进化信号检测与提示

**配置：**
- Order: 200（在 ToolCallAdvisor 之前执行）
- 在 ChatClientConfig 中注册
- 使用 `ChatClientRequest.builder()` 直接构建请求，避免 `request.mutate()` 触发 `prompt.copy()` → `instructionsCopy()` 对消息类型做 instanceof 检查

### EvolutionHintProvider
进化信号检测与提示生成器，分析用户消息检测进化信号，返回提示信息引导智能体使用进化工具。

**功能：**
- 使用 SignalExtractor 从用户消息中提取信号
- 节流策略：同一类型信号在30分钟内只提醒一次
- 返回 `<system-reminder>` 格式的提示信息

**Spring 注解：** `@Component`

## 使用方式

### LoggingAdvisor
手动 Builder 创建，添加到 ChatClient 配置中：
```java
ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(a -> a.advisors(LoggingAdvisor.builder().build()))
    .build();
```

### AutoMemoryToolsAdvisor（已废弃）
此 Advisor 已被移除，无需使用。

### ProactiveContextAdvisor
通过构造函数创建，在 ChatClientConfig 中注册：
```java
new ProactiveContextAdvisor(journalManager, memorySearchService, evolutionHintProvider)
```

### EvolutionHintProvider
Spring Bean 自动注入。

## 扩展指南
可以创建其他 Advisor 实现：
1. 实现 StreamAdvisor（流式）、CallAdvisor（同步）或两者同时实现
2. 在对应方法中添加自定义逻辑
3. 注册为 Spring Bean
4. 在 Agent 中注入并添加到 ChatClient

## 注意事项
1. Advisor 的 Order 决定执行顺序
2. 流式处理需要注意线程安全
3. 日志记录可能包含敏感信息，生产环境需谨慎
4. AutoMemoryToolsAdvisor 已废弃，由 ProactiveContextAdvisor 替代
5. 如果同时需要支持 `.call()` 和 `.stream()`，Advisor 必须同时实现 CallAdvisor 和 StreamAdvisor 接口。只实现 StreamAdvisor 的 Advisor 在同步调用中不会被触发
6. ProactiveContextAdvisor 的 Order 为 200，必须在 ToolCallAdvisor (300) 之前执行
