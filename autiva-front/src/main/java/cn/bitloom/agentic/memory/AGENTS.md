# Memory 包

## 概述
本包实现了对话记忆管理和持久化记忆服务，实现 Spring AI 的 ChatMemory 接口，通过 SessionManager 进行消息的持久化和加载，并提供自动压缩功能。同时提供每日日志管理和记忆搜索服务。

## 核心类

### ConpactChatMemory
实现 Spring AI 的 ChatMemory 接口，提供对话记忆管理。

**依赖注入：**
- `SessionManager`: 会话管理器

**核心方法：**
- `add(conversationId, messages)`: 添加消息到会话（追加前自动清理孤儿工具调用消息）
- `get(conversationId)`: 获取会话消息（延迟加载，首次获取时从磁盘加载）
- `clear(conversationId)`: 清除会话消息

**conversationId：**
直接使用 sessionId，格式为 `{agentId}-{type}-{source}-{target}`

### JournalManager
每日日志管理器，参考 OpenClaw 的每日日志机制，在会话结束时自动追加日志条目，在 ProactiveContextAdvisor 中自动注入近期日志摘要。

**Spring 注解：** `@Component`

**存储路径：** `~/.autiva/workspace/MAIN/memories/journal/YYYY-MM-DD.md`

**核心方法：**
- `getRecentJournalsSummary(int days)`: 获取最近N天的日志摘要（用于自动注入）
- `appendFromSession(String sessionId, String sessionSummary)`: 从会话消息中提取关键信息追加到当日日志

**日志格式：**
```markdown
---
date: 2026-05-15
agent: MAIN
---

# 2026-05-15

## 会话 14:30
[会话摘要内容]
```

### MemorySearchService
记忆搜索服务，提供基于关键词的记忆搜索能力，用于 ProactiveContextAdvisor 的自动召回功能。

**Spring 注解：** `@Component`

**搜索路径：** `~/.autiva/memory/`（即 `AppConstants.Base.MEMORY_DIR`）

**核心方法：**
- `searchAndFormat(String query, int limit)`: 搜索记忆并格式化返回结果

**搜索策略：**
- 基础方案：遍历记忆目录下的 YYYY-MM-DD.md 文件，匹配文件名和内容关键词
- 返回格式：文件名 + 描述 + 内容片段

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
         ├── 1. 检查消息是否已加载（session.getMessages().isEmpty()），未加载则调用 SessionManager.loadSessionMessages() 延迟加载
         │
         └── 2. 返回 session.getMessages()
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
