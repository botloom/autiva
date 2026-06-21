# Memory 包

## 概述
本包实现了对话记忆管理和持久化记忆服务，实现 Spring AI 的 ChatMemory 接口，通过 FileSystemSessionManager 进行消息的持久化和加载，并提供游标感知的消息获取功能。统一记忆管理服务由 `MemoryManager` 提供，支持智能体主动记忆（工具调用）和事件驱动记忆（上下文压缩）。

## 记忆架构（两层）

1. **热记忆（memory.md）**：自动注入上下文窗口，智能体通过工具主动读写
2. **上下文桥接（session.summary）**：压缩后的早期对话摘要，由 ProactiveContextAdvisor 注入

## 核心类

### CompactChatMemory
实现 Spring AI 的 ChatMemory 接口，提供游标感知的对话记忆管理。

**依赖注入：**
- `FileSystemSessionManager`: 会话管理器

**核心方法：**
- `add(conversationId, messages)`: 添加消息到会话，追加前检查上一条是否是孤儿 AssistantMessage(tool_calls)（若本次 add 无 ToolResponseMessage 则删除上一条）
- `get(conversationId)`: 获取会话消息（延迟加载，只返回 Session.memoryCursor 之后的消息），返回前检查最后一条是否是孤儿 AssistantMessage(tool_calls) 并过滤
- `clear(conversationId)`: 清除会话消息

**工具调用消息成对性保障（边界检查策略）：**
DeepSeek 等模型严格要求 `AssistantMessage(tool_calls)` 后必须紧跟对应的 `ToolResponseMessage`，否则返回 400 Bad Request。采用边界检查策略避免遍历整个历史列表：
- `add()` 时：检查上一条是否是孤儿 `AssistantMessage(tool_calls)`，若本次 add 无 `ToolResponseMessage` 则删除上一条
- `get()` 时：只检查最后一条是否是孤儿 `AssistantMessage(tool_calls)`，若是则过滤掉

**conversationId：**
直接使用 sessionId，格式为 `{agentId}-{type}-{source}-{target}`

**游标感知机制：**
- `get()` 方法从 Session 获取 memoryCursor，只返回游标之后的消息
- 游标前的消息以压缩摘要形式通过 ProactiveContextAdvisor 注入 system prompt
- `currentContextLength` 由 `UsageAdvisor` 维护（不再由 CompactChatMemory 估算）

### MemoryManager
统一记忆管理服务。所有记忆工具通过它操作，Session 处理 MemoryEvent 时也调用它。按 agentId 解析路径，不再硬编码 "default"。

**Spring 注解：** `@Component`，通过 `@RequiredArgsConstructor` 注入 `ModelFactory`

**存储路径：**
- memory.md：`~/.autiva/agents/{agentId}/memory.md`
- transcripts：`~/.autiva/workspace/{agentId}/transcripts/`

**核心方法（5 个）：**
- `save(agentId, content)`: 追加记忆到 memory.md
- `update(agentId, section, content)`: 更新 memory.md 的指定 `## 区块`（用户画像/关键偏好/近期事件/例行提醒）
- `delete(agentId, section)`: 清空 memory.md 指定区块
- `search(agentId, query, limit)`: 搜索 memory.md，返回格式化结果列表
- `compact(agentId, sessionId, messages, existingSummary)`: 异步上下文压缩，调用 LLM 生成摘要并合并已有摘要

**辅助方法：**
- `resolveAgentId(sessionId)`: 从 sessionId 解析出 agentId（格式 `{agentId}-{type}-{source}-{userId}-{timestamp}`）
- `replaceSection(content, sectionName, newContent)`: 替换 memory.md 中指定 `## 区块` 的内容
- `callLlm(prompt)`: 调用 LLM 生成文本（用于压缩和整理）

## 记忆处理流程

### 添加消息流程
```
CompactChatMemory.add(conversationId, messages)
         │
         ├── 1. 清理孤儿工具调用消息
         └── 2. sessionManager.store(conversationId, messages)
                   └── 写入 ~/.autiva/workspace/{agentId}/sessions/{sessionId}/messages.jsonl
```

### 获取消息流程
```
CompactChatMemory.get(conversationId)
         │
         ├── 1. 从 sessionManager.getById() 获取 Session
         ├── 2. 从 Session 获取 memoryCursor
         └── 3. 取游标之后的消息（subList(cursor, size)），检查最后一条是否是孤儿 AssistantMessage(tool_calls)
```

## 事件驱动压缩策略

### 触发机制
上下文压缩由 `UsageAdvisor` 触发，不再由 `MemoryCompactAdvisor` 同步处理：

1. `UsageAdvisor`（order=50）从模型响应的 `Usage` 中提取 `promptTokens`
2. 更新 `Session.currentContextLength`（语义为 token 数）
3. 当 `promptTokens >= maxTokens * compactionThreshold` 时，发布 `MemoryEvent.contextCompact()` 到 inBox
4. `Session.start()` 中 MemoryEvent 分支通过 `Schedulers.boundedElastic()` 异步处理，不阻塞对话流
5. 调用 `MemoryManager.compact()` 生成新摘要，推进 `memoryCursor`，重置 `currentContextLength`

### 压缩流程
```
UsageAdvisor 检测超阈值
         │
         └── 发布 MemoryEvent.CONTEXT_COMPACT 到 inBox
                   │
                   └── SessionRunner.handleContextCompact()（异步，boundedElastic）
                             │
                             ├── 1. MemoryManager.compact()：
                             │       ├── 保存原始消息到 transcripts
                             │       ├── 调用 LLM 生成摘要
                             │       └── 合并已有摘要
                             ├── 2. 更新 Session.summary
                             ├── 3. 推进 memoryCursor = messages.size()
                             └── 4. 重置 currentContextLength = 0
```

**默认配置：**
- contextCapacity: 64000（DEEPSEEK）/ 32000（QWEN）
- compactionThreshold: 0.8

## 智能体主动记忆
智能体通过 4 个记忆工具主动读写记忆（详见 tool/AGENTS.md）：
- `memory_save`: 追加记忆到 memory.md
- `memory_update`: 更新 memory.md 指定区块
- `memory_delete`: 清空 memory.md 指定区块
- `memory_search`: 搜索 memory.md

所有工具通过 `MemoryManager.resolveAgentId(sessionId)` 解析 agentId，委托 MemoryManager 执行。

## 设计模式
- 代理模式：CompactChatMemory 代理 FileSystemSessionManager 进行消息管理
- 游标感知：CompactChatMemory.get() 只返回游标后的消息
- 事件驱动：压缩通过 MemoryEvent 异步触发，不阻塞对话流
- 主动记忆：智能体通过工具主动读写记忆，替代旧版自动召回

## 注意事项
1. conversationId 直接使用 sessionId
2. 消息持久化由 FileSystemSessionManager 负责
3. 上下文压缩由 UsageAdvisor 触发 MemoryEvent，SessionRunner 异步处理
4. currentContextLength 语义为 token 数，由 UsageAdvisor 从模型响应 Usage 中提取
5. CompactChatMemory 通过 MessageChatMemoryAdvisor 注册到 ChatClient，历史消息自动注入
6. 游标前的消息以摘要形式通过 ProactiveContextAdvisor 注入
7. MemoryManager 是 Spring @Component，按 agentId 解析路径，不硬编码 "default"
8. 旧版 MemorySearchService、MemoryCompactAdvisor、LongMemoryConsolidateAdvisor 已移除
9. 日流水账（memory/YYYY-MM-DD.md）和 consolidate() 已移除，记忆架构简化为两层
