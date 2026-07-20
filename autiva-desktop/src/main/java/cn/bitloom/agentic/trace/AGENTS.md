# trace 包 — L4 结构化 Trace 记录

## 定位

L4 Hill Climbing Loop 的数据源。在 L1 Agent Loop 中通过 IAgentHook 机制
累积 Trace 数据（模型调用 + 工具调用 + L2 校验反馈），在对话轮次结束时落盘。

## 核心组件

```
trace/
├── Trace.java              ← Trace 数据结构 record
├── ToolCallRecord.java     ← 单次工具调用记录 record
├── TraceRecorder.java      ← Trace 持久化管理（@Component）
└── TraceHook.java          ← IAgentHook 实现，累积 Trace 并落盘（@Component）
```

## 数据流

```
L1 Agent Loop
   ↓ （HookAdvisor 注册 TraceHook）
TraceHook.beforeConversationRound  → 创建 Trace，存入 RuntimeContext.params["trace.current"]
   ↓
TraceHook.beforeToolCall           → 记录起始时间 + 输入参数到 ThreadLocal
TraceHook.afterToolCall            → 累积 ToolCallRecord 到 Trace
   ↓
TraceHook.afterModelCall           → 累积模型调用次数 + 记录 lastOutput
   ↓
VerificationHook（order=100）校验产生 Feedback
   ↓ 调用 traceHook.appendFeedback / markVerified
TraceHook.afterConversationRound   → 计算 duration + 调用 TraceRecorder.record 落盘
```

## Hook 顺序

| Hook | order | 职责 |
|------|-------|------|
| TraceHook | 50 | 创建 Trace + 累积工具调用 |
| VerificationHook | 100 | L2 校验 + 调用 traceHook.appendFeedback |

确保 TraceHook 在 VerificationHook 之前执行 beforeConversationRound，
保证 Trace 在 VerificationHook 写入 feedback 前已创建。

## Trace 数据结构

```java
record Trace(
    String traceId, String sessionId, String agentId, long timestamp,
    String userMessage,                    // 用户原始请求
    List<ToolCallRecord> toolCalls,        // 工具调用序列
    String finalOutput,                    // 最终产出
    int attemptCount,                      // 模型调用次数
    List<Feedback> feedbacks,              // L2 校验反馈
    boolean verified,                      // 是否通过 L2 校验
    String verifyMethod,                   // "deterministic" / "llm" / "skipped" / "deterministic+llm"
    long durationMs,                       // 总耗时
    long totalTokens                       // 暂记 0，后续接入 UsageAdvisor
)
```

## 存储

`~/.autiva/evolve/traces/{YYYY-MM-DD}/{sessionId}.jsonl`

- 按日期分目录，便于清理
- 每会话一个文件，JSONL 格式（每行一条 Trace）
- 默认保留 30 天（`EvolveConfig.traceRetentionDays`）

## TraceRecorder 关键方法

- `record(Trace)` — 落盘一条 Trace
- `loadRecent(agentId, limit)` — 加载指定 Agent 最近 N 条 Trace（按时间倒序）
- `loadBySession(sessionId)` — 加载指定会话的所有 Trace
- `stats(agentId, recentLimit)` — 统计校验通过率（totalTraces/passedTraces/failedTraces/passRate/totalToolCalls/blockedToolCalls）
- `cleanupOldTraces(retentionDays)` — 清理 N 天前的 Trace

## VerificationStats

供 UI 和 HillClimbingEngine 使用：
```java
record VerificationStats(
    int totalTraces, int passedTraces, int failedTraces,
    double passRate, int totalToolCalls, int blockedToolCalls
)
```

## 与其他包的关系

| 关联包 | 关系 |
|--------|------|
| `cn.bitloom.agentic.verify` | VerificationHook 调用 traceHook.appendFeedback / markVerified |
| `cn.bitloom.agentic.evolve.climb` | HillClimbingEngine 调用 traceRecorder.loadRecent / stats |
| `cn.bitloom.agentic.evolve` | EvolutionEngine 暴露 verificationStats / cleanupOldTraces 给 UI |

## 关键约束

1. **ThreadLocal 清理**：所有 ThreadLocal 在 finally 块清理，避免内存泄漏
2. **null 安全**：TraceHook 容错处理所有异常，不影响 L1 主流程
3. **appendFeedback 公共方法**：供 VerificationHook 写入 feedback，自动同步 ThreadLocal 和 ctx.params
4. **markVerified 公共方法**：供 VerificationHook 标记校验结果，决定 verifyMethod
5. **未启用 verification 时**：verifyMethod 保持 "skipped"，Trace 仍记录（无 feedbacks）
