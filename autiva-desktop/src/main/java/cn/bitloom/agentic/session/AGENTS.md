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
- `contextInjected`: 标记动态上下文是否已注入到当前对话
- `lastMemoryHash`: memory.md 内容的哈希值，用于检测变化

**方法：**
- `isStop()`: 判断是否已停止

### SessionRunner
Session 编排器，负责主会话的消息循环和记忆事件处理。对齐 netInsight 的 SessionRunner 设计，使用 TurnBufferedChatMemory 管理本轮缓冲与批量持久化。每个 SessionRunner 绑定一个 Session 实例和一个 per-session chatMemory 实例。同时供子智能体场景使用（通过 InMemorySessionManager.activate 创建），子智能体场景下使用 resultFuture 同步等待结果。

**核心字段：**
- `session`: 绑定的 Session 实例
- `agent`: Agent 实例（per-session 构建，由 SessionManager.activate() 注入）
- `memoryManager`: MemoryManager 实例（主会话由 FileSystemSessionManager.activate() 注入；子会话传 null）
- `chatMemory`: per-session TurnBufferedChatMemory（本轮缓冲 + flush 批量持久化）
- `sessionManager`: ISessionManager（解耦具体类型，主会话传 FileSystemSessionManager，子会话传 InMemorySessionManager）
- `subscription`: Disposable 订阅句柄（用于 stop）
- `resultFuture`: CompletableFuture\<String\>（可选，子智能体场景用于同步等待本轮结果；主智能体场景为 null）

**核心方法：**
- `start()`: 订阅 EventBus.inBoxFlux()，过滤当前会话事件，`publishOn(Schedulers.boundedElastic())` 切换到弹性线程池执行（避免占用 event loop 线程导致死锁），按事件类型分类处理：
  - `MessageEvent.USER` → 设置 currentMessageId，串行调用 Agent 执行对话（concatMap），构造 RuntimeContext 时从 session 读取 reviewDiff/projectPath 传递到 params（供 WriteTool/EditTool/TaskTool 使用），本轮结束后 safeFlush() 批量持久化；若 resultFuture != null 则完成 future（子智能体场景）
  - `A2UIActionEvent` → 构造用户消息让 Agent 继续处理 A2UI 用户交互回流，本轮结束后 safeFlush()
  - `MemoryEvent` → 异步处理（`Schedulers.boundedElastic()`），不阻塞对话流
- `safeFlush()`: 安全调用 chatMemory.flush()（异常路径也调用，避免丢失本轮消息）
- `stop()`: 取消订阅
- `isStopped()`: 判断 subscription 是否为空或已 dispose（供 activate 幂等检查）
- `setResultFuture(future)` / `getResultFuture()`: 子智能体场景设置/获取 resultFuture（getResultFuture 在 resultFuture 为 null 时 lazy 创建）
- `handleMemoryEvent(event)`: 处理 CONTEXT_COMPACT 事件，调用 handleContextCompact
- `handleContextCompact(memoryManager)`: 只压缩文件历史消息（chatMemory.getHistoryFromFile()），调用 `memoryManager.compact()` 生成摘要，推进 memoryCursor 到 chatMemory.countFileMessages()，重置 currentContextLength，通过 sessionManager.persistSession() 持久化 Session

### ISessionManager
Session 管理器统一接口。不同的实现提供不同的存储策略：
- `FileSystemSessionManager`：磁盘持久化，用于主会话
- `InMemorySessionManager`：纯内存，用于子智能体会话

**方法：**
- `create(agentId, parentSessionId, type, respType, model)`: 创建新的 Session
- `getById(sessionId)`: 根据 ID 获取 Session
- `remove(sessionId)`: 移除 Session
- `store(sessionId, messages)`: 存储消息到 Session
- `activate(sessionId)`: 激活会话（幂等：按 sessionId 加锁，已激活则跳过；per-session 创建 ChatMemory + Agent + SessionRunner，启动消息循环。FileSystemSessionManager 从磁盘加载状态 + buildAgent（主智能体），InMemorySessionManager 纯内存 + buildAgent（子智能体））
- `persistSession(session)`: 持久化 Session 元数据（FileSystemSessionManager 写入 metadata.json，InMemorySessionManager 为 no-op）

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
主会话管理器，Spring @Component，实现 ISessionManager 接口，支持持久化和消息存储。Session 是唯一的持久化格式。对齐 netInsight 设计，per-session 构建 Agent + FileChatMemory（不再缓存 Agent 实例）。通过 SessionRunner 管理主会话的消息循环。

**核心字段：**
- `definitionManager`: AgentDefinitionManager — 智能体定义管理
- `modelFactory`: ModelFactory — 模型工厂
- `toolkit`: Toolkit — 工具容器
- `memoryManager`: MemoryManager — 记忆管理器
- `skillManager`: SkillManager — 技能管理器（计算技能描述传给 ProactiveContextAdvisor）
- `sessions`: Map\<String, Session\> 会话内存缓存（LRU 策略，保留最近 5 个，超出时停止 SessionRunner + 清理消息）
- `runners`: Map\<String, SessionRunner\> SessionRunner 实例缓存

**核心方法：**
- `init()`: @PostConstruct，从 workspace/{agentId}/sessions/ 加载所有会话
- `create(agentId, parentSessionId, type, respType, model)`: 创建新的桌面端 Session，走完整流程：创建 → 持久化 → 注册 → 激活
- `activate(sessionId)`: 激活会话（幂等设计：按 sessionId.intern() 加锁，已激活则直接返回，不重复创建 SessionRunner；未激活则从磁盘加载最新状态、创建 per-session FileChatMemory、per-session 构建 Agent、创建 SessionRunner 注入 Agent/MemoryManager/ChatMemory/this、启动消息循环。历史消息不再加载到内存，由 FileChatMemory.get() 从 events.jsonl 按需读取）
- `buildAgent(agentId, chatMemory)`: per-session 构建 Agent 实例（对齐 netInsight，不再缓存 Agent，默认开启 memory + compact，计算 skillDescriptions/subagentDescriptions/memoryFilePath 传给 ProactiveContextAdvisor）
- `storeEvents(sessionId, events)`: 持久化事件到 events.jsonl（只写入 persist=true 的事件，通过 @JsonTypeInfo 自动写入 eventType 字段用于多态反序列化）
- `loadEvents(sessionId, offset, count)`: 从 events.jsonl 加载指定行号范围的事件（通过 eventType 字段由 Jackson 多态反序列化分发到正确子类）
- `loadEventsAsMessages(sessionId, offset, count)`: 从 events.jsonl 加载 MessageEvent 并转为 Spring AI Message（供 LLM 上下文加载）
- `countEvents(sessionId)`: 统计 events.jsonl 总行数
- `removeLastEventLine(sessionId)`: 删除 events.jsonl 最后一行（孤儿工具调用清理用）
- `stopSession(sessionId)`: 通过 SessionRunner 停止会话的消息处理循环
- `getById(sessionId)`: 获取会话
- `getDesktopSessions()`: 获取所有桌面端 session（同步块迭代 synchronizedMap）
- `store(sessionId, messages)`: 将消息转为事件（persist=true）持久化到 events.jsonl，发布 ToolResponseMessage 到 outBox（TOOL_CALLS 由 SessionRunner 发布）
- `clear(sessionId)`: 清空 events.jsonl
- `delete(sessionId)`: 删除会话（通过 SessionRunner 停止消息循环）
- `remove(sessionId)`: 实现 ISessionManager 接口，委托给 delete
- `updateSession(sessionId, Consumer<Session>)`: 局部更新 Session
- `persistSession(Session)`: 序列化 Session 到 metadata.json

**文件结构：**
```
~/.autiva/workspace/{agentId}/sessions/{sessionId}/
├── metadata.json    ← 唯一状态源（Session 序列化）
└── events.jsonl     ← 事件持久化（每行含 eventType 字段用于 Jackson 多态反序列化）
```

### InMemorySessionManager
子会话管理器，Spring @Component，实现 ISessionManager 接口，纯内存存储。供子智能体会话使用。

**与 FileSystemSessionManager 的区别：**
- 纯内存存储，不持久化到磁盘
- 启动 SessionRunner 消息循环（与主会话对齐，子智能体也走 EventBus 模式，由 TaskTool 通过 EventBus.publishIn 投递任务）
- 不注入 MemoryManager（子智能体不需要记忆整理，传 null）
- per-session 创建 InMemoryChatMemory + Agent（对齐 FileSystemSessionManager 的 per-session 模式，不共享 ChatMemory）
- buildAgent 简化：不注入 VerificationHook/GeneInjector/TraceHook/SkillManager，不开启 compact

**注入依赖（构造函数）：**
- `definitionManager`: AgentDefinitionManager — 智能体定义管理（getDefinition 获取子智能体定义）
- `modelFactory`: ModelFactory — 模型工厂
- `toolkit`: Toolkit — 工具容器（@Lazy 打破与 Toolkit 的循环依赖：Toolkit 注入本类用于 TaskTool 构建，本类注入 Toolkit 用于 buildAgent）

**核心字段：**
- `sessions`: ConcurrentHashMap\<String, Session\> — 子 Session 内存缓存
- `messageStore`: ConcurrentHashMap\<String, List\<Message\>\> — 自管理消息存储（Session 不再缓存 messages）
- `runners`: Map\<String, SessionRunner\> — per-subSession 的 SessionRunner 实例缓存
- `chatMemories`: Map\<String, InMemoryChatMemory\> — per-session InMemoryChatMemory 实例缓存（每个子智能体独立，不共享）

**核心方法：**
- `create(agentId, parentSessionId, type, respType, model)`: 创建子 Session（ID 格式：`sub-{agentId}-{timestamp}`），设置 sessionType=SUB，parentId=parentSessionId
- `getById(sessionId)`: 获取子 Session
- `remove(sessionId)`: 移除子 Session 并停止 SessionRunner、清理 chatMemory 和 messageStore
- `activate(sessionId)`: 激活子会话（幂等设计：按 sessionId.intern() 加锁，已激活则跳过；未激活则 per-session 创建 InMemoryChatMemory + buildAgent + SessionRunner，启动消息循环。Agent 在内部创建，不由外部传入）
- `getRunner(sessionId)`: 获取指定子会话的 SessionRunner（供 TaskTool 拿 resultFuture 同步等待结果）
- `buildAgent(agentId, chatMemory)`: per-session 构建子智能体 Agent（getDefinition + 校验 kind==SUBAGENT + Agent.builder，注册 per-session chatMemory）
- `persistSession(session)`: no-op（子会话不持久化，仅为实现 ISessionManager 接口）
- `store(sessionId, messages)`: 追加消息到 messageStore（纯内存，不写磁盘）
- `getMessages(sessionId)`: 获取 messageStore 中的消息列表
- `clearMessages(sessionId)`: 清空 messageStore 中的消息

### InMemoryChatMemory
纯内存 ChatMemory 实现，实现 TurnBufferedChatMemory 接口，供子智能体 Session 使用。对齐 netInsight 的 InMemoryChatMemory 设计。**per-session 实例**（非 Spring Bean，由 InMemorySessionManager.activate() 为每个子智能体独立 new 创建，不共享）。

**与 FileChatMemory 的区别：**
- 不依赖磁盘持久化
- 不做游标感知（子智能体不需要上下文压缩）
- 不做孤儿消息检查（子智能体生命周期短）
- 内部维护 messageCache（ConcurrentHashMap）替代委托 InMemorySessionManager
- flush/getHistoryFromFile/countFileMessages 为 no-op/空（仅为兼容 TurnBufferedChatMemory 协议，由 SessionRunner.safeFlush 调用）

**核心方法：**
- `add(sessionId, messages)`: 追加消息到 messageCache，同时广播 ToolCalls/ToolResponse 事件
- `get(sessionId)`: 从 messageCache 返回全部消息（无游标）
- `clear(sessionId)`: 清空 messageCache 中的消息
- `setCurrentMessageId(messageId)`: 设置当前轮次 messageId（由 SessionRunner 调用，子智能体场景下消息已实时广播，此字段仅用于广播时填充 event.messageId）
- `flush()`: no-op（子智能体消息已实时广播且纯内存存储，无需批量持久化）
- `getHistoryFromFile()`: 返回空列表（子智能体无文件历史）
- `countFileMessages()`: 返回 0（子智能体无文件历史）

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
- per-session 实例模式：FileChatMemory 和 InMemoryChatMemory 非 Spring Bean，由 SessionManager 为每个 Session 手动创建（对齐 netInsight）
- 懒加载模式：启动时从 metadata.json 加载元数据，点击会话时通过 activate() 激活
- 观察者模式：通过 EventBus（静态 inBox/outBox，基于 AbstractEvent）实现消息传递
- 策略模式：ISessionManager 接口统一管理不同存储策略的 Session
- 实体与编排分离：Session 为纯实体，SessionRunner 负责编排逻辑

## 注意事项
1. 主会话 ID 格式：`{agentId}-{type}-{source}-{userId}-{timestamp}`
2. 子会话 ID 格式：`sub-{agentId}-{timestamp}`
3. Session 不再缓存 messages 列表，LLM 上下文由 FileChatMemory 从 events.jsonl 按需读取 + 本轮缓冲区合并
4. 主会话事件持久化到 workspace/{agentId}/sessions/{sessionId}/events.jsonl
5. 子会话消息纯内存存储（InMemorySessionManager 自管理 messageStore），不持久化
6. 使用 ConcurrentHashMap 保证线程安全
7. Session 是唯一的持久化格式，序列化为 metadata.json
8. clear() 会清空 events.jsonl 文件
9. delete() 会先通过 SessionRunner 停止消息循环再删除
10. currentContextLength 由 UsageAdvisor 从模型响应 Usage 中提取并维护（语义为 token 数）
11. 历史消息通过 MessageChatMemoryAdvisor + FileChatMemory 自动注入
12. MemoryEvent（CONTEXT_COMPACT）通过 Schedulers.boundedElastic() 异步处理，不阻塞对话流
13. 子智能体通过 InMemorySessionManager 创建子 Session，拥有 ChatMemory 支持对话历史
14. 子 Session 通过 SessionRunner 启动 EventBus 消息循环（与主会话对齐），TaskTool 通过 EventBus.publishIn 投递任务，通过 SessionRunner.getResultFuture().get() 同步等待结果（5 分钟超时）
15. 消息发布统一使用 `EventBus.publishIn()`，不再通过 `session.publish()`
16. 消息订阅统一使用 `EventBus.outBoxFlux()`，不再通过 `session.subscribe()`
17. 旧版 CompactChatMemory 已移除，由 per-session FileChatMemory 替代
18. Agent 不再缓存（移除 agentCache），每次 activate() 时 per-session 构建
