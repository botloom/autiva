# Agent 包

## 概述
本包实现了智能体核心系统，采用**Agent 接口 + 策略模式**架构。Agent 是无状态策略接口，被 Session 持有和委托执行 LLM 调用。主智能体拥有独立身份、工作空间和会话，子智能体通过 Task 工具按需委派。系统提示词设计参考了 OpenClaw 的分层架构，将硬编码的系统级指令与用户可编辑的工作目录文件分离。

1. **主智能体（MAIN）**：长期记忆，有独立工作目录，作为调度者和协调者，**没有文件读写和Shell执行能力**
2. **子智能体（SUBAGENT）**：拥有对话记忆（通过 ChatMemory），用于完成子任务，配置存放在 workspace/subagents/ 目录。其中 Code 子智能体是唯一拥有文件操作和Shell执行能力的代理

智能体通过 EventBus 的 Inbox/Outbox 双通道进行消息收发。Session 作为 Actor 订阅 inBox，委托 Agent 执行，将响应发布到 outBox。**消息通过 MessageChannel 进行通道级隔离**，同一会话内不同通道拥有独立的对话历史和 conversationId。

## 目录结构

```
~/.autiva/workspace/
  MAIN/                    # 主智能体工作目录
    IDENTITY.md            # 身份定义（自我发现 + 调度者角色 + 安全边界）
    SOUL.md                # 核心信条 + 边界 + 风格 + 连续性 + 子智能体策略 + 任务管理
    MEMORY.md              # 长期记忆（含记忆类型和规则）
    USER.md                # 关于用户（关系建立，非档案）
    TOOLS.md               # 工具备忘录（环境特定配置）
    BOOTSTRAP.md           # 首次启动引导（完成后删除）
  subagents/               # 子智能体配置目录
    CODE_SUBAGENT.md       # 编码子智能体（唯一拥有文件读写和Shell执行能力）
    GENERAL_PURPOSE_SUBAGENT.md
    EXPLORE_SUBAGENT.md
    PLAN_SUBAGENT.md
    BASH_SUBAGENT.md
```

## 核心类

### AgentManager
智能体运行时管理器，负责智能体注册、会话绑定、配置 CRUD 和系统提示词构建。初始化逻辑已迁移至 AppBootstrap。

**注册方法：**
- `init()`: @PostConstruct，注册所有主智能体和子智能体到 agents Map
- `registerAgents()`: 注册主智能体（遍历 MAIN 类型的 AgentIdentityEnum）和子智能体（从目录加载）
- `reloadSubagents()`: 重新加载子智能体配置

**系统提示词方法：**
- `buildSystemPrompt(identity, skillManager)`: 构建主智能体的完整系统提示词（安全准则 + 工具调用风格 + 记忆系统 + 心跳 + 运行环境 + 技能描述 + 工作空间上下文）
- `getDescription(agentName)`: 获取主智能体描述（读取工作目录下的.md文件）

**主智能体方法：**
- `listMainAgents()`: 列出所有主智能体
- `loadAgentFolders()`: 加载主智能体文件夹列表（排除 subagents 目录）

**子智能体方法：**
- `listSubagents()`: 列出所有子智能体
- `loadSubagentFolders()`: 加载子智能体配置列表（解析 YAML frontmatter）
- `getSubagentContent(name)`: 读取子智能体配置内容
- `saveSubagentConfig(name, content)`: 保存子智能体配置
- `createSubagentConfig(name, description, content)`: 创建新的子智能体配置
- `deleteSubagentConfig(name)`: 删除子智能体配置

**通用方法：**
- `bindSession(agentName, sessionId)`: 绑定智能体与会话
- `getSessionByAgent(agentName)`: 获取智能体绑定的会话ID
- `listAgents()`: 列出所有智能体（主智能体 + 子智能体），内部委托 listAgentsByType(null)
- `listMainAgents()`: 列出主智能体，内部委托 listAgentsByType(MAIN)
- `listSubagents()`: 列出子智能体，内部委托 listAgentsByType(SUBAGENT)
- `exists(name)`: 检查智能体是否存在
- `count()`: 获取智能体总数
- `getAgentType(name)`: 获取智能体类型（返回 AgentIdentityEnum.AgentCategory）

**内部类：**
- `AgentConfig`: 智能体配置 record（name, type[AgentCategory], path, description）
- `AgentInfo`: 智能体信息 record (name, type, sessionId)
- `AgentFolder`: 主智能体文件夹（name, path, files）
- `AgentFile`: 主智能体文件（path, displayName）
- `SubagentFolder`: 子智能体配置 record（name, description, path）

### MainAgent
主智能体策略，作为调度者和协调者处理用户对话。实现 `Agent` 接口，被 Session 委托执行。

**Spring 注解：** `@Component` + `@Lazy(false)`（必须立即初始化，因为 @PostConstruct 中订阅 EventBus.inBox）

**核心设计：主智能体没有文件读写和Shell执行能力**，所有涉及文件操作、代码编写、命令执行的工作必须通过 Task 工具委派给子智能体。

**功能：**
- 被 Session 委托执行 LLM 调用（`run(Session, MessageEvent)`）
- 支持流式响应和非流式响应两种模式
- 支持通过 Session.isStop() 中断流式响应
- 系统提示词委托给 AgentManager.buildSystemPrompt() 构建

**事件处理：**
- `getHandledEventTypes()`: 返回 MESSAGE、MEMORY_CONSOLIDATE、JOURNAL
- `handleAgentEvent()`: 处理 MEMORY_CONSOLIDATE 和 JOURNAL 事件
  - MEMORY_CONSOLIDATE：从 Session 的 **USER 通道**读取 [cursor, size) 范围消息，使用 **MEMORY 通道**的 conversationId 调用 LLM 总结，写入记忆文件，更新 cursor
  - JOURNAL：从事件消息中提取摘要，调用 JournalManager 写入日记，更新 cursor

**工具集（仅调度相关工具，无文件/Shell能力）：**
- `WebFetchTool`: 网页获取（HTML转Markdown）
- `WebSearchTool`: 网页搜索（Bocha搜索引擎）
- `CommandTools`: 命令执行（Command + Process 双工具模型）
- `AskUserQuestionTool`: 向用户提问
- `TodoWriteTool`: 任务列表管理
- `CronTool`: 定时任务管理
- `TaskTool/TaskOutputTool/SessionQueryTool`: 子代理任务工具
- `EvolveQueryTool`: 进化查询工具（查询基因库、胶囊库、推荐基因）
- `EvolveApplyTool`: 进化应用工具（应用基因或胶囊获取策略指导）
- `NotifyTool`: IM 通知工具（通过钉钉或微信主动通知用户）
- `SkillManager.buildToolCallback()`: 技能工具
- `AsyncMcpToolCallbackProvider`: MCP工具

**子智能体工具配置：**
- GenericSubagentType.Builder 中配置了 `deployTool`（部署工具）和 `projectManagementTool`（项目管理工具）
- ProjectManagementTool 允许 Code 子智能体与后端项目管理系统交互（获取需求/Bug、提交设计方案/测试用例、更新状态）

### EvolverAgent
进化守护者主智能体策略，负责系统的自我演化。实现 `Agent` 接口，被 Session 委托执行。

**Spring 注解：** `@Component` + `@Lazy(false)`（必须立即初始化，因为 @PostConstruct 中订阅 EventBus.inBox）

**核心设计：独立运行，不处理用户日常对话**

**功能：**
- 被 Session 委托执行 LLM 调用（`run(Session, MessageEvent)`）
- 执行进化周期（信号提取→基因选择→策略组装→固化）
- 管理基因库（启用/禁用/删除基因和胶囊）
- 调整进化策略预设

**事件处理：**
- `getHandledEventTypes()`: 返回 MESSAGE、EVOLVE
- `handleAgentEvent()`: 处理 EVOLVE 事件
  - EVOLVE：从事件消息中提取进化意图，使用 **EVOLVE 通道**的 conversationId 调用 LLM 执行进化周期

**工具集：**
- `WebFetchTool`: 网页获取
- `WebSearchTool`: 网页搜索
- `AskUserQuestionTool`: 向用户提问
- `TodoWriteTool`: 任务列表管理
- `EvolveQueryTool`: 进化查询
- `EvolveCycleTool`: 执行进化周期
- `EvolveGeneManageTool`: 基因管理（启用/禁用/删除）
- `EvolveConfigTool`: 进化引擎配置

### Agent
智能体策略接口，定义 Session 委托 Agent 执行的契约。遵循依赖倒置原则，Session 依赖抽象而非具体实现。

**接口方法：**
- `run(Session session, MessageEvent event)`: 核心执行方法，接收 Session 上下文和消息事件，返回响应流
- `getIdentity()`: 获取智能体身份标识（AgentIdentityEnum），用于注册表映射

**设计意图：**
- Agent 是无状态策略，可被多个 Session 安全共享
- 新增 Agent 实现时无需修改 SessionManager（开闭原则）
- Session 通过 Agent 接口与具体实现解耦

### AbstractAgent
抽象智能体基类，实现 `Agent` 接口，提供通用的 LLM 调用逻辑和事件分发机制。

**核心方法：**
- `run()`: 订阅 inBox 事件流，按 sessionId 前缀过滤后进行事件分发
- `getTools()`: 构建工具集（protected 抽象方法，子类实现以自定义工具）
- `getSystemPrompt()`: 获取系统提示词（protected 抽象方法）
- `getIdentity()`: 获取智能体身份（public 抽象方法，实现 Agent 接口）

**事件分发方法：**
- `canHandle(EventType)`: 判断当前智能体是否能处理指定事件类型
- `getHandledEventTypes()`: 返回当前智能体可处理的事件类型集合（默认只处理 MESSAGE）
- `handleAgentEvent(EventType, MessageEvent)`: 处理非 MESSAGE 类型的业务事件（默认空实现，子类按需覆写）
- `handleMessageEvent(MessageEvent)`: 处理 MESSAGE 类型事件（从原 run() 方法中提取的消息处理逻辑）

**事件分发流程：**
```
inBox 事件 → run() 过滤 sessionId 前缀
    → 读取 eventType（默认 MESSAGE）
    → canHandle() 检查
    → MESSAGE → handleMessageEvent()（现有流程）
    → 其他类型 → handleAgentEvent()（子类覆写）
```

**LLM 调用逻辑（handleMessageEvent 方法）：**
- 从 MessageEvent 获取 MessageChannel，构建 channel-aware conversationId：`sessionId + "#" + channel.name()`
- STREAM 模式：流式调用 → 逐条收集 AssistantMessage → 仅当 `channel.shouldPublishToOutBox()` 为 true 时发布到 outBox → `doFinally` 清理 busy/stop 标志，仅在 ON_COMPLETE 时调用 `lifecycleHook.onSessionEnd` → `onErrorResume` 吞掉异常（如网络不稳定连接 LLM 失败），返回空 Flux 防止错误传播终止整个 inBox 订阅
- BLOCK 模式：try-catch 包裹阻塞调用 → 仅当 `channel.shouldPublishToOutBox()` 为 true 时发布到 outBox → 调用 `lifecycleHook.onSessionEnd(sessionId, messages, channel)` → finally 块清理 busy/stop 标志

**错误处理与容错设计：**
- `run()` 的 `subscribe()` 注册了 error handler 作为兜底，防止未捕获的异常终止整个 inBox 订阅
- STREAM 路径使用 `onErrorResume` 在操作符链中吞掉错误，与 `doFinally` 配合确保 busy/stop 标志总是被清理
- BLOCK 路径使用 try-catch-finally 确保异常被捕获且标志位在 finally 中清理

**channel-aware conversationId 设计：**
- 格式：`{sessionId}#{channel.name()}`，例如 `MAIN-DM-desktopApp-bitloom#USER`、`MAIN-DM-desktopApp-bitloom#SYSTEM`
- ConpactChatMemory 解析 conversationId 时，以 `#` 为分隔符拆分出 sessionId 和 channel
- 不同通道拥有独立的对话历史，互不干扰
- 未知通道名回退为 USER 通道

**outBox 发布策略：**
- 仅 USER 通道的消息会发布到 outBox（`channel.shouldPublishToOutBox()` 返回 true）
- SYSTEM、MEMORY、JOURNAL、EVOLVE 通道的响应不推送给用户，避免系统级交互干扰用户界面

**依赖注入（@Resource）：**
- `chatClientBuilderFactory`: ChatClient 构建工厂
- `skillManager`: 技能管理器
- `agentManager`: 智能体管理器（构建系统提示词）
- `sessionManager`: 会话管理器（TaskTool 需要）
- `toolUIBridge`: UI 桥接（工具交互）
- `cronManager`: 定时任务管理器
- `configManager`: 配置管理器
- `mcpToolCallbackProvider`: MCP 工具提供者
- `lifecycleHook`: 生命周期钩子

### AgentLifecycleHook
智能体会话生命周期钩子，在会话关键节点自动执行记忆操作。

**Spring 注解：** `@Component`

**核心方法：**
- `onSessionStart(String sessionId)`: 会话开始时触发
- `onSessionEnd(String sessionId, List<AssistantMessage> messages, MessageChannel channel)`: 会话结束时触发，接收 MessageChannel 参数
  - 仅当 `channel == MessageChannel.USER` 时执行日记和记忆整理逻辑
  - 非 USER 通道（SYSTEM/MEMORY/JOURNAL/EVOLVE）的会话结束不触发日记和记忆整理，避免系统级交互产生不必要的副作用

**事件发布（仅 USER 通道触发）：**
- `publishJournalEvent(sessionId, summary)`: 会话结束时发布 JOURNAL 事件到 inBox，使用 `EventType.JOURNAL` + `MessageChannel.JOURNAL`
- `publishMemoryConsolidateEvent(sessionId)`: 会话结束时检查 USER 通道未处理消息数量，超过阈值（10条）时发布 MEMORY_CONSOLIDATE 事件到 inBox，使用 `EventType.MEMORY_CONSOLIDATE` + `MessageChannel.MEMORY`

**设计意图：**
- 通过 MessageChannel 参数过滤，确保只有用户对话才触发日记和记忆整理
- 心跳（SYSTEM 通道）和进化（EVOLVE 通道）等系统级交互不会污染日记和记忆系统

### 枚举类
- `AgentIdentityEnum`: 智能体身份标识枚举，包含所有主智能体和子智能体类型
  - `MAIN(AgentCategory.MAIN)`: 主智能体
  - `EVOLVER(AgentCategory.MAIN)`: 进化守护者主智能体
  - `GENERIC(AgentCategory.SUBAGENT)`: 通用子智能体
  - `DOCTOR(AgentCategory.SUBAGENT)`: 系统医生子智能体
  - `A2A(AgentCategory.SUBAGENT)`: A2A协议子智能体
  - `AgentCategory`: 内部枚举 (MAIN, SUBAGENT)
  - `getCategory()`: 获取智能体类别
  - `isMain()` / `isSubagent()`: 便捷判断方法

## 消息流程

```
用户消息 -> EventBus.inBoxPublish(sessionId, message)  [inBox, 默认 USER 通道]
                    │
                    ▼
            AbstractAgent.run()     ← 订阅 inBox，按 identity 前缀过滤
                    │
                    ├── 读取 eventType（默认 MESSAGE）
                    ├── 读取 messageChannel（默认 USER）
                    ├── canHandle() 检查
                    │
                    ├── MESSAGE → handleMessageEvent()
                    │       │
                    │       ├── 构建 channel-aware conversationId: sessionId + "#" + channel.name()
                    │       ├── LLM 调用（流式/阻塞）
                    │       ├── 仅 channel.shouldPublishToOutBox() == true 时发布到 outBox
                    │       └── lifecycleHook.onSessionEnd(sessionId, messages, channel)
                    │
                    └── 其他类型 → handleAgentEvent()（子类覆写）
                            │
                            ├── MEMORY_CONSOLIDATE → MainAgent.handleMemoryConsolidate()
                            │       └── 读取 USER 通道消息，使用 MEMORY 通道 conversationId
                            ├── JOURNAL → MainAgent.handleJournal()
                            │       └── 写入日记，使用 JOURNAL 通道
                            └── EVOLVE → EvolverAgent.handleEvolve()
                                    └── 使用 EVOLVE 通道 conversationId
```

**通道路由全景：**
```
发布方                              通道/事件类型              MessageChannel     conversationId
────────────────────────────────────────────────────────────────────────────────────────────────
用户消息                    MESSAGE              USER               {sessionId}#USER
心跳消息                    MESSAGE              SYSTEM             {sessionId}#SYSTEM
AgentLifecycleHook          MEMORY_CONSOLIDATE   MEMORY             {sessionId}#MEMORY
AgentLifecycleHook          JOURNAL              JOURNAL            {sessionId}#JOURNAL
HeartbeatRunner(EVOLVER)    EVOLVE               EVOLVE             {sessionId}#EVOLVE
```

**子智能体委派流程：**
```
Session(Agent=MainAgent)
        │
        ├── Task(subagent_type="Code")    → fork子会话 → Code子智能体
        ├── Task(subagent_type="Explore")  → fork子会话 → Explore子智能体
        ├── Task(subagent_type="Plan")     → fork子会话 → Plan子智能体
        ├── Task(subagent_type="Bash")     → fork子会话 → Bash子智能体
        ├── Task(subagent_type="Doctor")   → fork子会话 → Doctor子智能体（系统配置管理）
```

## Session 模型

主智能体拥有独立会话，Session ID 格式：`{agentId}-{type}-{source}-{target}`

```
MAIN-DM-desktopApp-bitloom      # 主助手会话
MAIN-DM-wechat-user123          # 微信渠道主助手会话
EVOLVER-SYSTEM-internal-internal # 进化守护者会话
```

子智能体采用 fork 模型：
```
MAIN-DM-desktopApp-bitloom
  ├── fork → 子会话_0 (Code, parentId=主会话)
  ├── fork → 子会话_1 (Explore, parentId=主会话)
  └── fork → 子会话_2 (Bash, parentId=主会话)
```

**消息存储按通道隔离：** 每个会话的消息按 MessageChannel 分别存储在 `channelMessages`（EnumMap<MessageChannel, List<Message>>）中，持久化为独立的 `{channel}.jsonl` 文件。

## 设计模式
- **策略模式**：Agent 是无状态策略接口，Session 持有 Agent 引用并委托执行，新增 Agent 无需修改 SessionManager
- **依赖倒置原则**：Session 依赖 Agent 接口而非 AbstractAgent 具体类
- **开闭原则**：新增 Agent 实现自动注册到 SessionManager 的 agentRegistry，无需修改已有代码
- 观察者模式：通过 EventBus 订阅消息
- 响应式编程：基于 Project Reactor
- Builder模式：所有工具使用 Builder 创建，不依赖 Spring 管理
- 调度者-执行者模式：主智能体作为调度者，子智能体作为执行者
- 多主智能体模式：参考 OpenClaw，每个主智能体拥有独立工作空间、会话和身份
- 初始化分离模式：AppBootstrap 负责初始化，AgentManager 负责运行时管理
- 事件类型标记模式：通过 eventType 字段在同一 inBox 通道中区分不同业务事件类型
- **通道隔离模式**：通过 MessageChannel 在同一会话内隔离不同业务的消息历史和 conversationId，替代了原有的独立系统会话方案
- 游标模式：memoryCursor/journalCursor 实现断点续处理

## 心跳机制

灵感来源于 OpenClaw 的心跳设计。HeartbeatRunner 定期向**用户会话**发送心跳消息（使用 SYSTEM 通道），驱动主智能体进行主动检查和自省。

**核心组件：**
- **HeartbeatRunner**：定时任务执行器，按固定间隔向用户会话发送心跳消息（EventType.MESSAGE + MessageChannel.SYSTEM）
- **ProactiveContextAdvisor**：在心跳消息处理时，将 `HEARTBEAT.md` 检查清单注入到 Advisor 链的上下文中，引导智能体按清单逐项检查
- **HEARTBEAT.md**：工作目录下的检查清单文件，定义了心跳时需要检查的事项

**HEARTBEAT_OK 响应契约：**
- 当智能体完成心跳检查且无需采取行动时，应回复 `HEARTBEAT_OK`
- 前端/消息层识别到 `HEARTBEAT_OK` 后会抑制输出，避免向用户展示无意义的心跳响应

**流程：**
```
HeartbeatRunner 定时触发
        │
        ▼
EventBus.inBoxPublish(userSessionId, heartbeat消息, EventType.MESSAGE, MessageChannel.SYSTEM)
        │
        ▼
MainAgent 接收消息（conversationId = userSessionId#SYSTEM）
        │
        ▼
ProactiveContextAdvisor 注入 HEARTBEAT.md 检查清单
        │
        ▼
智能体按清单检查 → 回复 HEARTBEAT_OK（无需行动时）或执行相应操作
        │
        ▼
SYSTEM 通道 shouldPublishToOutBox() == false，响应不推送到 outBox
lifecycleHook.onSessionEnd() 检测到非 USER 通道，跳过日记/记忆整理
```

**EVOLVER 心跳：**
```
HeartbeatRunner 定时触发
        │
        ▼
EventBus.inBoxPublish(EVOLVER-SYSTEM-internal-internal, evolve消息, EventType.EVOLVE, MessageChannel.EVOLVE)
        │
        ▼
EvolverAgent 接收消息（conversationId = EVOLVER-SYSTEM-internal-internal#EVOLVE）
        │
        ▼
执行进化周期
```

## 忙状态跟踪

EventBus 维护全局的 busy 标志用于跨组件查询，按 sessionId 粒度管理。

**核心机制：**
- **EventBus** 维护每个会话的 busy 标志（per-session busy state），供 HeartbeatRunner 等外部组件查询
- **HeartbeatRunner** 在发送心跳前检查目标会话的 busy 状态，若 busy 则跳过本次心跳
  - 用户会话心跳：直接检查用户会话的 busy 状态
  - EVOLVER 心跳：检查 EVOLVER 会话的 busy 状态

**设计意图：**
- 避免心跳消息打断正在进行的用户对话
- 心跳跳过不会导致遗漏，下一次心跳周期会重新检查

## 空会话处理

Session 在 handleMessage 中增加了 Agent 为 null 的防御性处理。

**行为：**
- 当 Session 关联的 Agent 为 null 时，Session 记录一条警告日志并跳过该消息
- 不抛出异常，不影响其他会话的正常处理

**设计意图：**
- 防止因 Agent 未正确注入或已销毁导致的 NPE
- 在日志中保留可追溯的记录，便于排查会话管理问题

