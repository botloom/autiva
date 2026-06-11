# Agent 包

## 概述
本包实现了统一的智能体核心系统，参考 AgentScope 的设计理念，主智能体和子智能体都是 `Agent` 类的实例，区别仅在 `AgentDefinition` 的配置。智能体定义和长期配置存放在 `agents/` 目录，运行时数据存放在 `workspace/` 目录。

## 文件系统结构

```
~/.autiva/
├── agents/                        ← 智能体定义和长期配置（不随 session 变化）
│   ├── default/                   ← 默认主智能体
│   │   ├── agent.md               ← 智能体定义（YAML frontmatter + 提示词正文）
│   │   ├── config.json            ← MCP + 工具白名单 + skill + subagent
│   │   ├── AGENTS.md              ← 人格 + 行为约定
│   │   ├── MEMORY.md              ← 长期记忆
│   │   ├── BOOTSTRAP.md           ← 首次启动引导
│   │   └── memory/                ← 每日记忆流水账
│   │       └── YYYY-MM-DD.md
│   ├── code/                      ← 编码子智能体（仅 agent.md，无 config.json）
│   │   └── agent.md
│   ├── explore/                   ← 探索子智能体
│   │   └── agent.md
│   ├── doctor/                    ← 诊断子智能体
│   │   └── agent.md
│   └── <agent-id>/                ← 用户复制的主智能体
│       ├── agent.md
│       ├── config.json
│       ├── AGENTS.md
│       ├── MEMORY.md
│       ├── BOOTSTRAP.md
│       ├── memory/
│       └── xxx.md                 ← 额外提示词片段
├── workspace/                     ← 运行时数据（仅 session 相关）
│   ├── default/
│   │   ├── context/<session-id>   ← 会话快照
│   │   └── sessions/              ← 会话消息日志
│   └── <agent-id>/
│       ├── context/
│       └── sessions/
├── skills/                        ← 技能目录
└── settings.properties
```

**关键设计**：
- `agents/` 存放智能体定义和所有不随 session 变化的长期配置
- `workspace/` 仅存 session 相关的运行时数据（context/、sessions/）
- 子智能体是内置的，只有 agent.md，不需要 config.json 和 workspace
- 用户复制主智能体时，复制 agents/default/ 的所有内容到 agents/<new-id>/

## 核心类

### AgentKind
智能体分类枚举，替代原 `AgentType`。

- `MAIN`: 主智能体，直接面向用户，拥有 agents/ 下的长期配置和 workspace/ 下的运行时数据
- `SUBAGENT`: 子智能体，被其他 Agent 通过 Task 工具调用，内置不可配置

### AgentDefinition
统一的智能体定义 record，从 `agent.md` 的 YAML frontmatter + markdown body 解析而来。替代原先的 `SubagentDefinition + WorkspaceConfig` 双轨配置。

**字段：**
- `name`: 智能体名称
- `description`: 智能体描述
- `kind`: AgentKind（MAIN 或 SUBAGENT）
- `model`: 可选模型覆盖（子智能体专用）
- `tools`: 工具白名单
- `disallowedTools`: 工具黑名单
- `skills`: 注入到上下文的技能名称列表
- `permissionMode`: 权限模式
- `content`: 系统提示词正文（agent.md 的 markdown body）

**核心方法：**
- `fromMarkdown(Path)`: 从 agent.md 文件解析
- `fromMarkdown(String)`: 从 markdown 字符串解析
- `toRegistrationText()`: 格式化注册信息，用于 Task 工具描述

**agent.md 格式示例（子智能体）：**
```markdown
---
name: code
description: 全栈编码专家
model: deepseek
tools: Bash,Read,Write,Edit,Glob,Grep,WebFetch,WebSearch,TodoWrite,Skill
kind: subagent
---

你是一个全栈编码专家...
```

**agent.md 格式示例（主智能体）：**
```markdown
---
name: default
description: Autiva 默认助手
kind: main
---

你是 Autiva，一个智能助手...
```

### Agent
统一智能体类，合并了原 `AbstractAgent + MainAgent + SubagentExecutor` 的功能。主智能体和子智能体都是 Agent 类的实例，区别仅在 AgentDefinition 的配置。

**只能通过 Builder 创建（私有构造函数）。**

**核心字段：**
- `agentId`: 智能体标识
- `kind`: AgentKind
- `definition`: AgentDefinition
- `modelFactory`: ModelFactory（用于动态创建 ChatClient）
- `tools`: 根据 definition.tools 过滤后的工具集
- `advisors`: AgentAdvisor 列表（替代原 Hook 机制）
- `chatClientCache`: `Map<ModelTypeEnum, ChatClient>`（按需缓存 ChatClient）

**主智能体执行（Session 驱动）：**
- `runStream(Session, Message)`: 流式调用 LLM，返回 `Flux<AssistantMessage>`
- `runBlock(Session, Message)`: 阻塞调用 LLM，返回 `AssistantMessage`

**子智能体执行由 TaskTool 驱动，Agent 类不再包含 execute() 方法。**

**模型切换方案：**
Agent 持有 `ModelFactory` 引用，运行时通过 `chatClientCache` 按需缓存 ChatClient。主智能体使用 `session.getModel()` 选择模型，子智能体优先使用 `definition.model()`，其次使用上下文中的模型，最后回退到 DEEPSEEK。

**Advisor 机制：**
Hook 机制已替换为 Spring AI Advisor 机制。Agent 在创建 ChatClient 时注册 Advisors，在 Agent 层拦截模型调用前后。

**Builder 模式：**
```java
Agent.builder()
    .agentId("default")
    .definition(definition)
    .modelFactory(modelFactory)
    .tools(tools)
    .advisors(advisors)
    .build()
```

### AgentManager
智能体管理器，统一管理所有 Agent（MAIN 和 SUBAGENT），合并了原 `AgentFactory` 的功能，支持懒加载。

**核心字段：**
- `agents`: `ConcurrentHashMap<String, Agent>` — Agent 实例缓存
- `definitions`: `ConcurrentHashMap<String, AgentDefinition>` — AgentDefinition 缓存
- `skillManager`: SkillManager（@Getter 供 TaskTool 使用）
- `sessionManager`: SessionManager（@Lazy）
- `toolkit`: Toolkit（工具构建委托给 Toolkit）
- `journalManager`: JournalManager
- `mcpToolCallbackProvider`: AsyncMcpToolCallbackProvider

**初始化与加载：**
- `init()`: @PostConstruct，扫描 agents/ 目录加载所有定义，只预加载 default 主智能体
- `loadAllDefinitions()`: 扫描 agents/ 目录解析所有 agent.md
- `getAgent(String)`: 懒加载获取 Agent（computeIfAbsent）
- `createAgent(String)`: 创建 Agent 实例（加载定义 → 构建工具 → 构建 Advisor → Builder 创建）

**Advisor 构建：**
- `buildAdvisors(AgentDefinition)`: 根据 kind 构建 Advisor 列表。MAIN 智能体默认注册 JournalAdvisor 和 MemoryConsolidateAdvisor

**工具构建已委托给 Toolkit：**
- `toolkit.buildToolCallbacks(definition)`: 根据 AgentDefinition 构建工具集

**系统提示词构建：**
- `buildSystemPrompt(String agentId)`: MAIN 智能体从 agents/{agentId}/ 下的 .md 文件加载（支持覆盖），SUBAGENT 直接使用 definition.content()
  1. 运行环境信息（工作目录、时间、平台）
  2. 根目录 AGENTS.md（agents/{agentId} 有同名文件则跳过）
  3. 根目录 MEMORY.md（agents/{agentId} 有同名文件则跳过）
  4. agents/{agentId}/*.md（覆盖或扩展根目录文件，排除 agent.md）

**配置加载：**
- `loadWorkspaceConfig(String agentId)`: 加载 agents/{agentId}/config.json（仅 MAIN 智能体），与根 config.json 合并

**CRUD 方法：**
- `listSubagentDefinitions()`: 列出所有子智能体定义
- `listMainAgentDefinitions()`: 列出所有主智能体定义
- `getSubagentContent(String)`: 获取子智能体 agent.md 内容
- `saveSubagentConfig(String, String)`: 保存子智能体配置
- `createSubagentConfig(String, String, String)`: 创建子智能体配置
- `deleteSubagentConfig(String)`: 删除子智能体配置
- `copyMainAgent(String, String)`: 复制主智能体
- `reloadDefinitions()`: 重新加载所有定义

**内部类：**
- `AgentInfo(String name, String type)`: record
- `AgentFolder(String name, Path path, List<AgentFile> files)`: 主智能体文件夹
- `AgentFile(Path path, String displayName)`: 主智能体文件

### WorkspaceConfig
对应 `config.json` 的模型类，仅用于 MAIN 智能体。子智能体不需要 config.json。

**字段：**
- `tools`: 工具白名单（为空时注册所有默认工具）
- `mcpServers`: MCP server 配置映射
- `skills`: 技能名称列表
- `subagents`: 可用子智能体名称列表

### advisor 包

基于 Spring AI Advisor 机制的智能体拦截器，替代原 Hook 机制。在 Agent 层注册，与 Session 无关。

#### AgentAdvisor
Advisor 基类，实现 `CallAdvisor` + `StreamAdvisor` 接口。

**可覆盖方法：**
- `beforeModelCall(ChatClientRequest)`: 模型调用前触发，可修改请求
- `afterModelCall(ChatClientRequest, ChatClientResponse)`: 模型调用后触发
- `beforeToolCall(String, String)`: 工具调用前触发
- `afterToolCall(String, String)`: 工具调用后触发

**实现原理：**
- `adviseCall()`: 调用 beforeModelCall → chain.nextCall → afterModelCall
- `adviseStream()`: 调用 beforeModelCall → chain.nextStream → doOnNext(afterModelCall)

#### JournalAdvisor
日记 Advisor，在模型调用后将摘要写入日记。order=101。

#### MemoryConsolidateAdvisor
记忆整理 Advisor，在模型调用后检查未处理消息数量，超过阈值时触发 LLM 记忆整理。order=100。

**工作流程：**
1. 从 response.context() 获取 conversationId
2. 从 request.prompt().getInstructions() 获取对话消息
3. 检查未处理消息数量（从 cursor 到最新）
4. 超过阈值（10条）时，提取对话文本
5. 调用 LLM 总结为关键事实
6. 追加到 `memory/YYYY-MM-DD.md`（日流水账）
7. 调用 LLM 合并去重后写入 `MEMORY.md`（长期记忆）
8. 更新 conversationCursors

## 消息流程

```
用户消息 → ViewModel.sendMessage()
         → sessionManager.publishMessage(sessionId, message)
         → eventBus.inBoxPublish(message)
         → Session.handleMessage() [订阅 inBox]
            ├─ 首次对话？→ 注入 BOOTSTRAP.md 作为 SystemMessage 前置
            └─ agent.runStream(session, message) 或 agent.runBlock(session, message)
         → 响应通过 eventBus.outBoxPublish() 发布
         → ViewModel 的 outBoxSubscribe() 接收并渲染
         → Advisor 机制自动触发 afterModelCall
         → sessionManager.saveContext() 保存上下文快照
```

## 设计模式
- **统一 Agent 模型**：主智能体和子智能体都是 Agent 类的实例，区别仅在配置
- **Builder 模式**：Agent 只能通过 Builder 创建（私有构造函数）
- **懒加载**：AgentManager 启动只加载 default，其它按需创建
- **模型切换**：Agent 持有 ModelFactory，通过 Map 缓存 ChatClient 动态选择模型
- **观察者模式**：通过 EventBus 订阅消息
- **Advisor 机制**：基于 Spring AI Advisor 替代原 Hook 机制，在 Agent 层拦截
- **白名单模式**：config.json 的 tools 字段控制工具注册
