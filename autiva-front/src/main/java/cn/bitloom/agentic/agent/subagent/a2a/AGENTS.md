# A2A Subagent 包

## 概述
本包实现了 Google A2A (Agent-to-Agent) 协议的子代理支持。来自 spring-ai-agent-utils 项目的 a2a 模块。

## 核心类

### A2ASubagentDefinition
A2A 协议子代理定义，封装 AgentCard。

**常量：**
- `KIND = "A2A"`: 类型标识符

**方法：**
- `getName()`: 返回代理名称（来自 AgentCard）
- `getDescription()`: 返回代理描述（来自 AgentCard）
- `getKind()`: 返回 "A2A"
- `getReference()`: 返回 SubagentReference
- `getAgentCard()`: 获取 AgentCard

### A2ASubagentExecutor
通过 A2A 协议执行远程代理任务。

**功能：**
- 使用 A2A SDK 的 Client 发送消息到远程代理
- 使用 JSON-RPC 传输
- 支持 60 秒超时
- 从 Task 工件中提取文本响应

### A2ASubagentResolver
从远程端点获取 AgentCard 解析 A2A 子代理。

**常量：**
- `WELL_KNOWN_AGENT_CARD_PATH = "/.well-known/agent-card.json"`: 默认 AgentCard 路径

**方法：**
- `canResolve(SubagentReference)`: 检查引用类型是否为 "A2A"
- `resolve(SubagentReference)`: 从远程端点获取 AgentCard 并创建定义

## 依赖
- `io.github.a2asdk:a2a-java-sdk-client`: A2A Java SDK 客户端
- `io.github.a2asdk:a2a-java-sdk-client-transport-jsonrpc`: JSON-RPC 传输

## 参考链接
- [A2A 协议规范](https://google.github.io/A2A/)
