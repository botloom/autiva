# Memory 包

## 概述
本包实现了对话记忆管理和持久化记忆服务。对齐 netInsight 的设计思想，采用 **per-session ChatMemory + 轮次缓冲** 架构：每个 Session 拥有独立的 ChatMemory 实例（非 Spring Bean），由 SessionRunner 在每轮对话结束时批量 flush 持久化。统一记忆管理服务由 `MemoryManager` 提供，支持智能体主动记忆（工具调用）和事件驱动记忆（上下文压缩）。

## 记忆架构（两层）

1. **热记忆（memory.md）**：自动注入上下文窗口，智能体通过工具主动读写
2. **上下文桥接（session.summary）**：压缩后的早期对话摘要，由 ProactiveContextAdvisor 注入

## 核心类

### TurnBufferedChatMemory
支持轮次缓冲的 ChatMemory 接口，扩展 Spring AI ChatMemory。对齐 netInsight 的 TurnBufferedChatMemory 设计，存储介质改为文件系统。

**核心方法：**
- `setCurrentMessageId(String messageId)`: 设置当前轮次 messageId（由 SessionRunner 在每轮开始时调用）
- `flush()`: 本轮对话结束时批量持久化到文件系统（events.jsonl），由 SessionRunner 在 doOnComplete/onErrorResume/BLOCK 返回后调用
- `getHistoryFromFile()`: 只查文件历史消息（不含本轮缓冲区），供压缩使用
- `countFileMessages()`: 文件历史消息总数（供压缩推进游标用）

### FileChatMemory
会话级 ChatMemory 实现（per-session 实例，非 Spring Bean），实现 TurnBufferedChatMemory 接口。对齐 netInsight 的 DbChatMemory 设计，存储介质改为文件系统。由 `FileSystemSessionManager.activate()` 为每个 Session 手动创建。

**依赖（构造参数）：**
- `FileSystemSessionManager sessionManager`: 会话管理器
- `Session session`: 绑定的会话实例

**核心字段：**
- `currentTurnBuffer`: `List<Message>` 本轮对话缓冲区（未持久化的消息）
- `currentMessageId`: 当前轮次 messageId（由 SessionRunner 设置）

**核心方法：**
- `add(sessionId, messages)`: 缓冲消息到内存（不立即写文件），同时实时广播 ToolCalls/ToolResponse 事件到 EventBus.publishOut（前端实时显示）。清理前一条孤儿工具调用消息（边界检查）
- `get(sessionId)`: 获取消息 = 文件历史（游标后） + 本轮缓冲区。MessageChatMemoryAdvisor 在 Agent 推理时调用此方法获取历史消息
- `flush()`: 本轮对话结束时批量持久化到 events.jsonl，每条消息带上当前 messageId，便于按轮次区分
- `clear(sessionId)`: 清空缓冲区 + 清空 events.jsonl
- `getHistoryFromFile()`: 只查文件历史消息（不含缓冲区），供压缩使用
- `countFileMessages()`: 文件历史消息总数（供压缩推进游标用）

**工具调用消息成对性保障（边界检查策略）：**
DeepSeek 等模型严格要求 `AssistantMessage(tool_calls)` 后必须紧跟对应的 `ToolResponseMessage`，否则返回 400 Bad Request。采用边界检查策略：
- `add()` 时：若上一条缓冲消息是含 tool_calls 的 AssistantMessage，且本次追加的不是 ToolResponseMessage，则移除上一条（孤儿工具调用清理）

**轮次缓冲机制：**
- 本轮对话消息先缓冲到 `currentTurnBuffer`，不立即写文件
- 本轮结束后由 SessionRunner 调用 `flush()` 批量持久化到 events.jsonl
- 解决 tool_call/tool 不对称问题：本轮异常则整轮不入库，缓冲区被清空
- 实时广播 ToolCalls/ToolResponse 事件，前端无需等待 flush 即可显示

### InMemoryChatMemory
子智能体专用 ChatMemory（内存版，不持久化到文件）。对齐 netInsight 的 InMemoryChatMemory 设计。实现 TurnBufferedChatMemory 接口以兼容 SessionRunner 的轮次缓冲协议（子智能体也走 SessionRunner + EventBus 模式）。**per-session 实例**（非 Spring Bean，由 InMemorySessionManager.activate() 为每个子智能体独立 new 创建，不共享，对齐 FileChatMemory 的 per-session 模式）。

**与 FileChatMemory 的区别：**
- 不依赖磁盘持久化
- 不做游标感知（子智能体不需要上下文压缩）
- 不做孤儿消息检查（子智能体生命周期短）
- 内部维护 `messageCache`（ConcurrentHashMap）替代委托 InMemorySessionManager
- flush/getHistoryFromFile/countFileMessages 为 no-op/空（子智能体消息已实时广播且无文件历史，仅为兼容 TurnBufferedChatMemory 协议）

**核心字段：**
- `messageCache`: `ConcurrentHashMap<String, List<Message>>` 消息存储
- `currentMessageId`: 当前轮次 messageId（由 SessionRunner 设置，用于广播时填充 event.messageId）

**核心方法：**
- `add(sessionId, messages)`: 追加消息到 messageCache，同时广播 ToolCalls/ToolResponse 事件（前端实时显示）
- `get(sessionId)`: 从 messageCache 返回全部消息（无游标）
- `clear(sessionId)`: 清空 messageCache 中的消息
- `setCurrentMessageId(messageId)`: 设置当前轮次 messageId（由 SessionRunner 在每轮开始时调用）
- `flush()`: no-op（子智能体消息已实时广播且纯内存存储，无需批量持久化，仅为兼容 SessionRunner.safeFlush 调用）
- `getHistoryFromFile()`: 返回空列表（子智能体无文件历史）
- `countFileMessages()`: 返回 0（子智能体无文件历史）

### MemoryManager
统一记忆管理服务。所有记忆工具通过它操作，SessionRunner 处理 MemoryEvent 时也调用它。按 agentId 解析路径，不再硬编码 "default"。

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

### 添加消息流程（本轮缓冲）
```
FileChatMemory.add(sessionId, messages)
         │
         ├── 1. 清理前一条孤儿工具调用消息（边界检查）
         ├── 2. 缓冲消息到 currentTurnBuffer
         └── 3. 实时广播 ToolCalls/ToolResponse 事件到 EventBus.publishOut
                   └── 前端实时显示工具调用过程
```

### flush 持久化流程（本轮结束）
```
SessionRunner.safeFlush()
         │
         └── FileChatMemory.flush()
                   │
                   ├── 1. 遍历 currentTurnBuffer，转为 MessageEvent
                   ├── 2. 每条消息带上当前 messageId
                   ├── 3. sessionManager.storeEvents(sessionId, events)
                   │       └── 持久化到 ~/.autiva/workspace/{agentId}/sessions/{sessionId}/events.jsonl
                   └── 4. 清空 currentTurnBuffer
```

### 获取消息流程
```
FileChatMemory.get(sessionId)
         │
         ├── 1. getHistoryFromFile()：从 events.jsonl 的 memoryCursor 行开始读取所有未压缩事件
         └── 2. 合并 currentTurnBuffer（本轮缓冲区）
                   └── 返回 文件历史 + 本轮缓冲区
```

## 事件驱动压缩策略

### 触发机制
上下文压缩由 `UsageAdvisor` 触发，不再由 `MemoryCompactAdvisor` 同步处理：

1. `UsageAdvisor`（order=50）从模型响应的 `Usage` 中提取 `promptTokens`
2. 更新 `Session.currentContextLength`（语义为 token 数）
3. 当 `promptTokens >= maxTokens * compactionThreshold` 时，发布 `MemoryEvent.contextCompact()` 到 inBox
4. `SessionRunner.start()` 中 MemoryEvent 分支通过 `Schedulers.boundedElastic()` 异步处理，不阻塞对话流
5. 调用 `MemoryManager.compact()` 生成新摘要，推进 `memoryCursor` 到文件消息总数，重置 `currentContextLength`

### 压缩流程
```
UsageAdvisor 检测超阈值
         │
         └── 发布 MemoryEvent.CONTEXT_COMPACT 到 inBox
                   │
                   └── SessionRunner.handleContextCompact()（异步，boundedElastic）
                             │
                             ├── 1. chatMemory.getHistoryFromFile()：只取文件历史消息（不含缓冲区）
                             ├── 2. MemoryManager.compact()：
                             │       ├── 保存原始消息到 transcripts
                             │       ├── 调用 LLM 生成摘要
                             │       └── 合并已有摘要
                             ├── 3. 更新 Session.summary
                             ├── 4. 推进 memoryCursor = chatMemory.countFileMessages()
                             ├── 5. 重置 currentContextLength = 0
                             └── 6. sessionManager.persistSession(session)
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
- **per-session 实例模式**：FileChatMemory 和 InMemoryChatMemory 非 Spring Bean，由 SessionManager 为每个 Session 手动创建
- **轮次缓冲模式**：FileChatMemory 缓冲本轮消息到内存，flush 时批量持久化，解决并行任务消息混乱问题
- **游标感知**：FileChatMemory.get() 只返回游标后的消息 + 本轮缓冲区
- **事件驱动**：压缩通过 MemoryEvent 异步触发，不阻塞对话流
- **主动记忆**：智能体通过工具主动读写记忆，替代旧版自动召回

## 注意事项
1. FileChatMemory 是 per-session 实例，非 Spring Bean，由 FileSystemSessionManager.activate() 创建
2. InMemoryChatMemory 是 per-session 实例，非 Spring Bean，由 InMemorySessionManager.activate() 为每个子智能体独立 new 创建（不共享，对齐 FileChatMemory 的 per-session 模式）
3. 消息持久化由 FileChatMemory.flush() 批量写入，非逐条写入
4. 上下文压缩由 UsageAdvisor 触发 MemoryEvent，SessionRunner 异步处理
5. currentContextLength 语义为 token 数，由 UsageAdvisor 从模型响应 Usage 中提取
6. FileChatMemory 通过 MessageChatMemoryAdvisor 注册到 ChatClient，历史消息自动注入
7. 游标前的消息以摘要形式通过 ProactiveContextAdvisor 注入
8. MemoryManager 是 Spring @Component，按 agentId 解析路径，不硬编码 "default"
9. 旧版 CompactChatMemory 已移除，由 FileChatMemory 替代
10. 日流水账（memory/YYYY-MM-DD.md）和 consolidate() 已移除，记忆架构简化为两层
