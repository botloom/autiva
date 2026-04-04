# Event 包

## 概述
本包实现了基于 Reactor 的发布-订阅事件系统，用于智能体之间的异步通信。采用 Inbox/Outbox 双通道架构，支持会话级别的消息隔离。

## 核心类

### Event
事件实体类，封装事件的所有信息。

**字段：**
- `eventId`: 事件唯一标识（UUID）
- `timestamp`: 事件时间戳
- `from`: 发送者名称
- `sessionId`: 会话ID
- `message`: Spring AI Message 对象
- `metadata`: 元数据（可选）

**构建方式：**
```java
Event event = Event.builder()
    .from("main")
    .sessionId("session-uuid")
    .message(new UserMessage("用户消息"))
    .build();
```

### EventBus
事件总线，Spring Bean，提供 Inbox/Outbox 双通道消息传递。

**依赖注入：**
- `ConfigManager`: 配置管理器
- `SessionManager`: 会话管理器

**核心方法：**
- `inBoxPublish(Event)`: 发布事件到 Inbox（智能体接收消息）
- `inBoxSubscribe()`: 订阅 Inbox 事件流
- `outBoxPublish(sessionId, message)`: 发布消息到 Outbox（返回给用户）
- `outBoxSubscribe()`: 订阅 Outbox 消息流

**会话隔离策略：**
- `PER_PEER`: 按用户隔离，同一用户的所有会话共享
- `PER_CHANNEL_PEER`: 按通道+用户隔离，每个会话独立

**实现原理：**
- 使用 Reactor 的 Sinks.Many 实现多播
- `directBestEffort()`: 直接尽力投递模式

## 使用示例

### 发布事件到 Inbox
```java
eventBus.inBoxPublish(Event.builder()
    .from("user")
    .sessionId("session-uuid")
    .message(new UserMessage("用户请求内容"))
    .build());
```

### 订阅 Inbox 事件
```java
eventBus.inBoxSubscribe()
    .filter(event -> event.getSessionId().equals("my-session"))
    .concatMap(event -> processEvent(event))
    .subscribe();
```

### 发布消息到 Outbox
```java
eventBus.outBoxPublish(sessionId, responseMessage);
```

### 订阅 Outbox 消息
```java
eventBus.outBoxSubscribe()
    .filter(event -> event.getSessionId().equals(currentSessionId))
    .subscribe(event -> sendToUser(event.getMessage()));
```

## 设计模式
- 发布-订阅模式：解耦发送者和接收者
- 响应式编程：基于 Project Reactor
- 双通道架构：Inbox 处理输入，Outbox 处理输出

## 注意事项
1. EventBus 是 Spring Bean，通过依赖注入使用
2. 使用 filter 进行精确的会话路由
3. 注意处理异常，避免中断事件流
4. message 使用 Spring AI 的 Message 类型
5. 接收方需要根据 Message 的具体类型进行处理（如 `instanceof AssistantMessage`）
