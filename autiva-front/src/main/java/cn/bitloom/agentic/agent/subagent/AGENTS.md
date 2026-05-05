# Subagent 包

## 概述
本包定义了子代理系统的核心抽象接口，采用策略模式设计。来自 spring-ai-agent-utils 项目的 common 模块。

子智能体配置存放在 `~/.autiva/workspace/subagents/` 目录中，以 .md 文件形式存在，支持 YAML frontmatter 元数据。首次启动时从 classpath 的 `agent/` 目录复制默认配置。

## 核心接口

### SubagentDefinition
定义子代理的身份和配置元数据。

**方法：**
- `getName()`: 返回子代理的唯一名称
- `getDescription()`: 返回子代理能力的描述
- `getKind()`: 返回类型标识符（如 "CODE"、"A2A"）
- `reference()`: 返回用于解析此定义的引用
- `toSubagentRegistrations()`: 格式化注册显示，默认格式为 `- **name**: description`

### SubagentExecutor
为特定子代理类型执行子代理任务。

**方法：**
- `getKind()`: 返回此执行器处理的子代理类型
- `execute(TaskCall, SubagentDefinition)`: 使用指定的子代理定义执行任务

### SubagentResolver
将子代理引用解析为完整定义。

**方法：**
- `canResolve(SubagentReference)`: 检查此解析器是否可以处理给定的引用
- `resolve(SubagentReference)`: 将引用解析为完整的子代理定义

### SubagentType
将子代理解析器与其执行器配对的 record。

**字段：**
- `resolver`: SubagentResolver
- `executor`: SubagentExecutor

**方法：**
- `kind()`: 从执行器返回类型标识符

### SubagentReference
子代理定义资源的引用 record。

**字段：**
- `uri`: 资源URI（classpath或文件路径）
- `kind`: 子代理类型（如 "CODE"）
- `metadata`: 可选的键值元数据

### TaskCall
任务调用参数 record。

**字段：**
- `description`: 任务的简短描述
- `prompt`: 代理要执行的任务
- `subagent_type`: 用于此任务的专门代理类型
- `model`: 可选模型覆盖
- `resume`: 可选恢复代理ID（用于继续之前的子智能体会话）
- `run_in_background`: 是否在后台运行
- `sessionId`: 当前会话ID（由系统通过 ToolContext 自动注入，LLM 无需填写）

## 子包

### code 包 (`cn.bitloom.agentic.agent.subagent.code`)
编码子代理实现，详见 [code/AGENTS.md](code/AGENTS.md)

### a2a 包 (`cn.bitloom.agentic.agent.subagent.a2a`)
A2A 协议子代理实现，详见 [a2a/AGENTS.md](a2a/AGENTS.md)

## 设计模式
- 策略模式：不同子代理类型通过统一的接口执行
- 工厂模式：SubagentType 将 Resolver 和 Executor 配对
