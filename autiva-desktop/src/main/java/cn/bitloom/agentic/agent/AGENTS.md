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
│       └── xxx.md                 ← 额外提示词片段
├── workspace/                     ← 运行时数据（仅 session 相关）
│   ├── default/
│   │   └── sessions/              ← 会话数据
│   │       └── <session-id>/
│   │           ├── metadata.json  ← Session 序列化（唯一状态源）
│   │           └── messages.jsonl ← 消息持久化
│   └── <agent-id>/
│       └── sessions/
├── skills/                        ← 技能目录
└── settings.properties
```

**关键设计**：
- `agents/` 存放智能体定义和所有不随 session 变化的长期配置
- `workspace/` 仅存 session 相关的运行时数据（sessions/）
- 子智能体是内置的，只有 agent.md，不需要 config.json 和 workspace
- 用户复制主智能体时，复制 agents/default/ 的所有内容到 agents/<new-id>/

## 核心类

### AgentKind
智能体分类枚举，替代原 `AgentType`。

- `MAIN`: 主智能体，直接面向用户，拥有 agents/ 下的长期配置和 workspace/ 下的运行时数据
- `SUBAGENT`: 子智能体，被其他 Agent 通过 Task 工具调用，内置不可配置

### AgentDefinition
统一的智能体定义 record，从 `agent.md` 的 YAML frontmatter + markdown body 解析而来。MAIN 智能体在加载时会合并 config.json，合并后 tools/skills/subagents/mcpServers 以 config.json 为准（非空覆盖）。

**字段：**
- `name`: 智能体名称（@NonNull）
- `description`: 智能体描述（@NonNull）
- `kind`: AgentKind（MAIN 或 SUBAGENT）（@NonNull）
- `tools`: 工具白名单（@NonNull）
- `skills`: 注入到上下文的技能名称列表（@NonNull）
- `subagents`: 可用子智能体名称列表（@NonNull）
- `mcpServers`: MCP server 配置映射（@NonNull）
- `content`: 系统提示词正文（agent.md 的 markdown body）（@NonNull）

**内部类 WorkspaceConfig：**
对应 `config.json` 的模型类，仅用于 MAIN 智能体。加载后通过 `merge()` 合并到 AgentDefinition 中。

- `tools`: 工具白名单
- `mcpServers`: MCP server 配置映射
- `skills`: 技能名称列表
- `subagents`: 可用子智能体名称列表

**核心方法：**
- `fromMarkdown(Path/String)`: 从 agent.md 解析 AgentDefinition
- `merge(WorkspaceConfig)`: 将 config.json 合并到定义中，非空字段覆盖
- `toRegistrationText()`: 格式化注册信息

### Agent
统一智能体类。主智能体和子智能体都是 Agent 类的实例，区别仅在 AgentDefinition 的配置。

**纯 POJO 设计**：Agent 实例不注入任何 Spring Bean，所有依赖通过 Builder 方法链设置。可选功能（记忆、压缩等）默认不开启，需要显式调用 Builder 方法启用。运行时依赖（如 Session）通过 `RuntimeContext` 在调用时传递。

**只能通过 Builder 创建（私有构造函数）。**

**核心字段：**
- `name`: 智能体名称
- `definition`: AgentDefinition
- `chatClient`: ChatClient（由 Builder 在 build() 时构建）
- `tools`: 根据 definition.tools 过滤后的工具集
- `hooks`: AgentHook 列表（通过 HookAdvisor 桥接到 Spring AI Advisor）

**执行方法：**
- `runStream(RuntimeContext ctx, Message message)`: 流式调用 LLM，返回 `Flux<AssistantMessage>`。参数从 RuntimeContext 获取（conversationId、params），与 Session 解耦。conversationId 为 null 时不传 CONVERSATION_ID（子智能体场景）。内部通过 `onErrorResume` 将异常转换为兜底 `AssistantMessage`
- `runBlock(RuntimeContext ctx, Message message)`: 阻塞调用 LLM，返回 `AssistantMessage`。同 runStream，从 ctx 取参数

**Builder 方法链（可选功能默认不开启）：**
```java
Agent.builder()
    .name("default")
    .definition(definition)
    .model(chatModel)              // ChatModel（必需）
    .systemPrompt(prompt)          // 系统提示词
    .tools(tools)                  // 工具列表
    .hooks(hooks)                  // AgentHook 列表
    .memory(chatMemory)            // 开启记忆（传入 ChatMemory）
    .compact(true)                 // 开启上下文压缩（注册 UsageAdvisor + ProactiveContextAdvisor）
    .skillManager(skillManager)    // SkillManager（传给 ProactiveContextAdvisor，请求时动态计算技能描述）
    .definitionManager(defMgr)     // AgentDefinitionManager（传给 ProactiveContextAdvisor，请求时动态计算子智能体描述）
    .memoryFilePath(path)          // memory.md 路径（传给 ProactiveContextAdvisor）
    .logging(true)                 // 开启日志（默认 true）
    .build()
```

**Builder 注册的 Advisor（受 enableDefaultAdvisors 全局开关控制）：**
- `LoggingAdvisor`：日志记录（默认开启）
- `ToolCallAdvisor`：工具调用消息处理（默认开启）
- `MessageChatMemoryAdvisor`：通过 `.memory()` 开启
- `UsageAdvisor` + `ProactiveContextAdvisor`：通过 `.compact(true)` 开启
- `HookAdvisor`：当 hooks 非空时注册
- 调用 `.disableDefaultAdvisors()` 后，以上 Advisor 全部不注册（子智能体场景）

### RuntimeContext
运行时上下文，通过 `ChatClientRequest.context()` 传递给 Advisor。与 Session 解耦，支持主智能体和子智能体两种场景。

**字段：**
- `session`: Session 实例（可为 null，子智能体场景为 null）
- `conversationId`: 会话 ID（主智能体 = session.getId()，子智能体 = taskId）
- `params`: `Map<String, Object>` 工具上下文参数（如 sessionId、model）

**两个构造函数：**
- `RuntimeContext(Session session)`: 主智能体场景，conversationId = session.getId()
- `RuntimeContext(String conversationId)`: 子智能体场景，无 Session

**参数传递：**
- `param(key, value)`: 链式添加参数（会作为 toolContext 传给工具）
- `getParam(key)`: 获取参数

**传递机制：**
1. 主智能体：`Session.start()` 构建 `RuntimeContext(this)` 并填充 sessionId/model
2. 子智能体：`TaskTool.execute()` 构建 `RuntimeContext(taskId)`（无 Session）
3. 调用 `agent.runStream(ctx, message)` 或 `agent.runBlock(ctx, message)`
4. Agent 通过 `.advisors(a -> a.param("runtimeContext", ctx))` 注入到 ChatClientRequest.context()
5. Agent 通过 `.toolContext(ctx.getParams())` 将参数传给工具
6. Advisor 通过 `request.context().get("runtimeContext")` 获取 RuntimeContext

**设计优势：** Advisor 无需在构建时持有 SessionManager，依赖在运行时传递，Agent 保持纯 POJO。子智能体无需 Session 也能调用 runStream/runBlock。

### AgentDefinitionManager
智能体定义管理器，统一管理所有 AgentDefinition（MAIN 和 SUBAGENT）。

**核心字段：**
- `definitions`: `ConcurrentHashMap<String, AgentDefinition>` — 所有定义缓存（MAIN 和 SUBAGENT）

**初始化与加载：**
- `init()`: @PostConstruct，先加载 SUBAGENT 定义（classpath:subagent/），再加载 MAIN 定义（~/.autiva/agents/）
- `getDefinition(String)`: 获取指定名称的定义
- `getSubagentDefinitions()`: 获取所有 SUBAGENT 定义
- `getSubagentDefinitions(List<String>)`: 按名称列表过滤 SUBAGENT 定义（空列表返回全部）
- `getMainAgentIds()`: 获取所有 MAIN agent ID
- `getOrLoadMainDefinition(String)`: 获取或懒加载 MAIN 定义（加载后合并 config.json）
- `loadWorkspaceConfig(String)`: 加载并合并 WorkspaceConfig（default + agentId，agentId 优先）
- `loadConfigFromFile(Path)`: 从文件读取 WorkspaceConfig

### advisor 包

基于 Spring AI Advisor 机制的智能体拦截器。

#### MemoryCompactAdvisor
上下文压缩 Advisor，当 currentContextLength 达到阈值时自动压缩。通过 FileSystemSessionManager 获取和更新 Session。

#### LongMemoryConsolidateAdvisor
记忆整理 Advisor，在模型调用后检查未处理消息数量，超过阈值时触发 LLM 记忆整理。通过 FileSystemSessionManager 获取和更新 Session。

#### ProactiveContextAdvisor
主动上下文注入 Advisor，注入摘要、任务清单、日志、记忆等。通过 FileSystemSessionManager 获取 Session。

#### HookAdvisor
Hook 桥接 Advisor，将 AgentHook 列表桥接到 Spring AI Advisor 机制。

## 消息流程

```
用户消息 → ViewModel.sendMessage()
         → sessionManager.publishMessage(sessionId, message)
         → Session.start() [订阅 inBox]
            ├─ 构建 RuntimeContext(this)
            ├─ agent.runStream(ctx, message) 或 agent.runBlock(ctx, message)
            │   ├─ .param("runtimeContext", ctx) 传递给 Advisor
            │   ├─ 推理循环
            │   └─ 响应通过 Session.publish() 发布
            → ViewModel 的 subscribe() 接收并渲染
            → Advisor 机制自动触发 afterModelCall
```

## 设计模式
- **统一 Agent 模型**：主智能体和子智能体都是 Agent 类的实例，区别仅在配置
- **纯 POJO 设计**：Agent 不注入任何 Spring Bean，所有依赖通过 Builder 方法链设置
- **RuntimeContext 运行时依赖传递**：Advisor 依赖通过 ChatClientRequest.context() 在运行时传递，无需构建时持有 SessionManager
- **Builder 方法链**：可选功能（记忆、压缩等）默认不开启，通过 `.memory()`/`.compact()` 等方法显式启用
- **Advisor 机制**：基于 Spring AI Advisor 替代原 Hook 机制，在 Agent 层拦截
- **白名单模式**：AgentDefinition.tools 字段控制工具注册（MAIN 智能体已合并 config.json）
- **定义管理集中化**：AgentDefinitionManager 统一管理所有定义，FileSystemSessionManager 和 TaskTool 共享
- **config.json 合并**：MAIN 智能体加载时自动合并 config.json（default + agentId），非空字段覆盖 frontmatter 值
- **Agent 构建下沉**：主智能体由 FileSystemSessionManager.getOrCreateAgent() 构建，子智能体由 TaskTool.createSubagent() 构建
