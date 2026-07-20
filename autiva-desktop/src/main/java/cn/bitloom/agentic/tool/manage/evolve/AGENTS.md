# manage/evolve 包 — L4 自优化管理工具

## 定位

本包提供 4 个工具，让 L1 Agent 通过工具调用主动触发 L4 自优化能力。
所有工具委托 `EvolutionEngine`（@Component）执行实际逻辑，工具自身只做参数解析和结果封装。

## 核心组件

```
manage/evolve/
├── ClimbAnalyzeTool.java    ← 主动触发 L4 爬山自优化分析
├── GeneQueryTool.java       ← 查询 Gene 列表/详情
├── GeneToggleTool.java      ← 启用/禁用 Gene
└── GeneRollbackTool.java    ← 回滚 Gene 到历史 JGit 提交
```

## 工具一览

| 工具名 | name | 作用 | Input 字段 |
|--------|------|------|-----------|
| ClimbAnalyzeTool | `climb_analyze` | 触发 L4 爬山分析 | `agent_id?` |
| GeneQueryTool | `gene_query` | 查询 Gene 列表/详情 | `gene_id?`, `type?` |
| GeneToggleTool | `gene_toggle` | 启用/禁用 Gene | `gene_id`, `enabled` |
| GeneRollbackTool | `gene_rollback` | 回滚 Gene 到历史版本 | `gene_id`, `commit_hash` |

## 设计要点

1. **统一依赖 EvolutionEngine**：所有工具通过构造函数注入 `EvolutionEngine`，不直接依赖 `GeneStore` / `HillClimbingEngine` / `TraceRecorder` 等底层组件，保持单一依赖入口。
2. **agent_id 解析优先级**：
   - 显式传入 `agent_id` 参数 → 使用该值
   - 缺省 → 从 `ToolContext.getContext().get("sessionId")` 解析（取第一段）
   - 都没有 → 使用 `"default"`
3. **ToolResult 标准化**：
   - 成功返回 `ToolResult.success(message, data, rawOutput)` 三段式
   - `data` 含统计字段（trace_count/applied_count 等）供 UI 标签展示
   - `rawOutput` 含 Markdown 报告供 LLM 和 UI 详情区展示
4. **参数校验**：破坏性操作（toggle/rollback）强制校验 gene_id 非空，rollback 还校验 commit_hash 非空。

## 与其他包的关系

| 关联包 | 关系 |
|--------|------|
| `cn.bitloom.agentic.evolve` | EvolutionEngine 是工具的统一委托入口 |
| `cn.bitloom.agentic.evolve.climb` | ClimbAnalyzeTool 间接触发 HillClimbingEngine |
| `cn.bitloom.agentic.evolve.gene` | GeneQueryTool 间接读取 GeneStore |
| `cn.bitloom.agentic.tool`（父包）| Toolkit.buildAllTools() 注册 4 个工具 |

## 关键约束

1. 工具不使用 @Component 注解，通过 `new ClimbAnalyzeTool(evolutionEngine)` 创建
2. 工具名称必须唯一，已注册到 Toolkit.buildAllTools() 末尾
3. 工具调用受 AgentDefinition.tools 白名单过滤，需在 agent.md / config.json 中显式声明才会启用
4. 工具触发 L4 分析是同步阻塞的（ClimbAnalyzeTool），但底层 HillClimbingEngine 已限定分析 Trace 数量（默认 50 条），耗时可控
