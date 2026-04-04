# Session 包

## 概述
本包实现了会话管理系统，支持会话的创建、持久化、消息存储等操作。每个 Session 关联一个智能体身份和消息来源。

## 核心类

### Session
会话实体类。

**字段：**
- `id`: 会话唯一标识（格式：{agentId}-{type}-{source}-{target}）
- `agentId`: 智能体身份标识（AgentIdentityEnum）
- `type`: 会话类型（DM/GROUP）
- `respType`: 响应类型（STREAM/BLOCK）
- `source`: 消息来源标识
- `target`: 消息目标标识
- `parentId`: 父会话ID
- `messages`: 消息列表（Spring AI Message）

**Session ID 格式：**
```
MAIN-DM-wechat-user123
DOCTOR-GROUP-telegram-group456
```

### SessionTypeEnum
会话类型枚举：
- `DM`: 私聊
- `GROUP`: 群聊

### SessionRespTypeEnum
响应类型枚举：
- `STREAM`: 流式响应
- `BLOCK`: 阻塞响应

### SessionIsolationEnum
会话隔离策略枚举：
- `PER_PEER`: 按用户隔离
- `PER_CHANNEL_PEER`: 按通道+用户隔离

### SessionManager
会话管理器，支持持久化和消息存储。

**核心属性：**
- `sessions`: Map<String, Session> - 会话内存缓存

**核心方法：**
- `init()`: 初始化，加载所有会话
- `getOrCreate(source, type, respType, target)`: 获取或创建会话
- `getById(sessionId)`: 获取会话
- `getByTarget(target)`: 根据目标获取会话列表
- `appendMessage(sessionId, messages)`: 追加消息到 messages.jsonl
- `clearSessionMessages(sessionId)`: 清空会话消息记录

**Message 反序列化：**
由于 `Message` 是接口，有多个子类（`UserMessage`、`AssistantMessage`、`ToolResponseMessage`），反序列化时需要根据 `messageType` 字段判断具体类型：
```java
private Message deserializeMessage(String json) {
    JSONObject obj = JSON.parseObject(json);
    MessageType messageType = MessageType.valueOf(obj.getString("messageType"));
    return switch (messageType) {
        case USER -> JSON.parseObject(json, UserMessage.class);
        case ASSISTANT -> JSON.parseObject(json, AssistantMessage.class);
        case TOOL -> JSON.parseObject(json, ToolResponseMessage.class);
        default -> null;
    };
}
```

**文件结构：**
```
~/.autiva/sessions/
├── MAIN-DM-wechat-user123/
│   ├── metadata.json          # Session 元数据
│   └── messages.jsonl         # 对话记录
├── DOCTOR-GROUP-telegram-group456/
│   ├── metadata.json
│   └── messages.jsonl
```

## JSONL 格式

### messages.jsonl 格式
每行存储一条 Spring AI Message 的 JSON 序列化：
```jsonl
{"messageType":"USER","content":"帮我创建一个项目","mediaType":null,"metadata":{}}
{"messageType":"ASSISTANT","content":"好的，我来帮你...","metadata":{"model":"deepseek"}}
{"messageType":"TOOL","content":"{...}","metadata":{"tool":"write"}}
```

### metadata.json 格式
```json
{
  "id": "MAIN-DM-wechat-user123",
  "agentId": "MAIN",
  "type": "DM",
  "source": "wechat",
  "target": "user123",
  "parentId": null
}
```

## 使用示例

### 获取或创建会话
```java
Session session = sessionManager.getOrCreate(
    "wechat",               // source
    SessionTypeEnum.DM,     // type
    SessionRespTypeEnum.STREAM, // respType
    "user123"               // target
);
```

### 追加消息
```java
sessionManager.appendMessage(sessionId, List.of(
    new UserMessage("你好"),
    new AssistantMessage("你好！有什么可以帮助你的？")
));
```

### 获取会话消息
```java
Session session = sessionManager.getById(sessionId);
List<Message> messages = session.getMessages();
```

### 清空会话消息
```java
sessionManager.clearSessionMessages(sessionId);
```

## 会话隔离策略

### PER_PEER（按用户隔离）
同一用户的所有通道共享会话：
- wechat-user123 和 telegram-user123 共享

### PER_CHANNEL_PEER（按通道+用户隔离）
每个通道独立会话：
- wechat-user123 和 telegram-user123 是不同会话

## 设计模式
- 单例模式：SessionManager 是 Spring Bean
- 懒加载模式：启动时加载所有会话

## 注意事项
1. 会话 ID 格式固定为 {agentId}-{type}-{source}-{target}
2. messages 字段不参与 JSON 序列化
3. 消息持久化到 messages.jsonl 文件
4. 使用 ConcurrentHashMap 保证线程安全
