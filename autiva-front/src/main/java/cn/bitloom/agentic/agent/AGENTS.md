# Agent 包

## 概述
本包实现了智能体核心系统，采用**调度者-执行者**架构。主智能体作为调度者，不直接操作文件和执行命令，而是通过 Task 工具委派给子智能体完成实际工作。

1. **主智能体（MAIN）**：长期记忆，有独立工作目录，作为调度者和协调者，**没有文件读写和Shell执行能力**
2. **子智能体（SUBAGENT）**：拥有对话记忆（通过 ChatMemory），用于完成子任务，配置存放在 workspace/subagents/ 目录。其中 Code 子智能体是唯一拥有文件操作和Shell执行能力的代理

智能体通过 EventBus 的 Inbox/Outbox/Cancel 三通道进行消息收发。

## 目录结构

```
~/.autiva/workspace/
  MAIN/                    # 主智能体工作目录
    IDENTITY.md            # 身份定义（调度者角色 + 安全边界）
    SOUL.md                # 行为准则 + 子智能体使用策略（强制规则）+ 任务管理
    MEMORY.md              # 长期记忆
    USER.md                # 用户偏好
    TOOLS.md               # 工具笔记（项目特定配置）
  DOCTOR/                  # 另一个主智能体
    ...
  subagents/               # 子智能体配置目录
    CODE_SUBAGENT.md       # 编码子智能体（唯一拥有文件读写和Shell执行能力）
    GENERAL_PURPOSE_SUBAGENT.md
    EXPLORE_SUBAGENT.md
    PLAN_SUBAGENT.md
    BASH_SUBAGENT.md
```

## 核心类

### AgentManager
智能体管理器，统一管理主智能体和子智能体的配置、会话绑定关系和工作目录。

**初始化方法：**
- `init()`: 初始化工作目录（主智能体目录 + 子智能体目录 + 默认模板）
- `initAgentWorkspaces()`: 创建主智能体工作目录和默认模板文件（含 IDENTITY.md）
- `initSubagentWorkspace()`: 创建子智能体目录并从 classpath 复制默认配置（含 CODE_SUBAGENT.md）
- `registerAgents()`: 注册所有主智能体和子智能体到 agents Map

**主智能体方法：**
- `listMainAgents()`: 列出所有主智能体
- `getDescription(agentName)`: 获取主智能体描述（读取工作目录下的.md文件）
- `loadAgentFolders()`: 加载主智能体文件夹列表（排除 subagents 目录）

**子智能体方法：**
- `listSubagents()`: 列出所有子智能体
- `loadSubagentFolders()`: 加载子智能体配置列表（解析 YAML frontmatter）
- `getSubagentContent(name)`: 读取子智能体配置内容
- `saveSubagentConfig(name, content)`: 保存子智能体配置
- `createSubagentConfig(name, description, content)`: 创建新的子智能体配置
- `deleteSubagentConfig(name)`: 删除子智能体配置
- `reloadSubagents()`: 重新加载子智能体配置

**通用方法：**
- `bindSession(agentName, sessionId)`: 绑定智能体与会话
- `getSessionByAgent(agentName)`: 获取智能体绑定的会话ID
- `listAgents()`: 列出所有智能体（主智能体 + 子智能体），内部委托 listAgentsByType(null)
- `listMainAgents()`: 列出主智能体，内部委托 listAgentsByType(MAIN)
- `listSubagents()`: 列出子智能体，内部委托 listAgentsByType(SUBAGENT)
- `exists(name)`: 检查智能体是否存在
- `count()`: 获取智能体总数
- `getAgentType(name)`: 获取智能体类型

**内部类：**
- `AgentType`: 智能体类型枚举 (MAIN, SUBAGENT)
- `AgentConfig`: 智能体配置（name, type, path, description）
- `AgentInfo`: 智能体信息 record (name, type, sessionId)
- `AgentFolder`: 主智能体文件夹（name, path, files）
- `AgentFile`: 主智能体文件（path, displayName）
- `SubagentFolder`: 子智能体配置（name, description, path）

### MainAgent
主智能体，作为调度者和协调者处理用户对话。

**核心设计：主智能体没有文件读写和Shell执行能力**，所有涉及文件操作、代码编写、命令执行的工作必须通过 Task 工具委派给子智能体。

**功能：**
- 订阅 EventBus Inbox
- 处理用户请求并通过 Outbox 返回响应
- 支持流式响应和非流式响应两种模式
- 支持通过 EventBus Cancel 通道中断流式响应
- 系统提示词由工作目录中的 .md 文件 + 技能描述 + 运行时信息组成

**系统提示词结构：**
1. 工作目录上下文（IDENTITY.md, MEMORY.md, SOUL.md, TOOLS.md, USER.md 按文件名排序拼接）
2. 运行环境（工作目录路径、当前时间、智能体标识）
3. 可用技能列表（如有）

**工具集（仅调度相关工具，无文件/Shell能力）：**
- `WebFetchTool`: 智能网页获取+AI摘要 — `WebFetchTool.builder(chatClient).build()`
- `AskUserQuestionTool`: 向用户提问 — `AskUserQuestionTool.builder().questionHandler(handler).build()`
- `TodoWriteTool`: 任务列表管理 — `TodoWriteTool.builder().todoEventHandler(handler).build()`
- `CronTool`: 定时任务管理 — `CronTool.builder(cronManager).build()`
- `Task/TaskOutput`: 子代理任务工具 — `taskManager.buildToolCallbacks()`（ToolCallbacks模式）

**注意：** 主智能体不拥有 FileSystemTools、ShellTools、GrepTool、GlobTool，这些能力仅存在于 Code 子智能体中。

**ChatClient 配置：**
- 支持 DeepSeek 和智谱AI 两个模型
- 使用 StTemplateRenderer 模板渲染
- 集成 MessageChatMemoryAdvisor、LoggingAdvisor、ToolCallAdvisor
- 通过 `buildChatClient()` 方法统一构建 ChatClient，参数类型安全（List<Object> tools + Consumer advisorSpec）

**取消机制：**
- 流式响应支持 `takeUntil` 检查 EventBus 取消标志
- 事件处理前检查取消标志，已取消的事件直接跳过
- 完成后自动清理取消标志

### 枚举类
- `AgentIdentityEnum`: 主智能体身份标识 (MAIN, DOCTOR)

## 消息流程

```
用户消息 -> EventBus.inBoxPublish(sessionId, message)
                    │
                    ▼
            MainAgent（调度者）
            （订阅 Inbox，分析任务，委派子智能体）
                    │
                    ├── Task(subagent_type="Code")  → fork子会话 → Code子智能体（文件操作+Shell）
                    ├── Task(subagent_type="Explore") → fork子会话 → Explore子智能体（只读搜索）
                    ├── Task(subagent_type="Plan")   → fork子会话 → Plan子智能体（制定计划）
                    └── Task(subagent_type="Bash")   → fork子会话 → Bash子智能体（Shell命令）
                    │
                    ▼
            子智能体流式输出 → TaskCard 实时展示
                    │
                    ▼
            汇总子智能体结果
                    │
                    ▼
            EventBus.outBoxPublish(sessionId, response)
                    │
                    ▼
            用户接收响应

取消流程：
用户取消 -> EventBus.cancelPublish(sessionId)
                    │
                    ▼
            MainAgent 检查 isCancelled(sessionId)
                    │
                    ├── 流式响应：takeUntil 中断
                    └── 新事件：直接跳过
```

## Session Fork 模型

子智能体采用 fork 模型，类似进程 fork：

```
主会话 (MAIN-DM-wechat-user123)
  ├── fork → 子会话_0 (Code, parentId=主会话)
  ├── fork → 子会话_1 (Explore, parentId=主会话)
  └── fork → 子会话_2 (Bash, parentId=主会话)
```

**Fork 规则：**
- 每次调用 Task 工具时，TaskManager 自动 fork 子会话
- 子会话 ID 格式：`{parentSessionId}_{自增序号}`
- 子会话的 `parentId` 指向主会话
- 子会话的 `target` 字段记录子智能体类型
- 子会话固定为 STREAM 响应类型
- 同一会话中同一类型的子智能体共享 ChatMemory 对话历史

**TaskCard UI 卡片：**
- 子智能体执行时自动创建 TaskCard，实时展示操作过程
- 卡片头部显示：⚡图标 + 子智能体类型 + 任务描述 + 运行状态
- 卡片内容区：流式输出子智能体的文本内容（等宽字体，可滚动）
- 状态标签：运行中（蓝色）→ 已完成（绿色）/ 失败（红色）
- 点击头部可展开/折叠输出内容

## 子智能体加载流程

```
AgentManager.init()
    │
    ├── initAgentWorkspaces()     # 创建主智能体目录和默认模板（含 IDENTITY.md）
    ├── initSubagentWorkspace()   # 创建 subagents/ 目录，复制默认 .md 配置（含 CODE_SUBAGENT.md）
    └── registerAgents()          # 注册所有智能体到 agents Map
            │
            ▼
MainAgent.init()
    │
    └── TaskManager.registerSubagentTypes()
            │
            └── resolveAndIndex()
                    │
                    ├── 清理旧的 file: 协议引用（防重复加载）
                    └── loadWorkspaceSubagentReferences()  # 从 workspace/subagents/ 加载 .md 文件
```

## 设计模式
- 观察者模式：通过 EventBus 订阅消息
- 响应式编程：基于 Project Reactor
- Builder模式：所有工具使用 Builder 创建，不依赖 Spring 管理
- 策略模式：不同子智能体类型通过统一的接口执行
- 调度者-执行者模式：主智能体作为调度者，子智能体作为执行者
