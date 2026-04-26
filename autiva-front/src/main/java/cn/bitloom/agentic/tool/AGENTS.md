# Tool 包

## 概述
本包实现了工具系统，为智能体提供可调用的工具集。所有工具统一使用两种注册方式：
1. **Builder模式（优先）**：工具类通过Builder模式创建实例，使用 `@Tool` 注解标记工具方法，通过 `defaultTools()` 注册到 ChatClient
2. **ToolCallbacks模式（次选）**：通过 `FunctionToolCallback.builder()` 构建函数式工具回调，通过 `defaultToolCallbacks()` 注册到 ChatClient

## 工具注册方式

### Builder模式（优先）
工具类使用Builder模式创建，不依赖Spring管理。在MainAgent的`@PostConstruct`中直接创建并注册：

```java
FileSystemTools fileSystemTools = FileSystemTools.builder().build();
CronTool cronTool = CronTool.builder(cronManager).build();

chatClientBuilder
    .defaultTools(fileSystemTools, shellTools, webFetchTool, askUserQuestionTool, todoWriteTool, cronTool)
    .build();
```

### ToolCallbacks模式（次选）
对于需要动态描述或函数式实现的工具，使用FunctionToolCallback：

```java
chatClientBuilder
    .defaultToolCallbacks(taskManager.buildToolCallbacks())
    .build();
```

## Builder模式工具

### FileSystemTools
文件系统操作工具（Builder模式创建）。
- `Read`: 读取文件，支持 offset/limit 分页，行号格式输出
- `Write`: 写入/覆盖文件，自动创建父目录
- `Edit`: 精确字符串替换，支持 replace_all

### ShellTools
Shell 命令执行工具（Builder模式创建）。
- `Bash`: 执行命令，支持后台运行、超时、Windows/Unix 跨平台
- `BashOutput`: 获取后台 Shell 输出，支持正则过滤
- `KillShell`: 终止后台 Shell
- **编码处理**：通过 `getConsoleCharset()` 自动检测平台控制台编码，Windows 下使用 `sun.jnu.encoding` 系统属性（通常为 GBK）读取输出，Linux/macOS 下使用 UTF-8，解决中文乱码问题

### GlobTool
文件模式匹配工具（纯 Java NIO.2 实现，Builder模式创建）。
- 支持 glob 模式（如 `**/*.java`）
- 按修改时间排序，忽略 .git/node_modules/target 等目录

### GrepTool
正则表达式内容搜索工具（纯 Java regex 实现，Builder模式创建）。
- 三种输出模式：files_with_matches、count、content
- 支持上下文行、行号、大小写不敏感、多行模式
- 支持文件类型过滤（java、js、py、rust 等）

### WebFetchTool
智能网页获取+AI摘要工具（Builder模式创建，需要 ChatClient）。
- HTTP 获取 + HTML 转 Markdown（Flexmark）+ AI 摘要
- 15 分钟缓存，域名安全检查
- 自动重试（指数退避），字符集检测

### WebSearchTool
Brave 搜索引擎网络搜索工具（Builder模式创建，需要 Brave API Key）。
- 支持域名过滤（allowedDomains/blockedDomains）
- 返回结构化搜索结果

### ListDirectoryTool
目录列表工具（Builder模式创建）。
- 递归深度控制，结果数量限制
- 跳过噪音目录，目录优先排序

### TodoWriteTool
结构化任务列表管理工具（Builder模式创建）。
- 验证规则：恰好一个 in_progress 任务
- 状态：pending、in_progress、completed
- 通过 `todoEventHandler` 回调与 UI 交互

### AskUserQuestionTool
向用户提问澄清问题的工具（Builder模式创建，需要 QuestionHandler）。
- 支持多选/单选问题，1-4个问题，每个2-4个选项

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

## ToolCallbacks模式工具

### Task / TaskOutput
子代理任务工具，通过 TaskManager.buildToolCallbacks() 构建。
- `Task`: 启动子代理执行任务
- `TaskOutput`: 获取子代理任务输出

### Skill
技能加载和执行工具，通过 SkillManager.buildToolCallback() 构建。
- 从 SKILL.md 文件加载技能定义
- 函数式工具回调（FunctionToolCallback）

## 子包

### task 包 (`cn.bitloom.agentic.tool.task`)
子代理任务工具，详见 [task/AGENTS.md](task/AGENTS.md)

### deploy 包 (`cn.bitloom.agentic.deploy`)
项目部署工具，详见 [deploy/AGENTS.md](../deploy/AGENTS.md)

## 日志规范

所有工具调用都会记录日志，格式统一为 `[ToolCall] {工具名} - {操作描述}: {参数信息}`。

## 设计模式
- Builder模式：所有工具使用 Builder 创建，不依赖 Spring 管理
- 策略模式：不同工具实现统一的 ToolCallback 接口
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
2. 所有工具在 MainAgent.init() 中通过 Builder 模式创建并注册到 ChatClient
3. 工具名称必须唯一
4. 需要外部依赖的工具通过 Builder 参数传入（如 CronTool 需要 CronManager，WebFetchTool 需要 ChatClient）
