# Task 包

## 概述
子代理任务管理，支持同步和后台执行模式。核心逻辑在 TaskManager 中，TaskTool 和 TaskOutputTool 保留为底层实现。

## 核心类

### TaskManager
任务管理器，统一负责子代理注册、任务执行、后台任务管理和 AI 工具回调构建。Spring Bean。

**注册方法：**
- `registerSubagentTypes(List<SubagentType>)`: 注册子代理类型
- `registerSubagentTypes(SubagentType...)`: 注册子代理类型
- `registerSubagentReferences(List<SubagentReference>)`: 注册子代理引用
- `registerSubagentReferences(SubagentReference...)`: 注册子代理引用

**执行方法：**
- `executeTask(TaskCall)`: 执行任务（同步或后台）
- `getTaskOutput(taskId, block, timeout)`: 获取后台任务输出

**AI 工具方法：**
- `buildTaskToolCallback()`: 构建 Task 工具回调
- `buildTaskOutputToolCallback()`: 构建 TaskOutput 工具回调
- `buildToolCallbacks()`: 构建所有工具回调

**内置 Claude 子代理引用：**
当注册 Claude 类型的子代理时，自动注册以下引用：
- `classpath:/agent/CODE_SUBAGENT.md`
- `classpath:/agent/GENERAL_PURPOSE_SUBAGENT.md`
- `classpath:/agent/EXPLORE_SUBAGENT.md`
- `classpath:/agent/PLAN_SUBAGENT.md`
- `classpath:/agent/BASH_SUBAGENT.md`

**Task 工具描述特点：**
- 明确告知主智能体"你没有文件读写和Shell执行的能力"
- 提供子智能体选择规则（编码→Code、探索→Explore、计划→Plan、Shell→Bash）
- 收窄"何时不使用Task工具"的范围，仅限 WebFetch、TodoWrite、AskUserQuestion
- 示例改为展示如何通过 Task 工具委派给子智能体

**内部类：**
- `TaskFunction`: 实现 Function<TaskCall, String>，委托 TaskManager
- `TaskOutputFunction`: 实现 Function<TaskOutputCall, String>，委托 TaskManager
- `TaskOutputCall`: 后台任务输出查询 record

### TaskTool
底层任务工具实现，保留原始 Builder 模式供 TaskManager 内部使用。

### TaskOutputTool
底层后台任务输出工具，保留原始 Builder 模式供 TaskManager 内部使用。

## 子包

### claude 包 (`cn.bitloom.agentic.tool.task.claude`)
Claude 风格子代理实现，详见 [claude/AGENTS.md](claude/AGENTS.md)

### repository 包 (`cn.bitloom.agentic.tool.task.repository`)
后台任务仓库，详见 [repository/AGENTS.md](../../task/repository/AGENTS.md)
