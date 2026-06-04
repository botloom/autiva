# Tool 包

## 概述
本包实现了工具系统，为智能体提供可调用的工具集。所有工具统一使用两种注册方式：
1. **Builder模式（优先）**：工具类通过Builder模式创建实例，使用 `@Tool` 注解标记工具方法，通过 `defaultTools()` 注册到 ChatClient
2. **ToolCallbacks模式（次选）**：通过 `FunctionToolCallback.builder()` 构建函数式工具回调，通过 `defaultToolCallbacks()` 注册到 ChatClient

## 统一返回值类型

### ToolResult
所有 `@Tool` 方法统一返回 `ToolResult` 实体类（`ToolCallbacks` 模式的方法因接口限制返回 `String`，内部用 `ToolResult.toJson()` 转换）。

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

### Builder模式（优先）
工具类使用Builder模式创建，不依赖Spring管理。在MainAgent的`@PostConstruct`中直接创建并注册：

```java
FileSystemTools fileSystemTools = FileSystemTools.builder().build();
CronTool cronTool = CronTool.builder(cronManager).build();

chatClientBuilder
    .defaultTools(fileSystemTools, commandTools, webFetchTool, askUserQuestionTool, todoWriteTool, cronTool)
    .build();
```

### ToolCallbacks模式（次选）
对于需要动态描述或函数式实现的工具，使用FunctionToolCallback：

```java
TaskTool.Builder taskToolBuilder = TaskTool.builder()
        .sessionManager(sessionManager)
        .toolUIBridge(toolUIBridge)
        .subagentTypes(subagentType);

chatClientBuilder
    .defaultToolCallbacks(taskToolBuilder.buildToolCallbacks())
    .build();
```

## Builder模式工具

### FileSystemTools
文件系统操作工具（Builder模式创建）。所有方法返回 `ToolResult`，UI 可通过 `ToolResult.fromJson()` 解析结构化信息。
- `Read`: 读取文件，支持 offset/limit 分页，行号格式输出。成功时 data 含 file/start_line/end_line/total_lines，rawOutput 保留原始格式化文本
- `Write`: 写入/覆盖文件，自动创建父目录。成功时 data 含 file/bytes/action
- `Edit`: 精确字符串替换，支持 replace_all。成功时 data 含 file/occurrences/replace_all，rawOutput 保留 cat-n 片段

### CommandTools
命令执行工具（Builder模式创建），灵感来源于 OpenClaw exec/process 双工具模型 + Hermes Agent 安全检测。
- `Command`: 执行命令（前台/智能后台化/立即后台），支持 per-call workdir/env 覆盖
- `Process`: 管理后台进程（list/poll/log/write/kill/clear）
- **双工具模型**：替代 v4 的 5 个碎片工具（Bash/BashOutput/KillBash/SendInput/GetCwd）
- **智能后台化**：`yield_ms` 参数，先前台运行，超时自动转后台
- **per-call 覆盖**：`workdir`/`env` 每次调用可覆盖持久化状态
- **破坏性命令检测**：正则匹配 `rm -rf`、`dd`、`mkfs` 等危险命令并发出警告
- **waiting_for_input 检测**：超过 15 秒无输出且 stdin 可写时标记
- **log 分页**：完整输出支持 offset/limit 分页读取
- **编码处理**：Shell初始化时强制UTF-8 + ANSI剥离 + CLIXML清理
- **当前目录管理**：ShellSession 维护 cwd，后台命令继承前台会话的工作目录

### GlobTool
文件模式匹配工具（纯 Java NIO.2 实现，Builder模式创建）。
- 支持 glob 模式（如 `**/*.java`）
- 按修改时间排序，忽略 .git/node_modules/target 等目录
- 使用 `ToolResult` 统一返回值，直接返回 `ToolResult` 对象
  - 错误：`ToolResult.error(message, rawOutput)`
  - 无匹配：`ToolResult.success(message)`
  - 成功：`ToolResult.builder().status(SUCCESS).message(count + " 个文件匹配 " + pattern).data(Map.of("pattern", pattern, "count", count)).rawOutput(原始文件列表).build()`

### GrepTool
正则表达式内容搜索工具（纯 Java regex 实现，Builder模式创建）。
- 三种输出模式：files_with_matches、count、content
- 支持上下文行、行号、大小写不敏感、多行模式
- 支持文件类型过滤（java、js、py、rust 等）
- 使用 `ToolResult` 统一返回值，直接返回 `ToolResult` 对象
  - 错误：`ToolResult.error(message, rawOutput)`
  - 无匹配：`ToolResult.success(message)`
  - files_with_matches：`ToolResult.builder().status(SUCCESS).message(count + " 个文件匹配").data(Map.of("pattern", pattern, "count", count)).rawOutput(result).build()`
  - count：`ToolResult.builder().status(SUCCESS).message(count + " 个文件有匹配").data(Map.of("pattern", pattern, "count", count)).rawOutput(result).build()`
  - content：`ToolResult.builder().status(SUCCESS).message("搜索结果").data(Map.of("pattern", pattern)).rawOutput(result).build()`
  - 截断检查应用于 rawOutput

### WebFetchTool
网页获取工具（Builder模式创建，无外部依赖）。
- HTTP 获取 + HTML 转 Markdown（Flexmark），直接返回Markdown内容，不使用AI摘要
- 15 分钟缓存，基于 robots.txt 解析的域名安全检查（国内化，不依赖外部API）
- 自动重试（指数退避），字符集检测
- Accept-Language 优先中文（zh-CN）
- Builder 无需 ChatClient 参数：`WebFetchTool.builder().build()`
- 返回值使用 `ToolResult` 包装：成功返回 `ToolResult.builder().status(SUCCESS).message("已获取 " + url).data(Map.of("url", url, "content_length", length)).rawOutput(markdownContent).build()`，错误返回 `ToolResult.error(message, rawOutput)`

### WebSearchTool
网络搜索工具（Builder模式创建，需要 SearchProvider）。
- 通过 `SearchProvider` 策略接口委托搜索操作，当前使用博查搜索引擎
- 支持域名过滤（allowedDomains/blockedDomains），客户端过滤
- 返回结构化搜索结果（`SearchResult` record）
- Builder 接受 `SearchProvider`：`WebSearchTool.builder(new BochaSearchProvider(apiKey)).resultCount(15).build()`
- 返回值使用 `ToolResult` 包装：空查询返回 `ToolResult.error("搜索查询不能为空")`，空结果返回 `ToolResult.success("未找到搜索结果", Map.of("query", query, "count", 0))`，成功返回 `ToolResult.builder().status(SUCCESS).message(count + " 条搜索结果").data(Map.of("query", query, "count", count)).rawOutput(JsonParser.toJson(filteredResults)).build()`，异常返回 `ToolResult.error("执行搜索请求时出错: " + e.getMessage())`

### SearchProvider
搜索引擎策略接口，定义统一的搜索方法。
- `search(String query, int count)`: 执行搜索，返回 `List<SearchResult>`

### BochaSearchProvider
博查搜索实现（`SearchProvider` 接口）。
- API 地址：`https://api.bochaai.com/v1/web-search`
- 认证方式：`Authorization: Bearer {API_KEY}`
- 请求方式：POST JSON（`{"query": "...", "count": N, "freshness": "noLimit", "summary": true}`）
- 响应解析：`data.webPages.value[].name/url/snippet/summary` → `SearchResult`（注意：标题字段为 `name` 非 `title`，优先使用 `summary` 作为描述）
- 中文搜索质量最强，国内备案、安全合规、数据不出海
- API Key 通过环境变量 `BOCHA_API_KEY` 或 `ConfigManager.bochaApiKey` 配置

### ListDirectoryTool
目录列表工具（Builder模式创建）。
- 递归深度控制，结果数量限制
- 跳过噪音目录，目录优先排序

### TodoWriteTool
结构化任务列表管理工具（Builder模式创建）。
- 验证规则：恰好一个 in_progress 任务
- 状态：pending、in_progress、completed
- 通过 `todoEventHandler` 回调与 UI 交互
- 使用 `ToolResult` 统一返回值，直接返回 `ToolResult` 对象
  - 成功：`ToolResult.success("待办事项已成功修改", Map.of("count", todos.size()))`

### AskUserQuestionTool
向用户提问澄清问题的工具（Builder模式创建，需要 QuestionHandler）。
- 支持多选/单选问题，1-4个问题，每个2-4个选项
- 使用 `ToolResult` 统一返回值，直接返回 `ToolResult` 对象
  - 成功：`ToolResult.success("用户已回答你的问题", Map.of("answers", result), "用户已回答你的问题: " + JsonParser.toJson(result))`

### AutoMemoryTools
持久化记忆文件管理工具（Builder模式创建）。
- 6个工具方法：MemoryView、MemoryCreate、MemoryStrReplace、MemoryInsert、MemoryDelete、MemoryRename
- 安全路径解析（防止路径遍历攻击）
- 记忆类型：user、feedback、project、reference

### CronTool
定时任务工具（Builder模式创建，需要 CronManager）。
- `cron_create`: 创建定时任务
- `cron_list`: 列出定时任务
- `cron_delete`: 删除定时任务
- `cron_trigger`: 手动触发定时任务

## DOCTOR 子智能体专属工具

以下工具注册到 GenericSubagentType 子智能体工具池，通过 DOCTOR_SUBAGENT.md 的 `tools` 字段过滤后仅供 Doctor 子智能体使用。

### SkillConfigTool
技能配置管理工具（Builder模式创建，需要 SkillManager）。
- `skill_config_list`: 列出所有已安装的技能
- `skill_config_get`: 获取指定技能的详细内容
- `skill_config_delete`: 删除指定技能（破坏性操作）
- `skill_config_reload`: 重新加载所有技能配置

### McpConfigTool
MCP服务器配置管理工具（Builder模式创建，需要 ConfigManager）。
- `mcp_config_list`: 列出所有已配置的MCP服务器
- `mcp_config_update`: 更新MCP服务器配置（覆盖整个配置文件）
- `mcp_config_path`: 获取MCP配置文件路径

### MemoryManageTool
记忆文件管理工具（Builder模式创建，需要 AgentManager）。
- `memory_manage_list`: 列出指定智能体的记忆文件
- `memory_manage_read`: 读取指定智能体的记忆文件内容
- `memory_manage_write`: 写入或更新指定智能体的记忆文件
- `memory_manage_delete`: 删除指定智能体的记忆文件（破坏性操作）

### SubagentConfigTool
子智能体配置管理工具（Builder模式创建，需要 AgentManager）。
- `subagent_config_list`: 列出所有子智能体配置
- `subagent_config_get`: 获取指定子智能体的配置内容
- `subagent_config_create`: 创建新的子智能体配置
- `subagent_config_save`: 保存/更新子智能体配置内容
- `subagent_config_delete`: 删除子智能体配置（破坏性操作）
- `subagent_config_reload`: 重新加载所有子智能体配置

### AppConfigTool
应用配置管理工具（Builder模式创建，需要 ConfigManager）。
- `app_config_read`: 读取Autiva应用配置文件内容
- `app_config_get`: 获取指定配置项的值
- `app_config_set_isolation`: 设置会话隔离策略
- `app_config_path`: 获取应用配置文件路径

## ToolCallbacks模式工具

### Task / TaskOutput / SessionQuery
子代理任务工具，通过 TaskTool.Builder / TaskOutputTool.Builder / SessionQueryTool.Builder 构建。
- `Task`: 启动子代理执行任务（支持 Session Fork、TaskCard 流式输出）
- `TaskOutput`: 获取子代理任务输出
- `SessionQuery`: 查询子智能体会话记录（支持list和history两种操作）
- Task 和 TaskOutput 使用 `ToolResult` 包装返回值，因 FunctionToolCallback 要求 String 返回类型，最终调用 `.toJson()` 转换
  - Task 后台任务：`ToolResult.success("后台任务已启动", Map.of("task_id", taskId), rawOutput).toJson()`
  - Task 前台任务：`ToolResult.success("任务已完成", Map.of("subagentName", subagentName), result).toJson()`
  - TaskOutput 未找到：`ToolResult.error("未找到ID为 ... 的后台任务").toJson()`
  - TaskOutput 被中断：`ToolResult.error("等待任务被中断").toJson()`
  - TaskOutput 成功：`ToolResult.success("任务输出", Map.of("task_id", taskId, "status", status), rawOutput).toJson()`

**TaskTool Builder 参数：**
- `sessionManager(SessionManager)`: 会话管理器，用于 fork 子会话
- `toolUIBridge(ToolUIBridge)`: UI 桥接，用于 TaskCard 流式输出
- `subagentTypes(SubagentType...)`: 子代理类型
- `subagentReferences(SubagentReference...)`: 子代理引用
- `taskRepository(TaskRepository)`: 后台任务仓库
- `taskDescriptionTemplate(String)`: 自定义描述模板

**TaskTool 内部类：**
- `TaskFunction`: 实现 BiFunction<TaskCall, ToolContext, String>，包含 Session Fork、TaskCard 创建、流式输出逻辑。通过 BiFunction 接收 ToolContext 参数，从中提取 sessionId 进行会话 fork，并将 ToolContext 注入到 TaskCall 中传递给子智能体执行器

**Session 集成机制：**
Task 工具通过 BiFunction<TaskCall, ToolContext, String> 实现与 Session 的集成：
1. MainAgent 在调用 ChatClient 时设置 `toolContext(Map.of("sessionId", sessionId, "model", model))`
2. Spring AI 的 FunctionToolCallback 使用 BiFunction 重载，将 ToolContext 作为第二个参数传递给 TaskFunction
3. `TaskFunction.apply(TaskCall, ToolContext)` 从 ToolContext 提取 sessionId，调用 `sessionManager.forkSession()` 创建子会话
4. 将 childSessionId 写入 ToolContext，并创建包含 ToolContext 的 enrichedTaskCall 传递给子智能体执行器
5. 子智能体执行器（如 GenericSubagentExecutor）从 `taskCall.toolContext().getContext().get("childSessionId")` 获取子会话ID

**Session Fork 机制：**
Task 调用时自动 fork 子会话，实现主子会话的父子关系：
1. `TaskFunction.apply()` 调用 `sessionManager.forkSession(parentSessionId, subagentType)` 创建子会话
2. 子会话 ID 格式：`{parentSessionId}_{自增序号}`
3. 子会话的 `parentId` 指向主会话，`target` 记录子智能体类型
4. TaskCard ID 使用子会话 ID，确保 UI 卡片与子会话一一对应

**TaskCard 流式输出机制：**
子智能体执行时通过 ToolUIBridge 实时推送输出到 TaskCard：
1. `TaskFunction.apply()` 调用 `toolUIBridge.createTaskCard(taskCardId, taskJson)` 创建 UI 卡片
2. 子智能体执行器通过 `onChunk` 回调实时输出文本
3. `TaskFunction` 将 chunk 通过 `toolUIBridge.appendTaskOutput(taskCardId, chunk)` 推送到 UI
4. 执行完成后调用 `toolUIBridge.completeTaskCard(taskCardId, null)` 更新状态
5. 执行失败时调用 `toolUIBridge.failTaskCard(taskCardId, error)` 显示错误

**工作区子智能体加载：**
当注册 GenericSubagentDefinition.IDENTITY 类型的子代理时，自动从 `WORKSPACE_DIR/{SUBAGENT_IDENTITY}` 加载 .md 文件作为子智能体引用。

**SessionQueryTool 详解：**
查询子智能体会话记录工具，通过 SessionQueryTool.Builder 构建。

**Builder 参数：**
- `sessionManager(SessionManager)`: 会话管理器（必需）

**操作类型：**
- `list`: 列出当前会话的所有子智能体会话，显示子智能体类型和消息数量。从 ToolContext 获取当前 sessionId
- `history`: 查询指定会话的对话记录，了解子智能体的完整交互过程。需要传入 session_id 参数

**输入参数（SessionQueryCall record）：**
- `action`: 操作类型（list/history）
- `session_id`: 要查询的会话ID（action=history时必填，可从list结果或Task工具返回的agent_id获取）
- `limit`: 返回的最大消息数量（action=history时有效，默认20，最大100）

**SessionQueryFunction 实现：**
- 实现 BiFunction<SessionQueryCall, ToolContext, String>，通过 ToolContext 获取当前会话ID
- list 操作：调用 `sessionManager.getChildSessions(currentSessionId)` 获取子会话列表
- history 操作：调用 `sessionManager.getById(sessionId)` 获取会话消息，按消息类型格式化输出
  - UserMessage: 显示文本内容
  - AssistantMessage: 显示文本内容 + 工具调用信息
  - ToolResponseMessage: 显示工具响应数据（截断到200字符）
  - 所有消息内容截断到500字符

### Skill
技能加载和执行工具，通过 SkillManager.buildToolCallback() 构建。
- 从 SKILL.md 文件加载技能定义
- 函数式工具回调（FunctionToolCallback）

## 子包

### task 包 (`cn.bitloom.agentic.tool.task`)
子代理任务工具，详见 [task/AGENTS.md](task/AGENTS.md)

### command 包 (`cn.bitloom.agentic.tool.command`)
跨平台命令执行的核心实现。详见 [command/AGENTS.md](command/AGENTS.md)

### deploy 包 (`cn.bitloom.agentic.deploy`)
项目部署工具，详见 [deploy/AGENTS.md](../deploy/AGENTS.md)

## 日志规范

所有工具调用都会记录日志，格式统一为 `[ToolCall] {工具名} - {操作描述}: {参数信息}`。

## 设计模式
- Builder模式：所有工具使用 Builder 创建，不依赖 Spring 管理
- 策略模式：WebSearchTool 通过 SearchProvider 接口支持搜索引擎切换
- 工具方法提取：共享方法（isIgnoredPath、countOccurrences、replaceFirst、generateEditSnippet、resolveWorkingDirectory）统一提取到 ToolUtils 类

## 共享工具类

### ToolUtils
包级工具类（package-private），提供工具间共享的方法。

**方法：**
- `isIgnoredPath(Path)`: 判断路径是否属于忽略目录（.git、node_modules、target、build、.idea、.vscode、dist、__pycache__、.gradle、.mvn），使用 Path API 实现跨平台兼容
- `resolveWorkingDirectory(String, Path)`: 解析工作目录，优先使用传入路径，其次使用配置的工作目录，最后使用系统当前目录
- `countOccurrences(String, String)`: 统计子字符串出现次数
- `replaceFirst(String, String, String)`: 替换第一个匹配项
- `replaceAll(String, String, String)`: 替换所有匹配项
- `generateEditSnippet(String, String)`: 生成编辑位置周围的上下文片段（带行号）

## 注意事项
1. 工具类不使用 @Component 注解，不由 Spring 管理
2. 所有工具在 AbstractAgent.init() 中通过 Builder 模式创建并注册到 ChatClient
3. MainAgent 通过 `buildTools()` 注册通用工具（WebFetch、WebSearch、Command、Task 等）
4. DoctorAgent 通过重写 `buildTools()` 注册专属工具（SkillConfig、McpConfig、MemoryManage、AppConfig 等）
5. 工具名称必须唯一
6. 需要外部依赖的工具通过 Builder 参数传入（如 CronTool 需要 CronManager，WebFetchTool 需要 ChatClient）
