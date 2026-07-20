# inject 包 — Gene 配置注入器

## 定位

L4 优化结果对 L1/L2 的实际影响通道。将 Gene 配置内容注入到 Agent 的运行时配置中，
实现"L4 优化 → 下次对话生效"的闭环。

## 核心组件

```
inject/
└── GeneInjector.java    ← Gene 配置注入器（@Component）
```

配套 Advisor：`cn.bitloom.agentic.agent.advisor.GeneInjectAdvisor`

## 注入策略

| Gene 类型 | 注入位置 | 注入方式 | 实现状态 |
|-----------|---------|---------|---------|
| PROMPT | SystemMessage 末尾 | GeneInjectAdvisor 追加 `<genes>` 块 | ✅ 已实现 |
| TOOL_DESC | 工具定义的 description | Agent 构建时替换（暂未实现） | 待实施 |
| RUBRIC | LlmGrader 的评判标准 | LlmGrader 构建时加载（Phase 2） | ✅ 已实现 |
| SKILL_CONFIG | agent.md 的 skills 配置 | 覆盖技能列表（暂未实现） | 待实施 |

## PROMPT Gene 注入流程

```
Agent.runStream / runBlock
   ↓ ChatClient 请求
GeneInjectAdvisor.adviseStream/adviseCall（order=250）
   ↓
GeneInjector.buildPromptInjection(agentId)
   ├── GeneStore.findByTypeAndTarget(PROMPT, agentId)
   ├── 过滤 enabled=true
   ├── 按 epigeneticBoost 降序排序（高权重优先）
   └── 拼装为 <genes>...</genes> 文本块
   ↓
追加到 SystemMessage 末尾
   ↓
L1 Agent Loop 继续（带优化后的 Prompt）
```

## GeneInjectAdvisor 设计

- **Advisor 类型**：StreamAdvisor + CallAdvisor
- **order**：250（在 ProactiveContextAdvisor order=200 之后，确保最后注入）
- **注入条件**：`enableCompact=true` + `geneInjector != null`
- **容错**：任何异常返回原 request，不阻断主流程

## GeneInjector 关键方法

- `buildPromptInjection(agentId)` — 构建 PROMPT Gene 注入文本
- `loadActiveGenes(agentId)` — 加载指定 Agent 的所有启用 Gene（供 UI/调试）

## 注入示例

SystemMessage 末尾追加：
```
<genes>
### error_handling_instruction
遇到错误时，先分析错误根因，再制定修复方案，不要盲目重试。

### code_review_checklist
代码必须满足：1) 编译通过；2) 无未处理异常；3) 关键路径有日志。
</genes>
```

## 与其他包的关系

| 关联包 | 关系 |
|--------|------|
| `cn.bitloom.agentic.agent.advisor` | GeneInjectAdvisor 作为 Spring AI Advisor |
| `cn.bitloom.agentic.evolve.gene` | GeneStore 加载 PROMPT Gene |
| `cn.bitloom.agentic.agent` | Agent.Builder.geneInjector() 自动注册 GeneInjectAdvisor |
| `cn.bitloom.agentic.session` | FileSystemSessionManager 注入 GeneInjector 到 Agent |

## 关键约束

1. **只追加不覆盖**：Gene 内容追加到 SystemMessage 末尾，不替换原有 prompt
2. **epigeneticBoost 排序**：高 boost 的 Gene 排在前面，LLM 更关注
3. **enabled 过滤**：禁用的 Gene 不注入
4. **无 Gene 时跳过**：返回 null，GeneInjectAdvisor 不修改 request
