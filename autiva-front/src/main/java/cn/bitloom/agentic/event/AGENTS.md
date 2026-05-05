# Event 包

## 概述
本包实现了基于 Reactor 的发布-订阅事件系统，用于智能体之间的异步通信。采用 Inbox/Outbox/CancelBox 三通道架构，支持会话级别的消息隔离和流式生成中断。

## 核心类

### Event
事件实体类（不可变，使用 @Value），封装事件的所有信息。

**字段：**
- `timestamp`: 事件时间戳（@Builder.Default，自动设置为当前时间）
- `sessionId`: 会话ID
- `message`: Spring AI Message 对象

**构建方式：**
```java
Event event = Event.builder()
    .sessionId("session-uuid")
    .message(new UserMessage("用户消息"))
    .build();
```

### EventBus
事件总线（纯工具类，非 Spring Bean），提供静态方法实现 Inbox/Outbox 双通道消息传递。

**核心方法（静态方法）：**
- `inBoxPublish(sessionId, message)`: 发布事件到 Inbox（智能体接收消息），使用 tryEmitNext 并记录失败日志
- `inBoxSubscribe()`: 订阅 Inbox 事件流
- `outBoxPublish(sessionId, message)`: 发布消息到 Outbox（返回给用户），使用 tryEmitNext 并记录失败日志
- `outBoxSubscribe()`: 订阅 Outbox 消息流
- `cancelPublish(sessionId)`: 发布取消信号（设置 ConcurrentHashMap 标记），用于中止当前流式生成
- `isCancelled(sessionId)`: 检查指定会话是否已被取消
- `clearCancelFlag(sessionId)`: 清除指定会话的取消标记（流式完成/中断后清理）

**实现原理：**
- 使用 Reactor 的 Sinks.Many 实现多播（Inbox/Outbox）
- `multicast().onBackpressureBuffer()`: 多播模式，带背压缓冲
- 取消信号使用 ConcurrentHashMap 存储，配合 Flux.takeWhile() 实现流式中断

## 使用示例

### 发布事件到 Inbox
```java
EventBus.inBoxPublish(sessionId, new UserMessage("用户请求内容"));
```

### 订阅 Inbox 事件
```java
EventBus.inBoxSubscribe()
    .filter(event -> event.getSessionId().equals("my-session"))
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

## 设计模式
- 发布-订阅模式：解耦发送者和接收者
- 响应式编程：基于 Project Reactor
- 双通道架构：Inbox 处理输入，Outbox 处理输出

## 注意事项
1. EventBus 是纯工具类（非 Spring Bean），使用静态方法，无需依赖注入
2. 使用 filter 进行精确的会话路由
3. 消息发布使用 tryEmitNext，失败时记录 warn 日志而非静默丢弃
4. message 使用 Spring AI 的 Message 类型
5. 接收方需要根据 Message 的具体类型进行处理（如 `instanceof AssistantMessage`）
6. Event 使用 @Value（不可变），不应在构建后修改状态
