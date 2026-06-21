# Advisor 包

## 概述
本包实现了 Spring AI Advisor 机制，通过 `order` 控制执行顺序，在请求前后注入上下文、提取元数据、桥接 Hook。Advisor 是不侵入 Agent 类的横切逻辑载体。

## 核心类

### UsageAdvisor
Usage 提取 Advisor，从模型响应的 `Usage` 中提取 `prompt_tokens`，替代旧版 `MemoryCompactAdvisor`。

**Spring 注解：** `@Builder`（Lombok），无字段，无构造函数依赖

**order：** 50（在 ProactiveContextAdvisor 之前执行）

**核心逻辑：**
1. 从 `request.context().get("runtimeContext")` 获取 RuntimeContext，进而获取 Session
2. 从 `response.chatResponse().getMetadata().getUsage()` 提取 `promptTokens`
3. 直接操作 `Session.setCurrentContextLength(promptTokens)`（currentContextLength 是瞬态字段，不需要持久化）
4. 检查 `promptTokens >= maxTokens * compactionThreshold`，超阈值时发布 `MemoryEvent.contextCompact()` 到 inBox
5. 由 Session 异步处理压缩，不阻塞对话流

**模型 token 上限：**
- DEEPSEEK: 64000
- QWEN: 32000

**注册策略：** 通过 Agent.Builder 的 `.compact(true)` 注册，主智能体开启，子智能体 Fresh 模式不压缩。

### ProactiveContextAdvisor
主动上下文注入 Advisor，将可变上下文动态注入到系统提示词中。

**Spring 注解：** `@Builder`（Lombok）

**Builder 字段：**
- `skillDescriptions`: 技能描述文本（Agent 构建时计算，相对稳定）
- `subagentDescriptions`: 子智能体描述文本（Agent 构建时计算，相对稳定）
- `memoryFilePath`: memory.md 文件路径，请求时读取最新内容

**order：** 200

**依赖：** RuntimeContext（从 `request.context().get("runtimeContext")` 获取 Session）

**注入内容（XML 格式，追加到 SystemMessage 末尾）：**
1. `<memory>` — memory.md 热记忆内容（每次请求从文件读取最新版本）
2. `<skills>` — 技能描述文本
3. `<subagents>` — 子智能体描述文本
4. `<summary>` — Session.summary（压缩后的早期对话摘要，上下文桥接）

**注入方式：** 找到 SystemMessage 并追加上下文文本；若无 SystemMessage 则新建一个。

**注册策略：** 通过 Agent.Builder 的 `.compact(true)` 注册，主智能体开启，子智能体不压缩。

### HookAdvisor
Hook 桥接 Advisor，将 `AgentHook` 高级 API 桥接到 Spring AI Advisor 机制。

**order：** 0

**核心逻辑：**
- `beforeModelCall`: 按 hook.order() 顺序执行
- `afterModelCall`: 按 hook.order() 顺序执行
- `beforeToolCall` / `afterToolCall`: 委托所有 Hook

### LoggingAdvisor
日志 Advisor，记录 LLM 请求/响应的详细信息。

**order：** 1

**核心逻辑：**
- 记录请求信息（Seq、Time、System、User、History、Context）
- 记录响应信息（Text、Tools、Error）
- 流式模式通过 `doOnNext` 累积文本和工具调用，`doOnComplete` 输出日志

## Advisor 执行顺序
```
请求 → HookAdvisor(0) → LoggingAdvisor(1) → UsageAdvisor(50) → ProactiveContextAdvisor(200) → Model
                                                                              ↓
响应 ← HookAdvisor(0) ← LoggingAdvisor(1) ← UsageAdvisor(50) ← ProactiveContextAdvisor(200) ← Model
                                          ↓
                              提取 promptTokens，超阈值发布 MemoryEvent.CONTEXT_COMPACT
```

## 设计模式
- 责任链模式：通过 AdvisorChain 串联执行
- 模板方法：StreamAdvisor/CallAdvisor 接口定义 adviseStream/adviseCall
- 桥接模式：HookAdvisor 将 AgentHook 高级 API 桥接到 Advisor 机制

## 注意事项
1. Advisor 通过 Agent.Builder 的 build() 方法注册到 ChatClient
2. order 值越小越先执行（请求方向），响应方向相反
3. UsageAdvisor/ProactiveContextAdvisor 通过 `.compact(true)` 注册，只主智能体开启，子智能体不压缩
4. 旧版 MemoryCompactAdvisor、LongMemoryConsolidateAdvisor 已移除，功能由 UsageAdvisor + MemoryManager 替代
5. EvolutionHintProvider 已移除，不再自动注入进化提示
6. UsageAdvisor/ProactiveContextAdvisor 使用 @Builder，无 Spring 依赖，通过 RuntimeContext 获取 Session
