# Advisor 包

## 概述
本包实现了 Spring AI 的 Advisor 模式，用于在 LLM 请求/响应过程中添加横切关注点。

## 核心类

### LoggingAdvisor
实现 StreamAdvisor 接口，提供 LLM 请求和响应的日志记录功能。

**功能：**
- 记录所有发送给 LLM 的消息（System、User、Assistant、Tool Response）
- 流式记录 LLM 响应内容
- 记录工具调用信息
- 记录错误信息

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
