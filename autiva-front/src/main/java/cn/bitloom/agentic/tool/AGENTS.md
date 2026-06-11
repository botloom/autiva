# Tool 包

## 概述
本包实现了工具系统，为智能体提供可调用的工具集。所有工具统一使用 **AbstractTool\<I\> + Toolkit** 模式：

1. **AbstractTool\<I\> 抽象基类**：每个工具独立继承此类，泛型 I 对应 Input record 类型
2. **Toolkit 容器**：统一管理工具注册，支持链式调用，最终转为 `List<ToolCallback>` 供 Spring AI 消费
3. **Builder 模式**：所有工具通过 Builder 创建，依赖通过 Builder 参数注入
4. **一个工具一个类**：每个工具方法拆分为独立的类文件，按模块文件夹区分大类型

## 核心类

### AbstractTool\<I\>
所有工具的抽象基类，泛型 I 对应输入参数 record 类型。

```java
public abstract class AbstractTool<I> {
    private final String name;
    private final String description;
    private final Class<I> inputType;

    protected AbstractTool(String name, String description, Class<I> inputType) { ... }
    public abstract ToolResult execute(I input, ToolContext context);

    public final ToolCallback toToolCallback() {
        BiFunction<I, ToolContext, String> fn = (input, context) -> execute(input, context).toJson();
        return FunctionToolCallback.builder(name, fn)
                .description(description)
                .inputType(inputType)
                .build();
    }
}
```

### Toolkit
Spring @Component，统一管理工具注册和构建，替代原先散落在 AgentManager 中的工具构建逻辑。

**核心字段（通过构造函数注入）：**
- `skillManager`: SkillManager
- `agentManager`: AgentManager（@Lazy，避免循环依赖）
- `sessionManager`: SessionManager（@Lazy）
- `configManager`: ConfigManager
- `cronManager`: CronManager
- `toolUIBridge`: ToolUIBridge
- `mcpToolCallbackProvider`: AsyncMcpToolCallbackProvider

**核心方法：**
- `buildToolCallbacks(AgentDefinition)`: 根据 AgentDefinition 构建工具回调列表，按 kind 分流
- `buildMainAgentTools(AgentDefinition)`: 主智能体工具集（文件、搜索、命令、交互、定时、Task、技能、管理、MCP），支持 config.json 白名单过滤
- `buildSubagentTools(AgentDefinition)`: 子智能体工具集（默认工具 + 技能 + 搜索 + WebFetch），支持白名单/黑名单过滤

### ToolResult
所有工具统一返回 `ToolResult` 实体类，`toToolCallback()` 内部自动调用 `toJson()` 供 Spring AI 框架消费。

**字段：**
- `status`: SUCCESS / ERROR / WARNING — UI 根据此字段做差异化渲染（绿色/红色/黄色）
- `message`: 简短描述 — UI header 摘要显示，让用户一眼看到结果
- `data`: `LinkedHashMap<String, Object>` — 结构化键值对，UI 详情区域标签式展示
- `rawOutput`: 原始输出文本 — LLM 消费和 UI 输出区展示

**工厂方法：**
- `ToolResult.success(message)` / `ToolResult.success(message, data)` / `ToolResult.success(message, data, rawOutput)`
- `ToolResult.error(message)` / `ToolResult.error(message, rawOutput)` / `ToolResult.error(message, data)`
- `ToolResult.warning(message)` / `ToolResult.warning(message, data)` / `ToolResult.warning(message, data, rawOutput)`
- `ToolResult.builder().status(...).message(...).data(...).rawOutput(...).build()`

**序列化：**
- `toString()` / `toJson()`: 返回 JSON 字符串（含 status/message/data/rawOutput 四个字段），供 Spring AI 框架和 UI 消费
- `ToolResult.fromJson(json)`: 从 JSON 解析，解析失败返回 null（UI 降级到纯文本展示）
- `ToolResult.isToolResultJson(text)`: 判断字符串是否为 ToolResult JSON 格式

**UI 渲染：**
- `ToolMessageCard` 在 `isRequest=false` 时尝试 `ToolResult.fromJson(arguments)` 解析
- 解析成功：结构化渲染（状态圆点 + 摘要 + data 标签 + rawOutput 区域）
- 解析失败：降级到原有纯文本展示

## 工具注册方式

所有工具在 Toolkit.buildToolCallbacks() 中统一注册，Toolkit 是 Spring @Component，由 AgentManager 调用：

```java
// AgentManager.createAgent() 中：
List<ToolCallback> tools = toolkit.buildToolCallbacks(definition);
```

Toolkit 内部按 AgentDefinition.kind() 分流，构建主智能体或子智能体工具集。

## 目录结构

```
tool/
├── AbstractTool.java          # 抽象基类
├── Toolkit.java               # 工具容器
├── ToolResult.java            # 统一返回值
├── ToolUtils.java             # 共享工具方法
├── file/                      # 文件操作
│   ├── ReadTool.java
│   ├── WriteTool.java
│   └── EditTool.java
├── search/                    # 搜索工具
│   ├── GlobTool.java
│   ├── GrepTool.java
│   ├── WebSearchTool.java
│   ├── SearchProvider.java
│   └── BochaSearchProvider.java
├── web/                       # 网页工具
│   └── WebFetchTool.java
├── command/                   # 命令执行
│   ├── CommandTool.java
│   ├── ProcessTool.java
│   ├── CommandExecutor.java
│   ├── CommandResult.java
│   ├── CommandSafety.java
│   ├── EncodingHelper.java
│   ├── OutputSanitizer.java
│   ├── ProcessManager.java
│   ├── ShellSession.java
│   └── pty/                   # PTY 终端支持
│       ├── PtyResult.java
│       ├── PtySession.java
│       └── PtyTerminal.java
├── interaction/               # 交互工具
│   ├── AskUserQuestionTool.java
│   └── TodoWriteTool.java
├── task/                      # 子代理任务
│   ├── TaskTool.java
│   └── TaskOutputTool.java
├── skill/                     # 技能调用工具
│   └── SkillTool.java
├── cron/                      # 定时任务
│   ├── CronCreateTool.java
│   ├── CronListTool.java
│   ├── CronDeleteTool.java
│   └── CronTriggerTool.java
└── manage/                    # 管理类工具
    ├── skill/                 # 技能配置管理
    │   ├── SkillConfigListTool.java
    │   ├── SkillConfigGetTool.java
    │   ├── SkillConfigDeleteTool.java
    │   └── SkillConfigReloadTool.java
    ├── mcp/                   # MCP 服务器配置管理
    │   ├── McpConfigListTool.java
    │   ├── McpConfigUpdateTool.java
    │   └── McpConfigPathTool.java
    ├── memory/                # 记忆文件管理（管理员）
    │   ├── MemoryManageListTool.java
    │   ├── MemoryManageReadTool.java
    │   ├── MemoryManageWriteTool.java
    │   └── MemoryManageDeleteTool.java
    ├── subagent/              # 子智能体配置管理
    │   ├── SubagentConfigListTool.java
    │   ├── SubagentConfigGetTool.java
    │   ├── SubagentConfigCreateTool.java
    │   ├── SubagentConfigSaveTool.java
    │   ├── SubagentConfigDeleteTool.java
    │   └── SubagentConfigReloadTool.java
    └── app/                   # 应用配置管理
        ├── AppConfigReadTool.java
        ├── AppConfigGetTool.java
        ├── AppConfigSetIsolationTool.java
        └── AppConfigPathTool.java
```

## 子包详细说明

### file 包 (`cn.bitloom.agentic.tool.file`)
文件系统操作工具，每个工具独立继承 AbstractTool。

- **ReadTool**: 文件读取工具（继承 AbstractTool\<ReadTool.Input\>）。支持 offset/limit 分页，行号格式输出。成功时 data 含 file/start_line/end_line/total_lines，rawOutput 保留原始格式化文本
- **WriteTool**: 文件写入工具（继承 AbstractTool\<WriteTool.Input\>）。写入/覆盖文件，自动创建父目录。成功时 data 含 file/bytes/action
- **EditTool**: 文件编辑工具（继承 AbstractTool\<EditTool.Input\>）。精确字符串替换，支持 replace_all。成功时 data 含 file/occurrences/replace_all，rawOutput 保留 cat-n 片段。使用 ToolUtils 静态方法

### search 包 (`cn.bitloom.agentic.tool.search`)
搜索类工具，统一继承 AbstractTool。

- **GlobTool**: 文件模式匹配工具（继承 AbstractTool\<GlobTool.Input\>）。纯 Java NIO.2 实现，支持 glob 模式，按修改时间排序。Input record 包含 pattern/path。name="Glob"
- **GrepTool**: 正则表达式内容搜索工具（继承 AbstractTool\<GrepTool.Input\>）。纯 Java regex 实现，三种输出模式（files_with_matches/count/content）。Input record 包含 pattern/path/glob/outputMode/contextBefore/contextAfter/context/showLineNumbers/caseInsensitive/type/headLimit/offset/multiline。name="Grep"
- **WebSearchTool**: 网络搜索工具（继承 AbstractTool\<WebSearchTool.Input\>）。通过 SearchProvider 策略接口委托搜索操作，支持域名过滤。Input record 包含 query/allowedDomains/blockedDomains。Builder 接受 SearchProvider 和 resultCount。name="WebSearch"
- **SearchProvider**: 搜索引擎策略接口。`search(String query, int count)` 返回 `List<SearchResult>`
- **BochaSearchProvider**: 博查搜索实现（SearchProvider 接口）。API 地址 `https://api.bochaai.com/v1/web-search`，认证方式 `Authorization: Bearer {API_KEY}`

### web 包 (`cn.bitloom.agentic.tool.web`)
网页获取工具，统一继承 AbstractTool。

- **WebFetchTool**: 网页获取工具（继承 AbstractTool\<WebFetchTool.Input\>，实现 AutoCloseable）。HTTP 获取 + HTML 转 Markdown，15 分钟缓存，robots.txt 域名安全检查，自动重试。Input record 包含 url。name="WebFetch"

### command 包 (`cn.bitloom.agentic.tool.command`)
跨平台命令执行的核心实现。详见 [command/AGENTS.md](command/AGENTS.md)

- **CommandTool**: 命令执行工具（继承 AbstractTool\<CommandTool.Input\>）。支持前台/智能后台化/立即后台模式。Input record 包含 command/description/timeout/workdir/env/yieldMs/background。Builder 接受 CommandExecutor 和 ProcessManager。name="Command"
- **ProcessTool**: 后台进程管理工具（继承 AbstractTool\<ProcessTool.Input\>）。支持 list/poll/log/write/kill/clear 操作。Input record 包含 action/sessionId/data/offset/limit。Builder 接受 ProcessManager。name="Process"

### interaction 包 (`cn.bitloom.agentic.tool.interaction`)
交互类工具，统一继承 AbstractTool。

- **AskUserQuestionTool**: 向用户提问澄清问题的工具（继承 AbstractTool\<AskUserQuestionTool.Input\>）。支持多选/单选问题，1-4个问题，每个2-4个选项。Builder 接受 QuestionHandler。name="AskUserQuestionTool"
- **TodoWriteTool**: 结构化任务列表管理工具（继承 AbstractTool\<TodoWriteTool.Input\>）。验证规则：恰好一个 in_progress 任务。Builder 接受 TodoEventHandler。name="TodoWrite"

### task 包 (`cn.bitloom.agentic.tool.task`)
子代理任务工具，统一继承 AbstractTool。

- **TaskTool**: 启动子代理执行任务（继承 AbstractTool\<TaskTool.Input\>）。支持 Session Fork、TaskCard 流式输出、后台任务。包含子智能体执行逻辑（原 Agent.execute()）。Input record 包含 description/prompt/subagent_type/model/resume/run_in_background。Builder 接受 AgentManager/SessionManager/ToolUIBridge。name="Task"
  - `executeSubagent()`: 子智能体执行逻辑（流式/阻塞），从原 Agent.execute() 迁移
  - `getOrCreateChatClient()`: 创建子智能体 ChatClient，注册 Agent 的 Advisors
  - `resolveModel()`: 解析子智能体使用的模型
  - `buildSubagentSystemPrompt()`: 构建子智能体系统提示词，注入技能内容
- **TaskOutputTool**: 获取子代理任务输出（继承 AbstractTool\<TaskOutputTool.Input\>）。支持阻塞/非阻塞模式，可配置超时。Input record 包含 task_id/block/timeout。name="TaskOutput"

**Session 集成机制：**
Task 工具通过 ToolContext 实现与 Session 的集成：
1. MainAgent 在调用 ChatClient 时设置 `toolContext(Map.of("sessionId", sessionId, "model", model))`
2. Spring AI 的 FunctionToolCallback 使用 BiFunction 重载，将 ToolContext 作为第二个参数传递
3. TaskTool.execute() 从 ToolContext 提取 sessionId，调用 `sessionManager.forkSession()` 创建子会话
4. 子智能体执行器从 ToolContext 获取子会话ID

**TaskCard 流式输出机制：**
子智能体执行时通过 ToolUIBridge 实时推送输出到 TaskCard：
1. TaskTool.execute() 调用 `toolUIBridge.createTaskCard(taskCardId, taskJson)` 创建 UI 卡片
2. 子智能体执行器通过 `onChunk` 回调实时输出文本
3. 通过 `toolUIBridge.appendTaskOutput(taskCardId, chunk)` 推送到 UI
4. 执行完成后调用 `toolUIBridge.completeTaskCard(taskCardId, null)` 更新状态

### cron 包 (`cn.bitloom.agentic.tool.cron`)
定时任务工具，每个工具独立继承 AbstractTool。

- **CronCreateTool**: 创建定时任务工具（继承 AbstractTool\<CronCreateTool.Input\>）。支持三种类型：once/interval/cron。Input record 包含 name/type/intervalSeconds/delaySeconds/cronExpression/message。Builder 接受 CronManager。name="cron_create"
- **CronListTool**: 列出所有定时任务工具（继承 AbstractTool\<CronListTool.Input\>）。Input record 包含可选的 _none 字段。Builder 接受 CronManager。name="cron_list"
- **CronDeleteTool**: 删除定时任务工具（继承 AbstractTool\<CronDeleteTool.Input\>）。Input record 包含 name。Builder 接受 CronManager。name="cron_delete"
- **CronTriggerTool**: 手动触发定时任务工具（继承 AbstractTool\<CronTriggerTool.Input\>）。Input record 包含 name。Builder 接受 CronManager。name="cron_trigger"

### skill 包 (`cn.bitloom.agentic.tool.skill`)
技能调用工具，替代原 SkillManager.buildToolCallback()。

- **SkillTool**: 技能调用工具（继承 AbstractTool\<SkillTool.Input\>）。技能信息通过系统提示词注入，此工具仅负责按名称加载技能内容。Builder 接受 SkillManager。name="Skill"

### manage/skill 包 (`cn.bitloom.agentic.tool.manage.skill`)
技能配置管理工具，每个工具独立继承 AbstractTool。

- **SkillConfigListTool**: 列出所有已安装技能工具。Builder 接受 SkillManager。name="skill_config_list"
- **SkillConfigGetTool**: 获取指定技能详细内容工具。Builder 接受 SkillManager。name="skill_config_get"
- **SkillConfigDeleteTool**: 删除指定技能工具（破坏性操作）。Builder 接受 SkillManager。name="skill_config_delete"
- **SkillConfigReloadTool**: 重新加载所有技能配置工具。Builder 接受 SkillManager。name="skill_config_reload"

### manage/mcp 包 (`cn.bitloom.agentic.tool.manage.mcp`)
MCP服务器配置管理工具，每个工具独立继承 AbstractTool。

- **McpConfigListTool**: 列出所有已配置MCP服务器工具。name="mcp_config_list"
- **McpConfigUpdateTool**: 更新MCP服务器配置工具（覆盖整个配置文件）。name="mcp_config_update"
- **McpConfigPathTool**: 获取MCP配置文件路径工具。name="mcp_config_path"

### manage/memory 包 (`cn.bitloom.agentic.tool.manage.memory`)
记忆文件管理工具（管理员视角），每个工具独立继承 AbstractTool。

- **MemoryManageListTool**: 列出指定智能体的记忆文件列表工具。Builder 接受 AgentManager。name="memory_manage_list"
- **MemoryManageReadTool**: 读取指定智能体的记忆文件内容工具。Builder 接受 AgentManager。name="memory_manage_read"
- **MemoryManageWriteTool**: 写入或更新指定智能体的记忆文件内容工具。Builder 接受 AgentManager。name="memory_manage_write"
- **MemoryManageDeleteTool**: 删除指定智能体的记忆文件工具（破坏性操作）。Builder 接受 AgentManager。name="memory_manage_delete"

### manage/subagent 包 (`cn.bitloom.agentic.tool.manage.subagent`)
子智能体配置管理工具，每个工具独立继承 AbstractTool。

- **SubagentConfigListTool**: 列出所有子智能体配置工具。Builder 接受 AgentManager。name="subagent_config_list"
- **SubagentConfigGetTool**: 获取指定子智能体的配置内容工具。Builder 接受 AgentManager。name="subagent_config_get"
- **SubagentConfigCreateTool**: 创建新的子智能体配置工具。Builder 接受 AgentManager。name="subagent_config_create"
- **SubagentConfigSaveTool**: 保存/更新子智能体配置内容工具。Builder 接受 AgentManager。name="subagent_config_save"
- **SubagentConfigDeleteTool**: 删除子智能体配置工具（破坏性操作）。Builder 接受 AgentManager。name="subagent_config_delete"
- **SubagentConfigReloadTool**: 重新加载所有子智能体配置工具。Builder 接受 AgentManager。name="subagent_config_reload"

### manage/app 包 (`cn.bitloom.agentic.tool.manage.app`)
应用配置管理工具，每个工具独立继承 AbstractTool。

- **AppConfigReadTool**: 读取应用配置文件内容工具。Builder 接受 ConfigManager。name="app_config_read"
- **AppConfigGetTool**: 获取指定配置项的值工具。Builder 接受 ConfigManager。name="app_config_get"
- **AppConfigSetIsolationTool**: 设置会话隔离策略工具。Builder 接受 ConfigManager。name="app_config_set_isolation"
- **AppConfigPathTool**: 获取应用配置文件路径工具。Builder 接受 ConfigManager。name="app_config_path"

### deploy 包 (`cn.bitloom.agentic.deploy`)
项目部署工具，详见 [deploy/AGENTS.md](../deploy/AGENTS.md)

## 共享工具类

### ToolUtils
包级工具类（package-private），提供工具间共享的方法。

**方法：**
- `isIgnoredPath(Path)`: 判断路径是否属于忽略目录（.git、node_modules、target、build、.idea、.vscode、dist、__pycache__、.gradle、.mvn）
- `resolveWorkingDirectory(String, Path)`: 解析工作目录
- `countOccurrences(String, String)`: 统计子字符串出现次数
- `replaceFirst(String, String, String)`: 替换第一个匹配项
- `replaceAll(String, String, String)`: 替换所有匹配项
- `generateEditSnippet(String, String)`: 生成编辑位置周围的上下文片段（带行号）

## 日志规范

所有工具调用都会记录日志，格式统一为 `[ToolCall] {工具名} - {操作描述}: {参数信息}`。

## 设计模式
- **AbstractTool + Toolkit 模式**：所有工具继承 AbstractTool\<I\>，通过 Toolkit（Spring @Component）统一构建
- **Builder 模式**：所有工具使用 Builder 创建，不依赖 Spring 管理
- **策略模式**：WebSearchTool 通过 SearchProvider 接口支持搜索引擎切换
- **Input record 模式**：每个工具定义内部 Input record，字段用 `@ToolParam` 注解

## 注意事项
1. 工具类不使用 @Component 注解，不由 Spring 管理
2. Toolkit 是 Spring @Component，由 AgentManager 调用 buildToolCallbacks() 构建工具集
3. 主智能体注册所有工具（文件/搜索/命令/交互/任务/定时/技能/管理/MCP）
4. 子智能体注册精简工具集（文件/搜索/命令/交互/技能）
5. 工具名称必须唯一
6. 需要外部依赖的工具通过 Builder 参数传入（如 CronCreateTool 需要 CronManager）
