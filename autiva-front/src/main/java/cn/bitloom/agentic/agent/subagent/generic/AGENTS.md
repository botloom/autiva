# Generic Subagent 包

## 概述
通用子智能体实现，从带有 YAML 前置元数据的 Markdown 文件解析子代理定义。这是 Autiva 的核心通用子智能体类型，通过不同的 Markdown 配置文件驱动多种角色各异的子智能体实例。

## 核心类

### GenericSubagentDefinition
从 Markdown 前置元数据解析的通用子代理定义。

**常量：**
- `KIND = "GENERIC"`: 类型标识符

**前置元数据字段：**
- `name`（必需）: 子代理名称
- `description`（必需）: 子代理描述
- `model`（可选）: 模型覆盖，支持 provider:model 格式和别名（deepseek/glm/opus/haiku/sonnet）
- `tools`（可选）: 允许的工具名称列表
- `disallowedTools`（可选）: 禁止的工具名称列表
- `skills`（可选）: 注入到上下文的技能名称
- `permissionMode`（可选）: 权限模式

**注册信息格式：**
覆盖了 `toSubagentRegistrations()` 方法，输出格式为 `- **name**: description (工具: tool1, tool2)`，比默认格式更丰富，包含工具列表信息。

### GenericSubagentExecutor
使用 Spring AI ChatClient 执行通用子代理任务。

**功能：**
- 支持多 ChatClient Builder（按 provider 分派）
- 工具过滤（allowed/disallowed）
- 技能内容注入到系统提示
- 模型名称映射（deepseek→deepseek-chat, glm→glm-4-flash, opus/haiku/sonnet→Claude模型）
- **对话记忆支持**：通过 ChatMemory + MessageChatMemoryAdvisor 实现子智能体对话记忆
- **resume 支持**：通过 TaskCall.resume 参数恢复之前的子智能体会话上下文
- **agent_id 返回**：每次执行返回 agent_id，可用于后续 resume

**对话记忆机制（Session 集成）：**
- 使用 `subagent_` 前缀的 conversationId 隔离子智能体与主智能体的记忆
- **Session 感知**：同一会话中同一类型的子智能体共享对话记忆，conversationId 格式为 `subagent_{sessionId}_{subagentType}`
- **Resume 支持**：通过 TaskCall.resume 参数传入之前的 agent_id 可恢复上下文，conversationId 为 `subagent_{resume}`
- **降级处理**：无 sessionId 时自动生成 UUID 作为 conversationId
- 执行结果中包含 agent_id，供主智能体后续使用

**Session 集成流程：**
```
MainAgent 设置 toolContext(sessionId)
    ↓
SessionAwareTaskToolCallback 从 ToolContext 提取 sessionId
    ↓
注入到 TaskCall.sessionId 字段
    ↓
GenericSubagentExecutor 使用 sessionId 构建 conversationId
    ↓
同一会话中同一子智能体类型共享 ChatMemory 对话历史
```

### GenericSubagentResolver
从 classpath/file URI 加载 Markdown 文件并解析为 GenericSubagentDefinition。

### GenericSubagentType
Builder 模式，配置通用子代理类型。

**Builder 参数：**
- `braveApiKey(String)`: Brave 搜索 API Key
- `chatClientBuilder(String, ChatClient.Builder)`: 按模型ID注册 ChatClient Builder
- `chatClientBuilders(Map)`: 批量注册 ChatClient Builder
- `skillsDirectories(List<String>)`: 技能目录列表
- `deployTool(DeployTool)`: 部署工具实例
- `chatMemory(ChatMemory)`: 对话记忆实例（必需）
- `sessionManager(SessionManager)`: 会话管理器实例

**默认子代理工具集：**
- TodoWriteTool、GrepTool、GlobTool、ShellTools、FileSystemTools、WebFetchTool
- SkillTool（如果配置了 skillManager 且有已加载的技能）
- DeployTool（如果配置了 deployTool）
- WebSearchTool（如果配置了 braveApiKey）

### GenericSubagentReferences
工厂方法，从目录/资源发现 .md 代理文件。

**方法：**
- `fromRootDirectories(List<String>)`: 从多个目录发现子代理
- `fromRootDirectory(String)`: 从目录发现子代理
- `fromResource(Resource)`: 从资源发现子代理
- `fromResources(List<Resource>)`: 从多个资源发现子代理

## 子智能体实例

通过不同的 Markdown 配置文件，GENERIC 类型驱动以下子智能体实例：

| 子智能体 | 配置文件 | 工具集 | 角色定位 |
|----------|----------|--------|----------|
| **Code** | `CODE_SUBAGENT.md` | Bash,Read,Write,Edit,Glob,Grep,WebFetch,WebSearch,TodoWrite,Skill | 全栈编码专家，唯一拥有文件读写和Shell执行能力 |
| **Bash** | `BASH_SUBAGENT.md` | Bash,WebFetch | 终端命令执行专家 |
| **Explore** | `EXPLORE_SUBAGENT.md` | Read,Glob,Grep,WebFetch | 代码探索专家（只读） |
| **Plan** | `PLAN_SUBAGENT.md` | Read,Glob,Grep,WebFetch,WebSearch,TodoWrite | 规划专家（只读+规划） |
| **Research** | `RESEARCH_SUBAGENT.md` | WebFetch,WebSearch,Read,Glob,Grep | 研究专家（网络搜索+本地搜索） |

## 资源文件
子代理 Markdown 文件位于 `~/.autiva/workspace/subagents/`，首次启动从 classpath `agent/` 目录复制。

## 设计模式
- 策略模式：不同子代理类型通过统一的接口执行
- 工厂模式：GenericSubagentType 将 Resolver 和 Executor 配对
- 配置驱动：新增子智能体只需添加 Markdown 文件，无需写 Java 代码
