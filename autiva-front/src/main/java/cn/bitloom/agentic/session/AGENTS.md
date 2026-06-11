# Session 包

## 概述
本包实现了会话管理系统，支持会话的创建、持久化、消息存储等操作。每个 Session 关联一个智能体身份和消息来源。消息统一存储在 messages.jsonl 文件中，不再区分通道。Session 是业务编排者，负责消息收发、Agent 调用、Hook 触发、记忆管理。

## 核心类

### Session
会话实体类，同时是消息处理的核心编排者。

**字段：**
- `id`: 会话唯一标识（格式：{agentId}-{type}-{source}-{userId}）
- `agentId`: 智能体身份标识（String 类型）
- `type`: 会话类型（DM/GROUP/SYSTEM）
- `respType`: 响应类型（STREAM/BLOCK）
- `model`: 模型类型（ModelTypeEnum）
- `source`: 消息来源标识
- `parentId`: 父会话ID
- `eventBus`: SessionEventBus 实例（不参与 JSON 序列化，激活 Session 时初始化）
- `agent`: Agent 实例（不参与 JSON 序列化）
- `messages`: List&lt;Message&gt; 消息列表（不参与 JSON 序列化）
- `memoryCursor`: 记忆处理坐标（已处理到的消息索引位置，持久化到 metadata.json）
- `state`: 会话状态（SessionState，持久化到 metadata.json）
- `title`: 对话标题（可选，默认取首条用户消息前 20 字）
- `isFirstConversation`: 是否首次对话（默认 true）
- `createdAt`: 创建时间戳（Long，持久化到 metadata.json）
- `messageLoopSubscription`: inBox 订阅的 Disposable（不参与 JSON 序列化）
- `roundMessages`: 本轮对话收集的 AssistantMessage（不参与 JSON 序列化）

**核心方法：**
- `getMessages()`: 获取消息列表（null 安全，懒初始化）
- `getMemoryCursor()`: 获取记忆游标（null 安全，默认 0）
- `getDisplayTitle()`: 获取显示标题（优先使用 title 字段，否则取首条用户消息前 20 字，无消息返回"新对话"）
- `getCreatedAt()`: 获取创建时间戳（null 安全，默认 0）
- `hasHistoricalMessages()`: 判断是否有历史消息
- `startMessageLoop(SessionManager)`: 订阅 inBox，收到消息后调用 Agent 执行
- `stopMessageLoop()`: 取消 inBox 订阅
- `handleMessage(Message, SessionManager)`: 处理单条消息，首次对话时注入 BOOTSTRAP.md，调用 Agent 并发布响应
- `readBootstrapMd()`: 读取 BOOTSTRAP.md 文件内容

**Session ID 格式：**
```
default-DM-desktopApp-default
default-DM-wechat-user123
```

**Session ID 规则：**
- `{agentId}-{type}-{source}-{userId}`

**消息处理流程：**
1. `startMessageLoop()` 订阅 `eventBus.inBoxSubscribe()`
2. 收到消息后调用 `handleMessage()`
3. 首次对话时，读取 BOOTSTRAP.md 并作为 SystemMessage 前置注入
4. 流式模式：`agent.runStream()` → 逐条 `outBoxPublish()` → 完成后 `appendMessage()` + `fireSessionEnd()` + `saveContext()`
5. 阻塞模式：`agent.runBlock()` → `outBoxPublish()` → `appendMessage()` + `fireSessionEnd()` + `saveContext()`
6. 首次对话完成后，将 `isFirstConversation` 设为 false

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

**核心方法：**
- `init()`: 初始化，从 workspace/{agentId}/sessions/ 加载所有会话（仅反序列化元数据，不设置 EventBus）
- `activateSession(sessionId)`: 激活会话（设置 EventBus、注入 Agent、启动消息循环、加载历史消息、恢复上下文），仅在用户切换到该 session 时调用
- `getOrCreate(agentId, source, type, respType, model)`: 获取或创建指定智能体的会话
- `getOrCreate(agentId, userId, source, type, respType, model)`: 获取或创建指定智能体和用户的会话
- `getById(sessionId)`: 获取会话
- `getAllUserSessions()`: 获取所有非子会话的用户会话
- `getDesktopSessions()`: 获取所有桌面端 session
- `forkSession(parentSessionId, subagentName)`: Fork子会话
- `appendMessage(sessionId, messages)`: 追加消息到会话，写入内存和 messages.jsonl
- `loadSessionMessages(sessionId)`: 从 messages.jsonl 按需加载消息
- `clearSessionMessages(sessionId)`: 清空会话消息记录
- `deleteSession(sessionId)`: 删除会话（停止消息循环 + 从内存移除 + 删除磁盘文件夹）
- `updateCursor(sessionId, cursorField, value)`: 更新会话游标
- `updateState(sessionId, SessionState state)`: 更新会话状态
- `publishMessage(sessionId, message)`: 通过 Session 的 SessionEventBus 发布消息到 InBox
- `bindAgentAndStart(sessionId, agent)`: 为 Session 绑定 Agent 并启动消息处理循环
- `saveContext(sessionId)`: 保存会话上下文快照到 context/{sessionId}/agent_state.json
- `loadContext(sessionId)`: 从 context/{sessionId}/agent_state.json 恢复会话上下文

**私有方法：**
- `loadAllSessions()`: 从 workspace/{agentId}/sessions/ 加载所有会话（仅反序列化元数据，不设置 EventBus、不注入 Agent、不启动消息循环）
- `deserializeMessage(String json)`: 反序列化 JSON 字符串为 Spring AI Message
- `recoverSubagentCounter()`: 恢复子智能体计数器
- `persistMetadata(Session session)`: 持久化会话元数据到 metadata.json
- `getSessionDir(Session)`: 获取 Session 的存储目录（workspace/{agentId}/sessions/{sessionId}/）

**文件结构：**
```
~/.autiva/workspace/{agentId}/sessions/
├── {sessionId}/
│   ├── metadata.json
│   └── messages.jsonl
```

**Context 快照结构：**
```
~/.autiva/workspace/{agentId}/context/{sessionId}/
└── agent_state.json
```

## JSONL 格式

### messages.jsonl 格式
每行存储一条 Spring AI Message 的 JSON 序列化：
```jsonl
{"messageType":"USER","text":"帮我创建一个项目","media":null,"metadata":{}}
{"messageType":"ASSISTANT","text":"好的，我来帮你...","metadata":{"model":"deepseek"}}
{"messageType":"TOOL","text":"{...}","metadata":{"tool":"write"}}
```

### metadata.json 格式
```json
{
  "id": "default-DM-desktopApp-default",
  "agentId": "default",
  "type": "DM",
  "source": "desktopApp",
  "parentId": null,
  "memoryCursor": 0,
  "state": "IDLE",
  "title": null,
  "isFirstConversation": true,
  "createdAt": 1717400000000
}
```

### agent_state.json 格式
```json
{
  "sessionId": "default-DM-desktopApp-default",
  "agentId": "default",
  "memoryCursor": 10,
  "isFirstConversation": false,
  "messageCount": 15,
  "savedAt": 1717400000000
}
```

## 设计模式
- 单例模式：SessionManager 为 Spring Bean
- 懒加载模式：启动时仅反序列化会话元数据，点击会话时通过 activateSession() 激活（设置 EventBus、注入 Agent、启动消息循环、加载历史消息）
- 观察者模式：通过 SessionEventBus 实现消息传递
- 策略模式：Session 持有 Agent 引用并委托执行

## 注意事项
1. 会话 ID 格式：`{agentId}-{type}-{source}-{userId}`
2. messages 字段不参与 JSON 序列化（@JSONField(serialize = false)）
3. 消息持久化到 workspace/{agentId}/sessions/{sessionId}/messages.jsonl
4. 使用 ConcurrentHashMap 保证线程安全
5. agentId 为 String 类型
6. publishMessage 简化为只传 message
7. Session 的 eventBus 字段不参与 JSON 序列化，从磁盘加载时不创建，需通过 activateSession() 激活时创建
8. clearSessionMessages() 会清空内存消息列表和磁盘文件
9. deleteSession() 会先停止消息循环再删除
10. bindAgentAndStart() 必须在创建 Session 后调用，否则消息无法处理
