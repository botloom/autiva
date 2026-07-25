# Event 包

## 概述
本包实现了事件驱动架构，统一使用 `EventBus.inBox` 传递事件，由 `SessionRunner.start()` 按事件类型分类处理。对话消息事件（MessageEvent）串行处理，记忆事件（MemoryEvent）异步处理不阻塞对话流。对齐 netInsight 设计，使用 `@JsonTypeInfo` + `@JsonSubTypes` 支持 Jackson 多态序列化/反序列化，通过 `eventType` 字段作为类型标识。

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

### EventTypeEnum
事件类型枚举，用于 Jackson 多态序列化的类型标识。

**枚举值：**
- `MESSAGE`: 消息事件（MessageEvent）
- `MEMORY`: 记忆事件（MemoryEvent）
- `A2UI`: A2UI 事件（A2UIEvent）
- `A2UI_ACTION`: A2UI 用户动作事件（A2UIActionEvent）
- `DIFF`: Diff 事件（DiffEvent）
- `UI_CARD`: UI 卡片事件（UICardEvent）

### AbstractEvent
所有事件的抽象基类（sealed class），使用 `@SuperBuilder` 支持 Lombok Builder 模式。使用 `@JsonTypeInfo` + `@JsonSubTypes` 支持 Jackson 多态序列化/反序列化，通过已有的 `eventType` 字段作为类型标识（EXISTING_PROPERTY），避免新增 `@type` 字段污染序列化结构。

**permits 列表：** MessageEvent, MemoryEvent, A2UIEvent, A2UIActionEvent, DiffEvent, UICardEvent

**核心字段：**
- `sessionId`: 会话ID（用于 SessionRunner 过滤本会话事件）
- `messageId`: 消息ID（用于事件路由标识，对齐 netInsight 的 messageId 字段）
- `persist`: 是否持久化到 events.jsonl（autiva 特有，UICardEvent 等需要细粒度持久化控制）

**抽象方法：**
- `getEventType()`: 返回事件类型（EventTypeEnum），由子类通过 final 字段固定返回，确保编译期强制指定。同时作为 Jackson 多态反序列化的类型标识

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

### A2UIEvent
A2UI 事件（从 Agent → UI），携带 A2UI v0.9.1 消息体。

**字段：**
- `message`: A2UIMessage（CreateSurface/UpdateComponents/UpdateDataModel/DeleteSurface）

**静态工厂方法：**
- `of(sessionId, message)`: 创建 A2UI 事件

**流转路径：** Agent → A2UITool → ToolUIBridge → EventBus.outBox → A2UICard

### A2UIActionEvent
A2UI 用户动作事件（从 UI → Agent），用户通过 A2UI 界面触发的交互。

**字段：**
- `surfaceId`: Surface ID
- `sourceComponentId`: 触发动作的组件 ID
- `actionName`: 动作名称（Agent 识别）
- `context`: 上下文数据（已解析的值）

**静态工厂方法：**
- `of(sessionId, surfaceId, sourceComponentId, actionName, context)`: 创建动作事件

**流转路径：** A2UIRenderer → A2UISurface → ToolUIBridge → EventBus.inBox → SessionRunner → Agent

### DiffEvent
Diff 事件（从 Agent → UI），当 DiffService 生成新的 Diff 时发布。

**字段：**
- `diff`: FileDiff（文件 Diff 数据，包含 filePath/hunks/isCreate/isDelete）

**静态工厂方法：**
- `of(sessionId, diff)`: 创建 Diff 事件（sessionId 可为 null，因为 diff 由工具调用产生，不绑定特定 session）

**流转路径：** DiffService.generateDiff() → EventBus.publishOut() → HomePageController.subscribeDiffEvents() → 直接使用事件中的 FileDiff 数据追加卡片到 diff 文件卡片条

### UICardEvent
UI 卡片事件（TaskCard / QuestionCard），支持事件化驱动和历史回放。

**Type 枚举：**
- `TASK_CARD`: 任务卡片
- `QUESTION_CARD`: 提问卡片

**Status 枚举：**
- `CREATED`: 已创建（persist=false，实时 UI 用）
- `COMPLETED`: 已完成（persist=true，持久化用于历史回放）
- `FAILED`: 已失败（persist=true）
- `ANSWERED`: 已回答（persist=true）

**字段：**
- `type`: 卡片类型（Type 枚举）
- `cardId`: 卡片ID
- `cardJson`: 卡片 JSON 数据
- `status`: 卡片状态（Status 枚举）
- `result`: 完成结果 / 回答内容

**静态工厂方法：**
- `taskCreated(sessionId, taskId, taskJson)`: 创建任务卡片（persist=false）
- `taskCompleted(sessionId, taskId, taskJson, result)`: 完成任务卡片（persist=true）
- `taskFailed(sessionId, taskId, taskJson, error)`: 任务失败（persist=true）
- `questionAsked(sessionId, questionId, questionsJson)`: 提问（persist=false）
- `questionAnswered(sessionId, questionId, questionsJson, answersJson)`: 回答（persist=true）

**持久化策略：** CREATED/ASKED 事件 persist=false（实时 UI 用），COMPLETED/FAILED/ANSWERED 事件 persist=true（持久化用于历史回放）

### EventConverter
事件转换器，是唯一同时导入 Spring AI 和事件类型的类。负责在 Spring AI Message 和 MessageEvent 之间进行双向转换。

**核心方法：**
- `fromMessage(sessionId, Message)`: Spring AI Message → MessageEvent（保留模型生成的工具调用 id）
- `toMessage(MessageEvent)`: MessageEvent → Spring AI Message（按 type 分发，使用 builder 模式）
- `toUserMessage(MessageEvent)`: MessageEvent → UserMessage（Agent 输入用）
- `fromMessages(sessionId, List<Message>)`: 批量 Message → MessageEvent
- `toMessages(List<MessageEvent>)`: 批量 MessageEvent → Message（供 LLM 上下文加载）

## 事件处理流程

### SessionRunner 事件分类处理
```
EventBus.inBoxFlux()
    │
    ├── filter(event -> event.sessionId == this.id)
    │
    └── concatMap(event -> switch(event) {
        case MessageEvent USER → 设置 currentMessageId，串行调用 Agent，本轮结束 safeFlush()
        case A2UIActionEvent → 构造用户消息让 Agent 继续处理，本轮结束 safeFlush()
        case MemoryEvent → 异步处理（Schedulers.boundedElastic()），不阻塞对话流
    })
```

### MemoryEvent 处理流程
```
MemoryEvent
    │
    └── CONTEXT_COMPACT（由 UsageAdvisor 发布）
        └── SessionRunner.handleContextCompact()（异步）
            ├── chatMemory.getHistoryFromFile() 只取文件历史消息
            ├── memoryManager.compact() 生成摘要
            ├── 更新 Session.summary
            ├── 推进 memoryCursor = chatMemory.countFileMessages()
            ├── 重置 currentContextLength = 0
            └── sessionManager.persistSession(session)
```

## 序列化机制

### Jackson 多态序列化
使用 `@JsonTypeInfo` + `@JsonSubTypes` 实现事件的多态序列化/反序列化：

- **序列化**：`@JsonTypeInfo(use = Id.NAME, include = EXISTING_PROPERTY, property = "eventType")` 自动将 `eventType` 字段写入 JSON
- **反序列化**：根据 `eventType` 字段值自动还原正确子类类型（如 `"MESSAGE"` → MessageEvent）
- **子类注册**：`@JsonSubTypes` 注解注册所有子类与 eventType 值的映射

**events.jsonl 每行格式示例：**
```json
{"eventType":"MESSAGE","sessionId":"...","messageId":"...","persist":true,"type":"USER","text":"..."}
```

## 设计模式
- 观察者模式：通过 EventBus（Sinks.Many multicast）实现事件发布订阅
- 策略模式：按事件类型分类处理（MessageEvent 串行，MemoryEvent 异步）
- 桥接模式：EventConverter 桥接 Spring AI Message 和事件类型
- 多态序列化：通过 @JsonTypeInfo + @JsonSubTypes 实现事件的多态序列化（对齐 netInsight）

## 注意事项
1. 统一使用 inBox，不新增 memoryBox 等专用通道
2. MemoryEvent 通过 `Schedulers.boundedElastic()` 异步处理，不阻塞对话流
3. SessionRunner 通过 `event.sessionId.equals(this.id)` 过滤本会话事件
4. MessageEvent 不持有 Spring AI Message，使用结构化字段传递信息
5. EventConverter 是唯一同时导入 Spring AI 和事件类型的类
6. 所有事件子类必须实现 `getEventType()` 返回固定 EventTypeEnum 值
7. 事件持久化到 events.jsonl 时通过 `eventType` 字段支持多态反序列化（不再使用 `@type` 字段）
8. UICardEvent 通过 `persist` 字段细粒度控制持久化（CREATED 不持久化，COMPLETED/FAILED/ANSWERED 持久化）
