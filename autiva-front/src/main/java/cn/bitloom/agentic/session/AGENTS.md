# Session 包

## 概述
本包实现了会话管理系统，支持会话的创建、持久化、消息存储等操作。每个 Session 关联一个智能体身份和消息来源。支持多主智能体独立会话。通过 MessageChannel 枚举实现消息的多通道分区存储，不同类型的消息（用户对话、系统消息、记忆整理、日记、进化）分别存储在独立通道中，每个通道对应独立的持久化文件。

## 核心类

### MessageChannel
消息通道枚举，定义消息的分类通道，用于将不同类型的消息分区存储和管理。

**枚举值：**
- `USER`: 用户对话消息通道（默认通道，存储用户与智能体的对话历史）
- `SYSTEM`: 系统消息通道（存储心跳检测、定时任务等系统级消息）
- `MEMORY`: 记忆整理消息通道（存储记忆总结、归档等消息）
- `JOURNAL`: 日记消息通道（存储日记记录消息）
- `EVOLVE`: 进化消息通道（存储系统自演化相关消息）

**核心方法：**
- `fromEventType(EventType eventType)`: 根据 EventType 映射到对应的 MessageChannel，null 值默认返回 USER
- `shouldPublishToOutBox()`: 判断该通道消息是否需要发布到 OutBox（仅 USER 通道返回 true）

**MessageChannel → EventType 映射表：**

| EventType | MessageChannel | 说明 |
|-----------|---------------|------|
| MESSAGE | USER | 用户消息 |
| MEMORY_CONSOLIDATE | MEMORY | 记忆整理 |
| JOURNAL | JOURNAL | 日记 |
| EVOLVE | EVOLVE | 进化 |
| null | USER | 默认 |

### Session
会话实体类。

**字段：**
- `id`: 会话唯一标识（格式：{agentId}-{type}-{source}-{target}）
- `agentId`: 智能体身份标识（AgentIdentityEnum，目前有 MAIN / EVOLVER）
- `type`: 会话类型（DM/GROUP/SYSTEM）
- `respType`: 响应类型（STREAM/BLOCK）
- `model`: 模型类型（ModelTypeEnum）
- `source`: 消息来源标识
- `target`: 消息目标标识
- `parentId`: 父会话ID
- `channelMessages`: Map&lt;MessageChannel, List&lt;Message&gt;&gt; 多通道消息映射（使用 EnumMap，不参与 JSON 序列化）
- `memoryCursor`: 记忆处理坐标（已处理到的消息索引位置，持久化到 metadata.json）
- `journalCursor`: 日记处理坐标（持久化到 metadata.json）
- `state`: 会话状态（SessionState，持久化到 metadata.json）
- `title`: 对话标题（可选，默认取首条用户消息前 20 字）
- `createdAt`: 创建时间戳（Long，持久化到 metadata.json）

**核心方法：**
- `getChannelMessages(MessageChannel channel)`: 获取指定通道的消息列表（懒初始化，computeIfAbsent）
- `getMessages()`: 获取 USER 通道的消息列表（等价于 getChannelMessages(MessageChannel.USER)）
- `getAllChannelMessages()`: 获取所有通道的消息映射
- `getMemoryCursor()`: 获取记忆游标（null 安全，默认 0）
- `getJournalCursor()`: 获取日记游标（null 安全，默认 0）
- `getDisplayTitle()`: 获取显示标题（优先使用 title 字段，否则取首条用户消息前 20 字，无消息返回"新对话"）
- `getCreatedAt()`: 获取创建时间戳（null 安全，默认 0）

**Session ID 格式：**
```
MAIN-DM-desktopApp-bitloom            # 主助手桌面端会话
MAIN-DM-wechat-user123                # 微信渠道主助手会话
EVOLVER-SYSTEM-internal-internal      # EVOLVER 系统会话
```

### SessionTypeEnum
会话类型枚举：
- `DM`: 私聊
- `GROUP`: 群聊
- `SYSTEM`: 系统会话（用于后台任务、心跳检测、定时任务等系统级行为，响应类型固定为 BLOCK）

### SessionState
会话状态枚举，追踪会话当前的生成状态：
- `IDLE`: 空闲状态，等待用户输入
- `GENERATING`: 正在生成响应
- `PAUSED`: 生成已暂停（用户点击暂停按钮）

**状态转换：**
- IDLE → GENERATING：用户发送消息
- GENERATING → PAUSED：用户点击暂停按钮
- GENERATING → IDLE：生成自然完成或用户点击停止按钮
- PAUSED → IDLE：用户发送新消息或点击停止按钮

**持久化：** state 字段持久化到 metadata.json，应用启动时加载会话若状态非 IDLE 则自动重置为 IDLE

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
- `sessions`: Map&lt;String, Session&gt; - 会话内存缓存
- `LEGACY_MESSAGES_FILE`: 旧版消息文件名常量（"messages.jsonl"），用于迁移

**核心方法：**
- `init()`: 初始化，加载所有会话
- `getOrCreate(source, type, respType, model, target)`: 获取或创建会话（默认 MAIN 智能体）
- `getOrCreate(agentId, source, type, respType, model, target)`: 获取或创建指定智能体的会话
- `getById(sessionId)`: 获取会话
- `getByTarget(target)`: 根据目标获取会话列表
- `getAllUserSessions()`: 获取所有非 SYSTEM 类型的用户会话
- `getDesktopSessions()`: 获取所有桌面端 session（source="desktopApp" 且 parentId=null），按最后活跃时间倒序排序
- `getSessionLastActiveTime(String sessionId)`: 获取 session 最后活跃时间（从 USER.jsonl 文件修改时间获取）
- `forkSession(parentSessionId, subagentName)`: Fork子会话
- `appendMessage(sessionId, messages)`: 追加消息到 USER 通道（向后兼容，内部调用带 channel 参数的重载方法）
- `appendMessage(sessionId, MessageChannel channel, List<Message> messages)`: 追加消息到指定通道，根据隔离策略写入内存和持久化文件
- `clearSessionMessages(sessionId)`: 清空会话所有通道的消息记录（保留会话文件夹和metadata）
- `deleteSession(sessionId)`: 删除会话（从内存移除 + 删除磁盘文件夹，用于清除子智能体会话）
- `getChildSessions(parentSessionId)`: 获取子会话列表
- `updateCursor(sessionId, cursorField, value)`: 更新会话的游标值（memoryCursor/journalCursor），同步更新内存和 metadata.json
- `updateState(sessionId, SessionState state)`: 更新会话状态，同步更新内存和 metadata.json

**私有方法：**
- `getChannelFilePath(sessionId, MessageChannel channel)`: 获取指定通道的持久化文件路径（{CHANNEL}.jsonl）
- `migrateLegacyMessages(Path sessionDir)`: 迁移旧版 messages.jsonl 文件为 USER.jsonl
- `loadAllSessions()`: 加载所有会话，包括迁移旧文件和加载各通道消息
- `deserializeMessage(String json)`: 反序列化 JSON 字符串为 Spring AI Message，所有类型均使用 Builder 模式手动构建（避免 fastjson2 对 protected 构造函数生成 JDK 动态代理）

**appendMessage 核心逻辑：**
1. 根据会话隔离策略（PER_PEER / PER_CHANNEL_PEER）确定目标会话列表
2. 将消息追加到目标会话的对应通道内存列表
3. 如果通道的 `shouldPublishToOutBox()` 返回 true（仅 USER 通道），将 AssistantMessage（TOOL_CALLS）和 ToolResponseMessage 发布到 OutBox
4. 将消息逐行追加到对应的 {CHANNEL}.jsonl 持久化文件

**事件发布规则：**
- 创建用户会话（非 SYSTEM 类型）时，通过 `EventBus.sysPublish(AutivaEventType.SESSION_CREATED, sessionId)` 发布事件
- 删除用户会话（非 SYSTEM 类型）时，通过 `EventBus.sysPublish(AutivaEventType.SESSION_DELETED, sessionId)` 发布事件
- SYSTEM 类型会话的创建和删除不发布事件，避免递归

**多智能体会话支持：**
- `getOrCreate()` 接受 `AgentIdentityEnum` 参数，支持创建不同智能体的会话
- Session ID 使用精确匹配（`equals`），避免不同智能体的会话冲突
- 旧版 `getOrCreate(source, type, respType, model, target)` 保持向后兼容，默认创建 MAIN 智能体会话

**文件结构：**
```
~/.autiva/sessions/
├── MAIN-DM-desktopApp-bitloom/
│   ├── metadata.json
│   ├── USER.jsonl
│   ├── SYSTEM.jsonl
│   ├── MEMORY.jsonl
│   ├── JOURNAL.jsonl
│   └── EVOLVE.jsonl
├── MAIN-DM-wechat-user123/
│   ├── metadata.json
│   ├── USER.jsonl
│   ├── SYSTEM.jsonl
│   ├── MEMORY.jsonl
│   ├── JOURNAL.jsonl
│   └── EVOLVE.jsonl
├── EVOLVER-SYSTEM-internal-internal/
│   ├── metadata.json
│   ├── USER.jsonl
│   ├── SYSTEM.jsonl
│   ├── MEMORY.jsonl
│   ├── JOURNAL.jsonl
│   └── EVOLVE.jsonl
```

**旧版迁移：**
- 启动时 `migrateLegacyMessages()` 自动检测 `messages.jsonl` 文件
- 若存在旧文件且不存在 `USER.jsonl`，自动将 `messages.jsonl` 重命名为 `USER.jsonl`
- 迁移后旧文件不再存在，确保向后兼容

## JSONL 格式

### 通道 JSONL 格式
每个通道（USER.jsonl、SYSTEM.jsonl、MEMORY.jsonl、JOURNAL.jsonl、EVOLVE.jsonl）格式相同，每行存储一条 Spring AI Message 的 JSON 序列化：
```jsonl
{"messageType":"USER","text":"帮我创建一个项目","media":null,"metadata":{}}
{"messageType":"ASSISTANT","text":"好的，我来帮你...","metadata":{"model":"deepseek"}}
{"messageType":"TOOL","text":"{...}","metadata":{"tool":"write"}}
```

### metadata.json 格式
```json
{
  "id": "MAIN-DM-desktopApp-bitloom",
  "agentId": "MAIN",
  "type": "DM",
  "source": "desktopApp",
  "target": "bitloom",
  "parentId": null,
  "memoryCursor": 0,
  "journalCursor": 0,
  "state": "IDLE",
  "title": null,
  "createdAt": 1717400000000
}
```

## 使用示例

### 获取或创建主助手会话
```java
Session session = sessionManager.getOrCreate(
    "desktopApp",
    SessionTypeEnum.DM,
    SessionRespTypeEnum.STREAM,
    ModelTypeEnum.DEEPSEEK,
    "bitloom"
);
```

### 追加消息到 USER 通道（向后兼容）
```java
sessionManager.appendMessage(sessionId, List.of(
    new UserMessage("你好"),
    new AssistantMessage("你好！有什么可以帮助你的？")
));
```

### 追加消息到指定通道
```java
sessionManager.appendMessage(sessionId, MessageChannel.SYSTEM, List.of(
    new UserMessage("心跳检测")
));

sessionManager.appendMessage(sessionId, MessageChannel.MEMORY, List.of(
    new AssistantMessage("记忆整理结果...")
));
```

### 获取会话消息
```java
Session session = sessionManager.getById(sessionId);
List<Message> userMessages = session.getMessages();
List<Message> systemMessages = session.getChannelMessages(MessageChannel.SYSTEM);
Map<MessageChannel, List<Message>> allMessages = session.getAllChannelMessages();
```

### 清空会话消息
```java
sessionManager.clearSessionMessages(sessionId);
```

### 删除会话（含磁盘文件夹）
```java
sessionManager.deleteSession(sessionId);
```

### 获取子会话列表
```java
List<Session> childSessions = sessionManager.getChildSessions(parentSessionId);
```

### 获取所有用户会话
```java
List<Session> userSessions = sessionManager.getAllUserSessions();
```

## 会话隔离策略

### PER_PEER（按用户隔离）
同一用户的所有通道共享会话：
- wechat-user123 和 telegram-user123 共享

### PER_CHANNEL_PEER（按通道+用户隔离）
每个通道独立会话：
- wechat-user123 和 telegram-user123 是不同会话

## 事件驱动架构

### 事件流程
```
用户会话创建 → SessionManager.getOrCreate()
    → EventBus.sysPublish(SESSION_CREATED, sessionId)
    → HeartbeatRunner.registerSession()                  # 注册心跳

用户会话删除 → SessionManager.deleteSession()
    → EventBus.sysPublish(SESSION_DELETED, sessionId)
    → HeartbeatRunner.unregisterSession()                # 注销心跳
```

### HeartbeatRunner 事件流
```
HeartbeatRunner 定时触发
        │
        ▼
EventBus.inBoxPublish(用户会话ID, heartbeat消息, EventType.MESSAGE, MessageChannel.SYSTEM)
        │
        ▼
MainAgent 接收消息（通过 MessageChannel.SYSTEM 通道）
        │
        ▼
ProactiveContextAdvisor 注入 HEARTBEAT.md 检查清单
        │
        ▼
智能体按清单检查 → 回复 HEARTBEAT_OK（无需行动时）或执行相应操作
        │
        ▼
HEARTBEAT_OK 被抑制，不展示给用户
```

### 设计要点
- SYSTEM 类型会话不发布事件，避免事件循环
- HeartbeatRunner 现在向用户会话的 SYSTEM 通道发送心跳消息，不再需要独立的系统会话
- 事件处理中的异常不会中断主流程，仅记录警告日志
- 所有事件通过 EventBus sysBox 通道异步传递
- MessageChannel 实现了消息的分区存储，不同类型消息互不干扰

## 设计模式
- 单例模式：SessionManager 为 Spring Bean
- 懒加载模式：启动时加载所有会话
- 观察者模式：通过 EventBus sysBox 通道实现会话生命周期事件通知
- 通道分区模式：通过 MessageChannel 枚举将消息按类型分区存储，每个通道独立持久化
- 迁移兼容模式：migrateLegacyMessages() 自动将旧版 messages.jsonl 迁移为 USER.jsonl

## 注意事项
1. 会话 ID 格式固定为 {agentId}-{type}-{source}-{target}
2. channelMessages 字段不参与 JSON 序列化（@JSONField(serialize = false)）
3. 消息按通道持久化到 {CHANNEL}.jsonl 文件（USER.jsonl、SYSTEM.jsonl 等）
4. 使用 ConcurrentHashMap 保证线程安全
5. SYSTEM 类型会话的创建/删除不触发系统事件，避免递归
6. 旧版 messages.jsonl 文件会在启动时自动迁移为 USER.jsonl
7. getMessages() 方法返回 USER 通道消息，保持向后兼容
8. 只有 USER 通道的消息会发布到 OutBox（shouldPublishToOutBox() 返回 true）
9. MEMORY 和 JOURNAL 通道的消息通过 cursor 机制从对应通道文件重建
10. memoryCursor 和 journalCursor 持久化到 metadata.json，用于断点续处理
11. clearSessionMessages() 会清空所有通道的内存和磁盘文件
