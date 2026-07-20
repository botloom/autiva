# climb 包 — L4 爬山分析引擎

## 定位

L4 Hill Climbing Loop 的核心引擎。分析批量 Trace，发现高频缺陷，
输出优化建议并触发 GeneMutator 突变 Gene。

## 核心组件

```
climb/
├── HillClimbingEngine.java       ← L4 爬山分析引擎主类（@Component）
├── OptimizationSuggestion.java   ← 优化建议 record
└── ClimbingResult.java           ← 爬山结果 record
```

## 核心流程

```
HillClimbingEngine.climb(agentId)
   │
   ├── 1. TraceRecorder.loadRecent(agentId, 50)        ← 加载最近 50 条 Trace
   ├── 2. GeneStore.findByTarget(agentId)              ← 加载该 Agent 的 Gene 配置
   ├── 3. TraceRecorder.stats(agentId, 50)             ← 计算优化前通过率
   │
   ├── 4. LLM 分析（独立 analyzer Agent）
   │      输入：Traces + Genes + 通过率统计
   │      输出：JSON 数组 [OptimizationSuggestion, ...]
   │
   ├── 5. 对每个高置信度建议（confidence ≥ 0.7）
   │      ├── GeneMutator.mutate(original, issue, suggestion)
   │      ├── EvolutionSafety.check(original, mutated)
   │      ├── GeneStore.appendEvent(EvolutionEvent)
   │      └── 跳过低置信度/不存在/突变失败/安全未通过
   │
   └── 返回 ClimbingResult
```

## Analyzer Agent

独立的分析 Agent，不复用主 Agent 上下文：
- agentId：`hill-climber`
- 模型：DEEPSEEK
- system prompt：分析引擎角色，要求输出严格 JSON 数组
- 懒加载：首次调用时通过 `getAnalyzerAgent()` 双重检查锁创建
- verification 关闭（不需要 L2 校验自身产出）

## OptimizationSuggestion

```java
record OptimizationSuggestion(
    String geneId,         // 目标 Gene ID
    GeneType geneType,     // PROMPT / TOOL_DESC / RUBRIC / SKILL_CONFIG
    String targetId,       // agent 名 / 工具名 / grader 名 / 技能名
    String issue,          // 分析发现的问题（具体、可量化）
    String suggestion,     // 优化建议（具体、可执行）
    double confidence      // 置信度 [0,1]
)
```

## ClimbingResult

```java
record ClimbingResult(
    String agentId,
    int traceCount,                    // 分析的 Trace 数量
    String analysisText,               // LLM 原始分析文本（供 UI 展示）
    List<OptimizationSuggestion> suggestions,  // 全部建议（含未应用）
    List<EvolutionEvent> appliedEvents,        // 已应用的进化事件
    int skippedCount,                  // 跳过的建议数量
    double passRateBefore              // 优化前的 L2 校验通过率
)
```

## 触发方式

1. **手动触发**：UI 上"分析优化"按钮 → `EvolutionEngine.climb(agentId)`
2. **定时触发**：每天首次启动时分析昨天的 Trace（阶段4 实现）
3. **阈值触发**：累积 N 次失败校验后自动触发（阶段4 实现）

## 关键约束

1. **置信度阈值**：低于 `EvolveConfig.experienceConfidenceThreshold`（默认 0.7）的建议不自动应用
2. **Gene 必须存在**：建议指向的 geneId 在 GeneStore 中不存在则跳过
3. **安全检查必须通过**：内容长度增长、突变频率等限制
4. **GeneMutator 自动 upsert**：突变成功后自动写入 GeneStore + JGit 提交
5. **EvolutionEvent 记录**：每次成功应用的建议都生成事件，写入 events.jsonl

## 与其他包的关系

| 关联包 | 关系 |
|--------|------|
| `cn.bitloom.agentic.trace` | TraceRecorder 提供 Trace 数据源 |
| `cn.bitloom.agentic.evolve.mutation` | GeneMutator 执行实际突变 |
| `cn.bitloom.agentic.evolve.safety` | EvolutionSafety 安全检查 |
| `cn.bitloom.agentic.evolve.gene` | GeneStore 加载/查询 Gene |
| `cn.bitloom.agentic.evolve` | EvolutionEngine 作为对外编排入口 |
