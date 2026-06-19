# Task 包

## 概述
子代理任务管理，支持同步和后台执行模式。核心逻辑已融入 tool 包的 TaskTool 和 TaskOutputTool 中。

## 核心类

任务执行和输出获取的核心逻辑已迁移至 tool 包：
- **TaskTool**（`cn.bitloom.agentic.tool.core.TaskTool`）：子代理注册、任务执行、Session 集成、TaskCard 流式输出
- **TaskOutputTool**（`cn.bitloom.agentic.tool.core.TaskOutputTool`）：后台任务输出查询

详见 [tool/AGENTS.md](../tool/AGENTS.md)

## 子包

### repository 包 (`cn.bitloom.agentic.task.repository`)
后台任务仓库，详见 [repository/AGENTS.md](repository/AGENTS.md)
