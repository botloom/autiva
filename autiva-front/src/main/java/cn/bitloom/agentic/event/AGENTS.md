# Event 包

## 概述
本包实现了基于 Reactor 的统一事件系统。EventBus 提供双通道架构（inBox/outBox），分别用于智能体消息流入和消息流出。inBox 通道通过 MessageChannel 枚举实现逻辑路由，将不同来源的消息（用户、系统、记忆、日记、进化）分发到对应的处理流程。outBox 通道仅转发 USER 通道的响应消息给用户。所有事件统一通过 EventBus 发布和订阅，不再使用 Spring ApplicationEvent 机制。

## 核心类

### EventType
事件类型枚举，定义通过 inBox 通道传递的业务事件类型。

**枚举值：**
- `MESSAGE`: 用户消息事件（默认，现有流程）
- `MEMORY_CONSOLIDATE`: 记忆整理事件（触发记忆总结、归档、写入文件）
- `JOURNAL`: 日记事件（触发日记记录）
- `EVOLVE`: 进化事件（触发进化周期执行）

**扩展方式：** 新增事件类型只需添加枚举值，无需新建类

### MessageChannel
消息通道枚举（位于 `cn.bitloom.agentic.session` 包），定义 inBox 通道内的逻辑路由维度。每个 MessageEvent 携带一个 MessageChannel，用于区分消息来源和决定是否转发到 outBox。

**枚举值：**
- `USER`: 用户消息通道（来自用户输入，响应转发到 outBox）
- `SYSTEM`: 系统消息通道（来自心跳、定时任务等系统触发，响应不转发到 outBox）
- `MEMORY`: 记忆通道（记忆整理事件，响应不转发到 outBox）
- `JOURNAL`: 日记通道（日记事件，响应不转发到 outBox）
- `EVOLVE`: 进化通道（进化事件，响应不转发到 outBox）

**核心方法：**
- `fromEventType(EventType)`: 根据 EventType 自动推导 MessageChannel（映射关系见下表）
- `shouldPublishToOutBox()`: 判断该通道的消息是否应转发到 outBox（仅 USER 返回 true）

**EventType → MessageChannel 映射表：**

| EventType | MessageChannel | 说明 |
|-----------|---------------|------|
| MESSAGE | USER | 用户消息走 USER 通道 |
| MEMORY_CONSOLIDATE | MEMORY | 记忆整理走 MEMORY 通道 |
| JOURNAL | JOURNAL | 日记事件走 JOURNAL 通道 |
| EVOLVE | EVOLVE | 进化事件走 EVOLVE 通道 |
| null | USER | 默认回退到 USER 通道 |

### AbstractEvent
事件基类（使用 @SuperBuilder），提供公共字段。

**字段：**
- `sessionId`: String - 关联的会话ID
- `timestamp`: LocalDateTime - 事件时间戳（@Builder.Default，自动设置为当前时间）

### MessageEvent
消息事件类（继承 AbstractEvent，使用 @SuperBuilder），用于 inBox/outBox 通道。

**字段：**
- `timestamp`: 事件时间戳（继承自 AbstractEvent，@Builder.Default，自动设置为当前时间）
- `sessionId`: 会话ID（继承自 AbstractEvent）
- `message`: Spring AI Message 对象
- `eventType`: EventType 事件类型标记（@Builder.Default，默认为 MESSAGE）
- `messageChannel`: MessageChannel 消息通道（@Builder.Default，默认为 USER）

**构建方式：**
```java
MessageEvent event = MessageEvent.builder()
    .sessionId("session-uuid")
    .message(new UserMessage("用户消息"))
    .build();

MessageEvent eventWithChannel = MessageEvent.builder()
    .sessionId("session-uuid")
    .message(new UserMessage("系统消息"))
    .eventType(EventType.MESSAGE)
    .messageChannel(MessageChannel.SYSTEM)
    .build();
```

### SystemEvent
系统事件类（继承 AbstractEvent，使用 @SuperBuilder），预留用于系统级事件。

### EvolveEvent
进化事件类（继承 AbstractEvent，使用 @SuperBuilder），预留用于进化相关事件。

### EventBus
事件总线（纯工具类，非 Spring Bean），提供静态方法实现双通道消息传递。

**双通道架构 + MessageChannel 路由：**

| 通道 | 用途 | 事件类型 | MessageChannel 路由 |
|------|------|----------|-------------------|
| inBox | 消息流入（→ 智能体） | MessageEvent | USER / SYSTEM / MEMORY / JOURNAL / EVOLVE |
| outBox | 消息流出（→ 用户） | MessageEvent | 仅 USER 通道消息转发 |

**核心方法（静态方法）：**
- `inBoxPublish(sessionId, message)`: 发布事件到 Inbox（智能体接收消息），eventType 默认 MESSAGE，messageChannel 默认 USER
- `inBoxPublish(sessionId, message, eventType)`: 发布带事件类型的消息到 Inbox，messageChannel 通过 `MessageChannel.fromEventType(eventType)` 自动推导
- `inBoxPublish(sessionId, message, eventType, messageChannel)`: 发布带事件类型和显式通道的消息到 Inbox，用于需要覆盖默认映射的场景（如心跳使用 SYSTEM 通道）
- `inBoxSubscribe()`: 订阅 Inbox 事件流
- `outBoxPublish(sessionId, message)`: 发布消息到 Outbox（返回给用户），使用 tryEmitNext
- `outBoxSubscribe()`: 订阅 Outbox 消息流
- `stop(sessionId)`: 设置停止标记，用于中止当前流式生成
- `isStop(sessionId)`: 检查指定会话是否已被停止
- `clearStopFlag(sessionId)`: 清除指定会话的停止标记（流式完成/中断后清理）
- `markBusy(sessionId)`: 标记会话为忙碌状态
- `clearBusy(sessionId)`: 清除会话忙碌状态
- `isBusy(sessionId)`: 检查会话是否忙碌

**outBox 发布规则：**
- 仅 `MessageChannel.USER` 通道的消息会转发到 outBox
- 判断方式：`channel.shouldPublishToOutBox()`（仅 USER 返回 true）
- SYSTEM / MEMORY / JOURNAL / EVOLVE 通道的响应不会推送给用户
- 此规则在 AbstractAgent 和 SessionManager 中统一执行

**实现原理：**
- 使用 Reactor 的 Sinks.Many 实现多播（inBox/outBox）
- `multicast().onBackpressureBuffer()`: 多播模式，带背压缓冲
- 停止信号和忙碌状态使用 ConcurrentHashMap 存储

## 事件流全景

```
发布方                              通道                    监听方
──────────────────────────────────────────────────────────────────────────
HeartbeatRunner         ──inBox(MESSAGE/SYSTEM)──→   AbstractAgent (处理消息)
CronManager             ──inBox(MESSAGE/SYSTEM)──→   AbstractAgent (处理消息)
HomePageViewModel       ──inBox(MESSAGE/USER)───→    AbstractAgent (处理消息)
AgentLifecycleHook      ──inBox(JOURNAL/JOURNAL)──→  MainAgent (日记)
AgentLifecycleHook      ──inBox(MEMORY_CONSOLIDATE/MEMORY)──→ MainAgent (记忆整理)
HeartbeatRunner         ──inBox(EVOLVE/EVOLVE)──→    EvolverAgent (进化)
AbstractAgent           ──outBox(仅USER通道)──→      HomePageViewModel (展示响应)

SettingsPageViewModel   ──sysBox──→  AbstractAgent (重建ChatClient)
SkillPageViewModel      ──sysBox──→  AbstractAgent (重建ChatClient)
SessionManager          ──sysBox──→  SessionEventListener (管理系统会话+心跳)
```

**注：** inBox 通道格式为 `inBox(EventType/MessageChannel)`，sysBox 通道为系统配置变更事件，独立于 MessageChannel 路由。

## 使用示例

### 发布事件到 Inbox（默认 USER 通道）
```java
EventBus.inBoxPublish(sessionId, new UserMessage("用户请求内容"));
```

### 发布带事件类型的消息到 Inbox（自动推导 MessageChannel）
```java
EventBus.inBoxPublish(sessionId, new UserMessage("整理记忆"), EventType.MEMORY_CONSOLIDATE);
```

### 发布带显式 MessageChannel 的消息到 Inbox
```java
EventBus.inBoxPublish(sessionId, userMessage, EventType.MESSAGE, MessageChannel.SYSTEM);
EventBus.inBoxPublish(sessionId, journalMessage, EventType.JOURNAL, MessageChannel.JOURNAL);
```

### 订阅 Inbox 事件
```java
EventBus.inBoxSubscribe()
    .filter(event -> event.getSessionId().startsWith("MAIN-"))
    .concatMap(event -> processEvent(event))
    .subscribe();
```

### 发布消息到 Outbox
```java
EventBus.outBoxPublish(sessionId, responseMessage);
```

### 订阅 Outbox 消息
```java
EventBus.outBoxSubscribe()
    .filter(event -> event.getSessionId().equals(currentSessionId))
    .subscribe(event -> sendToUser(event.getMessage()));
```

### 根据 MessageChannel 过滤事件
```java
EventBus.inBoxSubscribe()
    .filter(event -> event.getMessageChannel() == MessageChannel.USER)
    .subscribe(event -> handleUserMessage(event));
```

## 设计模式
- 发布-订阅模式：解耦发送者和接收者
- 响应式编程：基于 Project Reactor
- 双通道架构：inBox 处理输入，outBox 处理输出
- MessageChannel 路由模式：通过 messageChannel 字段在 inBox 通道内实现逻辑路由，替代物理多通道
- 事件类型标记模式：通过 eventType 字段区分不同业务事件类型
- 通道推导模式：`MessageChannel.fromEventType()` 自动从 EventType 推导 MessageChannel，减少调用方负担
- outBox 门控模式：`shouldPublishToOutBox()` 统一控制哪些通道的响应转发给用户

## 注意事项
1. EventBus 是纯工具类（非 Spring Bean），使用静态方法，无需依赖注入
2. 使用 filter 进行精确的事件路由
3. 消息发布使用 tryEmitNext，失败时记录 warn 日志而非静默丢弃
4. message 使用 Spring AI 的 Message 类型
5. 接收方需要根据 Message 的具体类型进行处理（如 `instanceof AssistantMessage`）
6. MessageEvent 使用 @SuperBuilder（继承 AbstractEvent），不应在构建后修改状态
7. 非 MESSAGE 类型事件由 AbstractAgent.run() 中的 handleAgentEvent() 分发处理
8. EventType 默认值为 MESSAGE，MessageChannel 默认值为 USER，现有 inBoxPublish 调用完全向后兼容
9. `inBoxPublish(sessionId, message, eventType)` 会自动通过 `MessageChannel.fromEventType()` 推导通道，无需手动指定
10. 需要覆盖默认映射时（如心跳使用 SYSTEM 通道而非 USER），使用四参数重载 `inBoxPublish(sessionId, message, eventType, messageChannel)`
11. outBox 仅转发 USER 通道消息，其他通道（SYSTEM/MEMORY/JOURNAL/EVOLVE）的响应不会推送给用户
12. MessageChannel 位于 `cn.bitloom.agentic.session` 包，非 event 包，使用时需注意导入
