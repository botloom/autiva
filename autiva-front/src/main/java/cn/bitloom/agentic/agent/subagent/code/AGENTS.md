# Code Subagent 包

## 概述
编码子智能体实现，从带有 YAML 前置元数据的 Markdown 文件解析子代理定义。这是 Autiva 的核心编码子智能体类型。

## 核心类

### CodeSubagentDefinition
从 Markdown 前置元数据解析的编码子代理定义。

**常量：**
- `KIND = "CODE"`: 类型标识符

**前置元数据字段：**
- `name`（必需）: 子代理名称
- `description`（必需）: 子代理描述
- `model`（可选）: 模型覆盖，支持 provider:model 格式和别名（opus/haiku/sonnet）
- `tools`（可选）: 允许的工具名称列表
- `disallowedTools`（可选）: 禁止的工具名称列表
- `skills`（可选）: 注入到上下文的技能名称
- `permissionMode`（可选）: 权限模式

### CodeSubagentExecutor
使用 Spring AI ChatClient 执行编码子代理任务。

**功能：**
- 支持多 ChatClient Builder（按 provider 分派）
- 工具过滤（allowed/disallowed）
- 技能内容注入到系统提示
- 模型名称映射（opus/haiku/sonnet → 完整模型名）

### CodeSubagentResolver
从 classpath/file URI 加载 Markdown 文件并解析为 CodeSubagentDefinition。

### CodeSubagentType
Builder 模式，配置编码子代理类型。

**Builder 参数：**
- `braveApiKey(String)`: Brave 搜索 API Key
- `chatClientBuilder(String, ChatClient.Builder)`: 按模型ID注册 ChatClient Builder
- `chatClientBuilders(Map)`: 批量注册 ChatClient Builder
- `skillsDirectories(List<String>)`: 技能目录列表

**默认子代理工具集：**
- TodoWriteTool、GrepTool、GlobTool、ShellTools、FileSystemTools、WebFetchTool
- WebSearchTool（如果配置了 braveApiKey）

### CodeSubagentReferences
工厂方法，从目录/资源发现 .md 代理文件。

**方法：**
- `fromDirectory(String)`: 从目录发现子代理
- `fromResource(Resource)`: 从资源发现子代理
- `fromResources(List<Resource>)`: 从多个资源发现子代理

## 资源文件
子代理 Markdown 文件位于 `~/.autiva/workspace/subagents/`：
- `GENERAL_PURPOSE_SUBAGENT.md`: 通用子代理
- `EXPLORE_SUBAGENT.md`: 探索子代理
- `PLAN_SUBAGENT.md`: 计划子代理
- `BASH_SUBAGENT.md`: Bash 子代理

## 设计模式
- 策略模式：不同子代理类型通过统一的接口执行
- 工厂模式：CodeSubagentType 将 Resolver 和 Executor 配对
