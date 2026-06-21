# Session 包

## 概述
本包实现了会话管理系统，支持主会话和子会话两种模式。Session 是纯实体类（唯一的状态源），编排逻辑由 SessionRunner 负责。主会话通过 FileSystemSessionManager 管理并持久化到磁盘，子会话通过 InMemorySessionManager 管理并纯内存存储。

## 核心类

### Session
会话实体类，唯一的状态源。编排逻辑已提取到 SessionRunner。

**标识字段：**
- `id`: 会话唯一标识
  - 主会话格式：`{agentId}-{type}-{source}-{userId}-{timestamp}`
  - 子会话格式：`sub-{agentId}-{timestamp}`
- `agentId`: 所属智能体标识
- `userId`: 所属用户标识
- `parentId`: 父会话ID（子 Session 关联父 Session，主会话为 null）

**会话分类字段：**
- `sessionType`: 会话类型（DM/GROUP/SYSTEM/SUB）
- `respType`: 响应类型（STREAM/BLOCK）
- `source`: 消息来源标识

**会话元数据字段：**
- `model`: 模型类型（ModelTypeEnum）
- `title`: 对话标题（默认"新对话"）
- `createdAt`: 创建时间戳
- `updateAt`: 更新时间戳
- `sessionState`: 会话状态（IDLE/GENERATING/PAUSED/STOPPED）

**对话上下文字段：**
- `messageCount`: 当前对话消息数量
- `memoryCursor`: 记忆处理坐标（已处理到的消息索引位置）
- `summary`: 压缩后的对话摘要

**压缩上下文字段：**
- `contextCapacity`: 上下文容量上限（token 数，默认 64000）
- `compactionThreshold`: 压缩触发阈值（0.0~1.0，默认 0.8）
- `currentContextLength`: 当前上下文使用长度（token 数，由 UsageAdvisor 从模型响应 Usage 中提取）

**元数据字段：**
- `savedAt`: 保存时间戳
- `shutdownInterrupted`: 是否被优雅停机中断

**编码智能体字段：**
- `projectPath`: 关联的项目路径（编码智能体场景，可为 null）
- `reviewDiff`: 是否启用 Diff 审核（coder 场景为 true，默认 false，持久化以便重启后保留）

**瞬态字段（不序列化，@JsonIgnore）：**
- `agent`: Agent 实例
- `messages`: List\<Message\> 消息列表（Spring AI 聊天记忆）
- `memoryManager`: MemoryManager 实例（由 FileSystemSessionManager.activate() 注入，子 Session 不注入）

**方法：**
- `isStop()`: 判断是否已停止

### SessionRunner
Session 编排器，负责主会话的消息循环和记忆事件处理。从 Session 类中提取的编排逻辑，使 Session 成为纯实体。

**核心方法：**
- `start()`: 订阅 EventBus.inBoxFlux()，过滤当前会话事件，按事件类型分类处理：
  - `MessageEvent.USER` → 串行调用 Agent 执行对话（concatMap），构造 RuntimeContext 时从 session 读取 reviewDiff/projectPath 传递到 params（供 WriteTool/EditTool/TaskTool 使用）
  - `MemoryEvent` → 异步处理（`Schedulers.boundedElastic()`），不阻塞对话流
- `stop()`: 发布 `MemoryEvent.sessionEnd()` 到 inBox 触发异步记忆检查，然后取消订阅
- `handleMemoryEvent(event)`: 根据 MemoryEvent.type 分发到 handleContextCompact 或 handleSessionEnd
- `handleContextCompact(memoryManager)`: 调用 `memoryManager.compact()` 生成摘要，推进 memoryCursor，重置 currentContextLength
- `handleSessionEnd(memoryManager)`: 调用 `memoryManager.consolidate()` 提取关键事实追加到日流水账

### ISessionManager
Session 管理器统一接口。不同的实现提供不同的存储策略：
- `FileSystemSessionManager`：磁盘持久化，用于主会话
- `InMemorySessionManager`：纯内存，用于子智能体会话

**方法：**
- `create(agentId, parentSessionId, type, respType, model)`: 创建新的 Session
- `getById(sessionId)`: 根据 ID 获取 Session
- `remove(sessionId)`: 移除 Session
- `store(sessionId, messages)`: 存储消息到 Session

### SessionTypeEnum
会话类型枚举：
- `DM`: 私聊
- `GROUP`: 群聊
- `SYSTEM`: 系统会话
- `SUB`: 子智能体会话

### SessionState
会话状态枚举：
- `IDLE`: 空闲
- `GENERATING`: 正在生成
- `PAUSED`: 已暂停
- `STOPPED`: 已停止

### SessionRespTypeEnum
响应类型枚举：
- `STREAM`: 流式响应
- `BLOCK`: 阻塞响应

### FileSystemSessionManager
主会话管理器，Spring @Component，实现 ISessionManager 接口，支持持久化和消息存储。Session 是唯一的持久化格式。同时负责主智能体 Agent 实例的构建和缓存。通过 SessionRunner 管理主会话的消息循环。

**核心字段：**
- `definitionManager`: AgentDefinitionManager — 智能体定义管理
- `modelFactory`: ModelFactory — 模型工厂
- `toolkit`: Toolkit — 工具容器
- `chatMemory`: CompactChatMemory（@Lazy，避免循环依赖）
- `memoryManager`: MemoryManager — 记忆管理器
- `sessions`: Map\<String, Session\> 会话内存缓存
- `agentCache`: Map\<String, Agent\> Agent 实例缓存（懒加载）
- `runners`: Map\<String, SessionRunner\> SessionRunner 实例缓存

**核心方法：**
- `init()`: @PostConstruct，从 workspace/{agentId}/sessions/ 加载所有会话，预加载 default 主智能体
- `create(agentId, parentSessionId, type, respType, model)`: 创建新的桌面端 Session，走完整流程：创建 → 持久化 → 注册 → 激活（委托重载方法传 projectPath=null, reviewDiff=false）
- `create(agentId, parentSessionId, type, respType, model, projectPath, reviewDiff)`: 创建带编码参数的 Session（coder 场景使用，projectPath 关联项目路径，reviewDiff=true 启用 Diff 审核）
- `activate(sessionId)`: 激活会话（注入 Agent、注入 MemoryManager、加载历史消息、通过 SessionRunner 启动消息循环）
- `stopSession(sessionId)`: 通过 SessionRunner 停止会话的消息处理循环
- `getOrCreateAgent(agentId)`: 获取或懒加载创建主智能体 Agent 实例（computeIfAbsent，默认开启 memory + compact）
- `getById(sessionId)`: 获取会话
- `getDesktopSessions()`: 获取所有桌面端 session
- `store(sessionId, messages)`: 追加消息到 messages.jsonl
- `clear(sessionId)`: 清空会话消息记录
- `delete(sessionId)`: 删除会话（通过 SessionRunner 停止消息循环）
- `remove(sessionId)`: 实现 ISessionManager 接口，委托给 delete
- `updateState(sessionId, SessionState)`: 更新会话状态
- `updateSession(sessionId, Consumer<Session>)`: 局部更新 Session
- `persistSession(Session)`: 序列化 Session 到 metadata.json

**文件结构：**
```
~/.autiva/workspace/{agentId}/sessions/{sessionId}/
├── metadata.json    ← 唯一状态源（Session 序列化）
└── messages.jsonl   ← 消息持久化
```

### InMemorySessionManager
子会话管理器，Spring @Component，实现 ISessionManager 接口，纯内存存储。供子智能体会话使用。

**与 FileSystemSessionManager 的区别：**
- 纯内存存储，不持久化到磁盘
- 不启动 EventBus 消息循环（子 Session 由 TaskTool 直接驱动）
- 不注入 MemoryManager（子智能体不需要记忆整理）
- 提供 InMemoryChatMemory 供子智能体注册 ChatMemory

**核心字段：**
- `sessions`: ConcurrentHashMap\<String, Session\> — 子 Session 内存缓存
- `chatMemory`: InMemoryChatMemory — 子 Session 专用 ChatMemory

**核心方法：**
- `create(agentId, parentSessionId, type, respType, model)`: 创建子 Session（ID 格式：`sub-{agentId}-{timestamp}`），设置 sessionType=SUB，parentId=parentSessionId
- `getById(sessionId)`: 获取子 Session
- `remove(sessionId)`: 移除子 Session 并清理 ChatMemory
- `store(sessionId, messages)`: 追加消息到 Session.messages 列表（纯内存，不写磁盘）
- `getChatMemory()`: 返回 InMemoryChatMemory 实例

### InMemoryChatMemory
纯内存 ChatMemory 实现，供子智能体 Session 使用。

**与 CompactChatMemory 的区别：**
- 不依赖磁盘持久化
- 不做游标感知（子智能体不需要上下文压缩）
- 不做孤儿消息检查（子智能体生命周期短）

**核心方法：**
- `add(conversationId, messages)`: 追加消息到内存列表
- `get(conversationId)`: 返回全部消息（无游标）
- `clear(conversationId)`: 清空消息列表

## 事件模型

### MessageEvent
消息事件，在 EventBus 中传递的核心事件类型。不再持有 `org.springframework.ai.chat.messages.Message`，而是使用结构化字段：

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

### EventConverter
事件转换器，位于 `cn.bitloom.agentic.event` 包，是唯一同时导入 Spring AI 和事件类型的类。负责在 Spring AI Message 和 MessageEvent 之间进行双向转换。

## 设计模式
- 单例模式：FileSystemSessionManager 和 InMemorySessionManager 为 Spring Bean
- 懒加载模式：启动时从 metadata.json 加载元数据，点击会话时通过 activate() 激活
- 观察者模式：通过 EventBus（静态 inBox/outBox，基于 AbstractEvent）实现消息传递
- 策略模式：ISessionManager 接口统一管理不同存储策略的 Session
- 实体与编排分离：Session 为纯实体，SessionRunner 负责编排逻辑

## 注意事项
1. 主会话 ID 格式：`{agentId}-{type}-{source}-{userId}-{timestamp}`
2. 子会话 ID 格式：`sub-{agentId}-{timestamp}`
3. messages 字段不参与 JSON 序列化（@JsonIgnore）
4. 主会话消息持久化到 workspace/{agentId}/sessions/{sessionId}/messages.jsonl
5. 子会话消息纯内存存储，不持久化
6. 使用 ConcurrentHashMap 保证线程安全
7. Session 是唯一的持久化格式，序列化为 metadata.json
8. clear() 会清空内存消息列表和磁盘文件
9. delete() 会先通过 SessionRunner 停止消息循环再删除
10. currentContextLength 由 UsageAdvisor 从模型响应 Usage 中提取并维护（语义为 token 数）
11. 历史消息通过 MessageChatMemoryAdvisor + CompactChatMemory 自动注入
12. MemoryEvent（CONTEXT_COMPACT/SESSION_END）通过 Schedulers.boundedElastic() 异步处理，不阻塞对话流
13. 子智能体通过 InMemorySessionManager 创建子 Session，拥有 ChatMemory 支持对话历史
14. 子 Session 不启动 EventBus 消息循环，由 TaskTool 直接驱动 Agent 执行
15. 消息发布统一使用 `EventBus.publishIn()`，不再通过 `session.publish()`
16. 消息订阅统一使用 `EventBus.outBoxFlux()`，不再通过 `session.subscribe()`
