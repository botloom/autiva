# Event 包

## 概述
本包实现了事件驱动架构，统一使用 `EventBus.inBox` 传递事件，由 `Session.start()` 按事件类型分类处理。对话消息事件（MessageEvent）串行处理，记忆事件（MemoryEvent）异步处理不阻塞对话流。

## 核心类

### EventBus
事件总线，基于 Reactor `Sinks.Many` 的 multicast 实现。

**核心字段：**
- `inBox`: `Sinks.Many<AbstractEvent>` 输入事件流（消息进入智能体）
- `outBox`: `Sinks.Many<AbstractEvent>` 输出事件流（智能体响应输出）

**核心方法：**
- `publishIn(AbstractEvent)`: 发布事件到 inBox
- `publishOut(AbstractEvent)`: 发布事件到 outBox
- `inBoxFlux()`: 订阅 inBox 事件流
- `outBoxFlux()`: 订阅 outBox 事件流

### IEvent
事件接口，定义事件的基本行为。

### AbstractEvent
所有事件的抽象基类，使用 `@SuperBuilder` 支持 Lombok Builder 模式。

**核心字段：**
- `sessionId`: 会话ID（用于 Session 过滤本会话事件）

### MessageEvent
对话消息事件，在 EventBus 中传递的核心事件类型。不再持有 `org.springframework.ai.chat.messages.Message`，而是使用结构化字段。

**Type 枚举：**
- `USER`: 用户消息
- `ASSISTANT`: 助手消息
- `TOOL`: 工具消息

**结构化字段：**
- `type`: 消息类型（Type 枚举）
- `text`: 消息文本内容
- `finishReason`: 完成原因（STOP / TOOL_CALLS）
- `toolCalls`: 工具调用信息列表
- `responses`: 工具响应信息列表
- `attachments`: 附件列表

**静态工厂方法：**
- `userMessage(sessionId, text)`: 创建用户消息事件
- `assistantStream(sessionId, chunk)`: 创建助手流式消息事件
- `assistantStop(sessionId, text)`: 创建助手完成消息事件
- `assistantToolCalls(sessionId, text, toolCalls)`: 创建助手工具调用消息事件
- `toolResponse(sessionId, responses)`: 创建工具响应消息事件

**判断方法：**
- `isUserMessage()`: 判断是否为用户消息

### MemoryEvent
记忆事件，统一发布到 EventBus.inBox，由 SessionRunner 分类处理。

**Type 枚举：**
- `CONTEXT_COMPACT`: 上下文需要压缩（由 UsageAdvisor 在 token 超阈值时发布）

**字段：**
- `type`: 事件类型（Type 枚举）
- `agentId`: 智能体ID
- `currentTokens`: 当前 token 使用量（CONTEXT_COMPACT 用）
- `maxTokens`: 模型上下文上限（CONTEXT_COMPACT 用）

**静态工厂方法：**
- `contextCompact(sessionId, agentId, currentTokens, maxTokens)`: 创建上下文压缩事件

### EventConverter
事件转换器，是唯一同时导入 Spring AI 和事件类型的类。负责在 Spring AI Message 和 MessageEvent 之间进行双向转换。

**核心方法：**
- `toUserMessage(MessageEvent)`: MessageEvent → UserMessage
- `fromMessage(sessionId, Message)`: Message → MessageEvent

## 事件处理流程

### Session 事件分类处理
```
EventBus.inBoxFlux()
    │
    ├── filter(event -> event.sessionId == this.id)
    │
    └── concatMap(event -> {
        if (event instanceof MessageEvent && isUserMessage) {
            // 串行调用 Agent 执行对话
            agent.runStream/runBlock(session, userMessage)
        } else if (event instanceof MemoryEvent) {
            // 异步处理记忆事件（不阻塞对话流）
            Mono.fromRunnable(() -> handleMemoryEvent(event))
                .subscribeOn(Schedulers.boundedElastic())
        }
    })
```

### MemoryEvent 处理流程
```
MemoryEvent
    │
    └── CONTEXT_COMPACT（由 UsageAdvisor 发布）
        └── SessionRunner.handleContextCompact()
            ├── memoryManager.compact() 生成摘要
            ├── 更新 Session.summary
            ├── 推进 memoryCursor
            └── 重置 currentContextLength
```

## 设计模式
- 观察者模式：通过 EventBus（Sinks.Many multicast）实现事件发布订阅
- 策略模式：按事件类型分类处理（MessageEvent 串行，MemoryEvent 异步）
- 桥接模式：EventConverter 桥接 Spring AI Message 和事件类型

## 注意事项
1. 统一使用 inBox，不新增 memoryBox 等专用通道
2. MemoryEvent 通过 `Schedulers.boundedElastic()` 异步处理，不阻塞对话流
3. Session 通过 `event.sessionId.equals(this.id)` 过滤本会话事件
4. MessageEvent 不持有 Spring AI Message，使用结构化字段传递信息
5. EventConverter 是唯一同时导入 Spring AI 和事件类型的类
