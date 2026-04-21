# Agent 包

## 概述
本包实现了智能体核心系统，通过 AgentManager 进行统一管理。智能体分为两种类型：

1. **主智能体（MAIN）**：长期记忆，有独立工作目录，通用型 AI 助手
2. **子智能体（SUBAGENT）**：无记忆，用于完成子任务，配置存放在 workspace/subagents/ 目录

智能体通过 EventBus 的 Inbox/Outbox 双通道进行消息收发。

## 目录结构

```
~/.autiva/workspace/
  MAIN/                    # 主智能体工作目录
    IDENTITY.md            # 身份定义
    SOUL.md                # 行为准则
    MEMORY.md              # 长期记忆
    TOOLS.md               # 工具笔记
    USER.md                # 用户偏好
  DOCTOR/                  # 另一个主智能体
    ...
  subagents/               # 子智能体配置目录
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
- `initAgentWorkspaces()`: 创建主智能体工作目录和默认模板文件
- `initSubagentWorkspace()`: 创建子智能体目录并从 classpath 复制默认配置
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
- `listAgents()`: 列出所有智能体（主智能体 + 子智能体）
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
主智能体，处理用户的主要对话。

**功能：**
- 订阅 EventBus Inbox
- 处理用户请求并通过 Outbox 返回响应
- 支持流式响应和非流式响应两种模式
- 系统提示词由工作目录中的 .md 文件 + 技能描述 + 运行时信息组成

**系统提示词结构：**
1. 工作目录上下文（IDENTITY.md, SOUL.md, MEMORY.md, USER.md, TOOLS.md 按文件名排序拼接）
2. 运行环境（工作目录路径、当前时间、智能体标识）
3. 可用技能列表（如有）

**工具集（统一Builder模式注册）：**
- `FileSystemTools`: 文件操作（Read/Write/Edit）— `FileSystemTools.builder().build()`
- `ShellTools`: Shell 命令执行（Bash/BashOutput/KillShell）— `ShellTools.builder().build()`
- `WebFetchTool`: 智能网页获取+AI摘要 — `WebFetchTool.builder(chatClient).build()`
- `AskUserQuestionTool`: 向用户提问 — `AskUserQuestionTool.builder().questionHandler(handler).build()`
- `TodoWriteTool`: 任务列表管理 — `TodoWriteTool.builder().todoEventHandler(handler).build()`
- `CronTool`: 定时任务管理 — `CronTool.builder(cronManager).build()`
- `Task/TaskOutput`: 子代理任务工具 — `taskManager.buildToolCallbacks()`（ToolCallbacks模式）

**ChatClient 配置：**
- 支持 DeepSeek 和智谱AI 两个模型
- 使用 StTemplateRenderer 模板渲染
- 集成 MessageChatMemoryAdvisor、LoggingAdvisor、ToolCallAdvisor

### 枚举类
- `AgentIdentityEnum`: 主智能体身份标识 (MAIN, DOCTOR)

## 消息流程

```
用户消息 -> EventBus.inBoxPublish()
                    │
                    ▼
            MainAgent
            (订阅 Inbox，处理消息)
                    │
                    ▼
            EventBus.outBoxPublish()
                    │
                    ▼
            用户接收响应
```

## 子智能体加载流程

```
AgentManager.init()
    │
    ├── initAgentWorkspaces()     # 创建主智能体目录和默认模板
    ├── initSubagentWorkspace()   # 创建 subagents/ 目录，复制默认 .md 配置
    └── registerAgents()          # 注册所有智能体到 agents Map
            │
            ▼
MainAgent.init()
    │
    └── TaskManager.registerSubagentTypes()
            │
            └── resolveAndIndex()
                    │
                    └── loadWorkspaceSubagentReferences()  # 从 workspace/subagents/ 加载 .md 文件
```

## 设计模式
- 观察者模式：通过 EventBus 订阅消息
- 响应式编程：基于 Project Reactor
- Builder模式：所有工具使用 Builder 创建，不依赖 Spring 管理
- 策略模式：不同子智能体类型通过统一的接口执行
