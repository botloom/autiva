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
- `classpath:/agent/BASH_SUBAGENT.md`

**Task 工具描述特点：**
- 提供子智能体选择规则（Shell→Bash）
- 收窄"何时不使用Task工具"的范围，仅限 WebFetch、TodoWrite、AskUserQuestion
- 示例改为展示如何通过 Task 工具委派给 Bash 子智能体

**内部类：**
- `TaskFunction`: 实现 Function<TaskCall, String>，委托 TaskManager
- `TaskOutputFunction`: 实现 Function<TaskOutputCall, String>，委托 TaskManager
- `TaskOutputCall`: 后台任务输出查询 record
- `SessionAwareTaskToolCallback`: 包装 FunctionToolCallback，从 ToolContext 提取 sessionId 自动注入到 TaskCall

**Session 集成机制：**
Task 工具通过 `SessionAwareTaskToolCallback` 实现与 Session 的集成：
1. MainAgent 在调用 ChatClient 时设置 `toolContext(Map.of("sessionId", sessionId))`
2. `SessionAwareTaskToolCallback.call(String, ToolContext)` 从 ToolContext 提取 sessionId
3. 将 sessionId 注入到 TaskCall 的 JSON 输入中
4. 子智能体执行器使用 sessionId 构建会话感知的 conversationId，实现同一会话内对话记忆共享

**Session Fork 机制：**
Task 调用时自动 fork 子会话，实现主子会话的父子关系：
1. `executeTask()` 调用 `sessionManager.forkSession(parentSessionId, subagentType)` 创建子会话
2. 子会话 ID 格式：`{parentSessionId}_{自增序号}`
3. 子会话的 `parentId` 指向主会话，`target` 记录子智能体类型
4. TaskCard ID 使用子会话 ID，确保 UI 卡片与子会话一一对应

**TaskCard 流式输出机制：**
子智能体执行时通过 ToolUIBridge 实时推送输出到 TaskCard：
1. `executeTask()` 调用 `toolUIBridge.createTaskCard(taskCardId, taskJson)` 创建 UI 卡片
2. 子智能体执行器通过 `onChunk` 回调实时输出文本
3. `TaskManager` 将 chunk 通过 `toolUIBridge.appendTaskOutput(taskCardId, chunk)` 推送到 UI
4. 执行完成后调用 `toolUIBridge.completeTaskCard(taskCardId, null)` 更新状态
5. 执行失败时调用 `toolUIBridge.failTaskCard(taskCardId, error)` 显示错误

### TaskTool
底层任务工具实现，保留原始 Builder 模式供 TaskManager 内部使用。

### TaskOutputTool
底层后台任务输出工具，保留原始 Builder 模式供 TaskManager 内部使用。

## 子包

### claude 包 (`cn.bitloom.agentic.tool.task.claude`)
Claude 风格子代理实现，详见 [claude/AGENTS.md](claude/AGENTS.md)

### repository 包 (`cn.bitloom.agentic.tool.task.repository`)
后台任务仓库，详见 [repository/AGENTS.md](../../task/repository/AGENTS.md)
