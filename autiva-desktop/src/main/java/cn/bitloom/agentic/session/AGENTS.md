# Session 包

## 概述
本包实现了会话管理系统，支持会话的创建、持久化、消息存储等操作。每个 Session 关联一个智能体身份和消息来源。Session 是唯一的状态源，所有会话元数据和运行时状态直接定义在 Session 中，序列化为 metadata.json。消息统一存储在 messages.jsonl 文件中。

## 核心类

### Session
会话实体类，消息处理的核心编排者，也是唯一的状态源。

**标识字段：**
- `id`: 会话唯一标识（格式：{agentId}-{type}-{source}-{userId}-{timestamp}）
- `agentId`: 所属智能体标识
- `userId`: 所属用户标识

**会话分类字段：**
- `sessionType`: 会话类型（DM/GROUP/SYSTEM）
- `respType`: 响应类型（STREAM/BLOCK）
- `source`: 消息来源标识
- `parentId`: 父会话ID

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

**工具上下文字段：**
- `activatedToolGroups`: 激活的工具组
- `permissionMode`: 当前权限模式

**任务上下文字段：**
- `tasks`: todo_write 维护的任务清单（List\<TaskItem\>）

**计划模式上下文字段：**
- `planModeActive`: Plan Mode 是否激活
- `planFilePath`: 计划文件路径

**元数据字段：**
- `savedAt`: 保存时间戳
- `shutdownInterrupted`: 是否被优雅停机中断

**瞬态字段（不序列化，@JsonIgnore）：**
- `agent`: Agent 实例
- `messages`: List\<Message\> 消息列表（Spring AI 聊天记忆）
- `interruptControl`: per-session 中断信号（懒初始化）
- `memoryManager`: MemoryManager 实例（由 FileSystemSessionManager.activate() 注入）

**编排方法：**
- `start()`: 订阅 EventBus.inBoxFlux()，过滤当前会话事件，按事件类型分类处理：
  - `MessageEvent.USER` → 串行调用 Agent 执行对话（concatMap）
  - `MemoryEvent` → 异步处理（`Schedulers.boundedElastic()`），不阻塞对话流
- `handleMemoryEvent(event)`: 根据 MemoryEvent.type 分发到 handleContextCompact 或 handleSessionEnd
- `handleContextCompact(event)`: 调用 `memoryManager.compact()` 生成摘要，推进 memoryCursor，重置 currentContextLength
- `handleSessionEnd(event)`: 调用 `memoryManager.consolidate()` 提取关键事实追加到日流水账
- `publish(AbstractEvent)`: 发布事件到 EventBus.inBox
- `subscribe()`: 订阅 EventBus.outBoxFlux() 消息流
- `stop()`: 发布 `MemoryEvent.sessionEnd()` 到 inBox 触发异步记忆检查，然后取消订阅
- `isStop()`: 判断是否已停止

**内部类 TaskItem：**
- `id`: 任务唯一标识
- `content`: 任务描述
- `status`: 任务状态（pending / in_progress / completed）
- `priority`: 优先级（high / medium / low）

### SessionTypeEnum
会话类型枚举：
- `DM`: 私聊
- `GROUP`: 群聊
- `SYSTEM`: 系统会话

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
会话管理器，Spring @Component，支持持久化和消息存储。Session 是唯一的持久化格式。同时负责主智能体 Agent 实例的构建和缓存（原 AgentManager 功能下沉）。

**核心字段：**
- `definitionManager`: AgentDefinitionManager — 智能体定义管理
- `modelFactory`: ModelFactory — 模型工厂
- `toolkit`: Toolkit — 工具容器
- `chatMemory`: CompactChatMemory（@Lazy，避免循环依赖）
- `memoryManager`: MemoryManager — 记忆管理器
- `sessions`: Map\<String, Session\> 会话内存缓存
- `agentCache`: Map\<String, Agent\> Agent 实例缓存（懒加载）

**核心方法：**
- `init()`: @PostConstruct，从 workspace/{agentId}/sessions/ 加载所有会话，预加载 default 主智能体
- `create(agentId, source, type, respType, model)`: 创建新的桌面端 Session（UUID 格式 ID），走完整流程：创建 → 持久化 → 注册 → 激活
- `activate(sessionId)`: 激活会话（设置 EventBus、通过 getOrCreateAgent 注入 Agent、注入 MemoryManager、启动消息循环、加载历史消息、恢复上下文）
- `getOrCreateAgent(agentId)`: 获取或懒加载创建主智能体 Agent 实例（computeIfAbsent，默认开启 memory + compact）
- `resolveModel(definition)`: 解析主智能体使用的模型
- `getById(sessionId)`: 获取会话
- `getAllUserSessions()`: 获取所有非子会话的用户会话
- `getDesktopSessions()`: 获取所有桌面端 session
- `store(sessionId, messages)`: 追加消息到 messages.jsonl
- `clear(sessionId)`: 清空会话消息记录
- `delete(sessionId)`: 删除会话
- `updateState(sessionId, SessionState)`: 更新会话状态
- `updateSession(sessionId, Consumer<Session>)`: 局部更新 Session
- `persistSession(Session)`: 序列化 Session 到 metadata.json（public，供 TaskTool 等外部调用）

**私有方法：**
- `loadAllSessions()`: 从 workspace/{agentId}/sessions/ 加载所有会话
- `loadMetadata(sessionId)`: 从 metadata.json 加载 Session
- `resolveAgentId(sessionId)`: 从 sessionId 解析出 agentId
- `syncPersistentFields(source, target)`: 将磁盘上的持久化字段同步到内存 Session（保留瞬态字段）

**文件结构：**
```
~/.autiva/workspace/{agentId}/sessions/{sessionId}/
├── metadata.json    ← 唯一状态源（Session 序列化）
└── messages.jsonl   ← 消息持久化
```

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
- 单例模式：FileSystemSessionManager 为 Spring Bean
- 懒加载模式：启动时从 metadata.json 加载元数据，点击会话时通过 activate() 激活
- 观察者模式：通过 EventBus（静态 inBox/outBox，基于 AbstractEvent）实现消息传递
- 策略模式：Session 持有 Agent 引用并委托执行

## 注意事项
1. 会话 ID 格式：`{agentId}-{type}-{source}-{userId}-{timestamp}`
2. messages 字段不参与 JSON 序列化（@JsonIgnore）
3. 消息持久化到 workspace/{agentId}/sessions/{sessionId}/messages.jsonl
4. 使用 ConcurrentHashMap 保证线程安全
5. Session 是唯一的持久化格式，序列化为 metadata.json
6. clear() 会清空内存消息列表和磁盘文件
7. delete() 会先停止消息循环再删除
8. currentContextLength 由 UsageAdvisor 从模型响应 Usage 中提取并维护（语义为 token 数）
9. 历史消息通过 MessageChatMemoryAdvisor + CompactChatMemory 自动注入
10. MemoryEvent（CONTEXT_COMPACT/SESSION_END）通过 Schedulers.boundedElastic() 异步处理，不阻塞对话流
11. 子智能体采用 Fresh 模式（TaskTool.createFreshSession），不继承父会话消息，不再使用 fork
