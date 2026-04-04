# Memory 包

## 概述
本包实现了对话记忆管理，实现 Spring AI 的 ChatMemory 接口，通过 SessionManager 进行消息的持久化和加载，并提供自动压缩功能。

## 核心类

### ConpactChatMemory
实现 Spring AI 的 ChatMemory 接口，提供对话记忆管理。

**依赖注入：**
- `SessionManager`: 会话管理器

**配置参数：**
- `KEEP_RECENT = 3`: 保留最近 N 条工具调用结果
- `TOKEN_THRESHOLD = 8000`: Token 数量阈值

**核心方法：**
- `add(conversationId, messages)`: 添加消息到会话
- `get(conversationId)`: 获取会话消息（带压缩处理）
- `clear(conversationId)`: 清除会话消息

**conversationId：**
直接使用 sessionId，格式为 `{agentId}-{type}-{source}-{target}`

## 记忆处理流程

### 添加消息流程
```
ConpactChatMemory.add(conversationId, messages)
         │
         └── SessionManager.appendMessage(conversationId, messages)
                   │
                   └── 写入 ~/.autiva/sessions/{sessionId}/messages.jsonl
```

### 获取消息流程
```
ConpactChatMemory.get(conversationId)
         │
         ├── 1. 从 SessionManager 获取消息列表
         │
         ├── 2. microCompact: 压缩工具调用结果
         │
         └── 3. autoCompact: Token 超限时的自动归档
```

## 压缩策略

### Micro Compact (微压缩)
当工具调用结果过多时：
- 保留最近的 3 条完整工具结果
- 较早的工具结果压缩为 `[Previous tool result from: toolName]`

**实现逻辑：**
```java
private List<Message> microCompact(List<Message> messages) {
    // 统计 TOOL 类型消息数量
    // 如果超过 KEEP_RECENT，将较早的 TOOL 消息压缩为简短描述
}
```

### Auto Compact (自动压缩)
当 Token 数量超过阈值（8000）时：
- 将历史消息保存到 `~/.autiva/logs/transcripts/` 目录
- 文件名格式：`transcript_{conversationId}_{timestamp}.jsonl`
- 生成摘要并清空当前记忆

**实现逻辑：**
```java
private List<Message> autoCompact(String conversationId, List<Message> messages) {
    // 1. 创建 transcripts 目录
    // 2. 保存消息到文件
    // 3. 生成摘要
    // 4. 返回压缩后的消息列表
}
```

## 使用示例

### 添加消息
```java
chatMemory.add(sessionId, List.of(
    new UserMessage("你好"),
    new AssistantMessage("你好！有什么可以帮助你的？")
));
```

### 获取消息
```java
List<Message> messages = chatMemory.get(sessionId);
```

### 在 ChatClient 中使用
```java
ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(a -> a.advisors(
        MessageChatMemoryAdvisor.builder(chatMemory).build()
    ))
    .build();

// 调用时传入 conversationId
chatClient.prompt()
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
    .messages(userMessage)
    .call();
```

## 设计模式
- 代理模式：代理 SessionManager 进行消息管理
- 策略模式：不同的压缩策略

## 注意事项
1. conversationId 直接使用 sessionId
2. 消息持久化由 SessionManager 负责
3. 压缩只在获取消息时进行，不影响原始存储
4. Token 估算是基于字符数的近似计算
