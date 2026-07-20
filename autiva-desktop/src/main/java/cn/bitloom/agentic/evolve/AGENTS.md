# Evolve 包

## 概述
本包实现了基于 **Loop Engineering** 的 L4 爬山自优化系统。Gene 不再是"可执行技能"，而是 **L4 可优化的配置单元**（Agent Prompt / 工具描述 / Grader Rubric / 技能配置）。版本通过 JGit 管理，表观遗传值（epigeneticBoost）基于 L2 校验通过率动态调整。

## 设计理念

四层循环架构中本包负责 **L4 Hill Climbing Loop**：

```
L4 (本包)        TraceRecorder → HillClimbingEngine → GeneMutator → GeneStore
                                              ↑                          ↓
L2 (verify 包)   VerificationHook / LlmGrader ←── Rubric Gene 注入 ───┘
                                              ↑                          ↓
L1 (agent 包)    Agent / SessionRunner ←── PROMPT Gene 注入 ─────────┘
```

**核心原则**：
1. **Gene = 配置单元**：PROMPT/TOOL_DESC/RUBRIC/SKILL_CONFIG 四种类型
2. **配置驱动优化**：所有可调配置都是 Gene，L4 通过 LLM 分析 Trace 反向优化
3. **复用基础设施**：JGit 版本控制、表观遗传值、安全检查、突变器全部保留
4. **Maker/Verifier 分离**：L2 的 LlmGrader 是独立 Agent，与 L1 主 Agent 分离

## 目录结构

```
cn.bitloom.agentic.evolve/
├── config/
│   └── EvolveConfig.java              # 配置（路径、表观遗传参数、安全参数、Trace参数）
├── gene/
│   ├── Gene.java                      # Gene record（L4 配置单元）
│   ├── GeneType.java                  # 配置类型枚举（PROMPT/TOOL_DESC/RUBRIC/SKILL_CONFIG）
│   └── GeneStore.java                 # Gene 持久化 + JGit 集成 + 读写锁
├── mutation/
│   └── GeneMutator.java               # LLM 驱动的配置内容优化器
├── repository/
│   └── GeneRepository.java            # JGit 基因版本仓库
├── safety/
│   └── EvolutionSafety.java           # 进化安全系统（防改坏）
├── solidify/
│   ├── EvolutionEvent.java            # 进化事件 record（审计日志）
│   └── Solidifier.java                # 表观遗传值更新器
├── climb/                             # 阶段3新增
│   ├── HillClimbingEngine.java        # L4 爬山分析引擎
│   ├── OptimizationSuggestion.java    # 优化建议 record
│   └── ClimbingResult.java            # 爬山结果 record
├── inject/                            # 阶段3新增
│   └── GeneInjector.java              # Gene 配置注入器
├── EvolutionEngine.java               # 进化引擎主类（L4 编排器）
└── AGENTS.md                          # 包文档
```

## 核心概念

### Gene（基因 / 配置单元）
L4 可优化的配置片段，字段：
- `id` — 基因ID（如 `prompt_heimy_error_handling`）
- `type` — `GeneType` 枚举：PROMPT / TOOL_DESC / RUBRIC / SKILL_CONFIG
- `targetId` — 目标对象ID（agent名 / 工具名 / grader名 / 技能名）
- `name` — 配置项名称（如 `system_prompt` / `description` / `completeness_check`）
- `content` — 配置内容（Prompt文本 / 工具描述 / Rubric规则 / 技能配置JSON）
- `description` — 配置项作用说明（供 L4 理解上下文）
- `epigeneticBoost` — 表观遗传值（L4 优化效果权重，1.0 为基准，上限 5.0，下限 0.1）
- `version` — 版本号（每次突变 +1）
- `parentId` — 父版本ID（突变来源）
- `enabled` — 启用状态
- `createdAt` / `updatedAt` — 时间戳

**不可变 record**，提供 `withEpigeneticBoost` / `withEnabled` / `withContent` / `withVersion` 派生方法。

### GeneType（配置类型）
| 类型 | 注入位置 | L4 优化目标 |
|------|---------|------------|
| `PROMPT` | SystemMessage 末尾 | Agent 提示词片段 |
| `TOOL_DESC` | 工具定义的 description | 工具描述文本 |
| `RUBRIC` | LlmGrader 的评判标准 | L2 评分规则 |
| `SKILL_CONFIG` | agent.md 的 skills 配置 | 技能列表/参数 |

### EvolutionEvent（进化事件）
不可变审计记录，捕获单次进化决策的结果，写入 `events.jsonl`。

### 表观遗传值（epigeneticBoost）
- 初始值 `1.0`
- L2 校验通过率提升 → `×1.2`（上限 5.0）
- L2 校验通过率下降 → `×0.95`（下限 0.1）
- 低 boost 的 Gene 在 L4 分析时优先被重新优化

## 核心类

### EvolutionEngine
**Spring 注解**：`@Component`

进化引擎主类，L4 爬山循环的编排器。委托 `HillClimbingEngine` 执行 L4 分析，委托 `Solidifier` 更新表观遗传值，暴露 `TraceRecorder` 的统计能力。

**核心方法**：
- `climb(agentId)` — 执行 L4 爬山分析，返回 ClimbingResult
- `verificationStats(agentId, recentLimit)` — 获取指定 Agent 的 L2 校验通过率统计
- `cleanupOldTraces()` — 清理旧 Trace 文件（按 `traceRetentionDays` 配置）
- `solidify(EvolutionEvent)` — 固化进化事件，更新表观遗传值
- `createEvent(geneId, intent, outcome)` — 创建进化事件
- `queryGenes(GeneType)` — 按类型查询基因
- `queryGeneDetail(String)` — 查看基因详情
- `queryEvents(int)` — 查看最近 N 条进化事件
- `applyGene(String)` — 应用基因（返回详情）
- `toggleGene(String, boolean)` — 启用/禁用基因
- `revertGene(String, String)` — 回滚基因到指定 commit

### EvolveConfig
**Spring 注解**：`@Component` `@Getter`

集中式配置类，所有参数支持运行时调整。

**路径配置**：
- `evolveDir` — `~/.autiva/evolve/`
- `genesFile` — `~/.autiva/evolve/genes.json`（兼容）
- `eventsFile` — `~/.autiva/evolve/events.jsonl`（仅追加）
- `candidatesFile` — `~/.autiva/evolve/candidates.jsonl`
- `genesDir` — `~/.autiva/genes/`（目录结构，新）
- `executionsDir` — `~/.autiva/logs/executions/`

**表观遗传参数**：
- `epigeneticBoostOnSuccess` — 成功增强系数（默认 1.2）
- `epigeneticDecay` — 失败衰减系数（默认 0.95）

**安全参数**：
- `experienceConfidenceThreshold` — 经验置信度阈值（默认 0.7）
- `mutationFrequencyLimitPerHour` — 每小时突变频率上限（默认 10）
- `maxComplexityIncrease` — 内容长度最大增长倍数（默认 1.5）

**Trace/L2 参数**：
- `recentEventsLimit` — 最近事件查询上限（默认 20）
- `maxConversationRetries` — L2 对话级最大重试（默认 2）
- `traceRetentionDays` — Trace 保留天数（默认 30）

### GeneStore
**Spring 注解**：`@Component`

Gene 持久化管理，支持目录结构存储 + JGit 版本控制 + ReentrantReadWriteLock 保证并发安全。

**查询方法**：
- `loadGenes()` — 加载所有基因（优先从目录结构加载）
- `loadEnabledGenes()` — 只加载启用的基因
- `findByType(GeneType)` — 按类型查询
- `findByTarget(String)` — 按目标对象查询
- `findByTypeAndTarget(GeneType, String)` — 按类型+目标查询
- `findById(String)` — 按 ID 查询

**写入方法**：
- `upsertGene(Gene)` — 更新或插入基因（自动 JGit 提交）
- `deleteGene(String)` — 删除基因
- `toggleGene(String, boolean)` — 启用/禁用

**事件方法**：
- `appendEvent(EvolutionEvent)` — 追加进化事件
- `readRecentEvents(int)` — 读取最近 N 条事件

**版本控制方法**：
- `getGeneHistory(String)` — 获取 JGit 历史
- `revertGene(String, String)` — 回滚到指定 commit
- `diffGene(String, fromCommit, toCommit)` — 比较版本差异

### GeneMutator
**Spring 注解**：`@Component`

LLM 驱动的配置内容优化器。使用独立的 `mutator` Agent（DEEPSEEK 模型），不复用主 Agent 上下文。

**核心方法**：
- `mutate(Gene original, String issue, String suggestion)` — 根据优化建议生成新版本
  - 自动通过 `EvolutionSafety` 检查
  - 自动 `upsertGene` 写入并 JGit 提交
  - 版本号 +1，parentId 指向原版本
  - 失败返回 `null`

### EvolutionSafety
**Spring 注解**：`@Component`

防"L4 改坏配置"的安全检查器。

**检查项**：
- 基因 ID 一致性（原/突变后必须相同）
- 突变后内容非空
- 内容长度增长限制（不超过 `maxComplexityIncrease` 倍）
- 突变频率限制（不超过 `mutationFrequencyLimitPerHour` 次/小时）

**返回**：`SafetyCheckResult(passed, message)`

### Solidifier
**Spring 注解**：`@Component`

表观遗传值更新器，根据 L2 校验结果（通过 `EvolutionEvent` 传入）调整 Gene 的 `epigeneticBoost`。

**逻辑**：
- 事件 success → `boost × 1.2`（上限 5.0）
- 事件 failure → `boost × 0.95`（下限 0.1）

### GeneRepository
JGit 基因版本仓库，管理 `~/.autiva/genes/` 目录的 git 操作。

**方法**：`commit(Gene, message)` / `history(geneId)` / `revert(geneId, commitHash)` / `diff(...)`

## 资源文件

```
~/.autiva/
├── genes/                              ← Gene 配置库（新格式，目录结构）
│   ├── prompt_heimy_error_handling/
│   │   ├── gene.json                   ← 配置内容 + 元数据
│   │   └── versions/
│   │       ├── v1.json
│   │       └── v2.json
│   ├── rubric_heimy_code_quality/
│   │   └── gene.json
│   ├── tooldesc_read_file/
│   │   └── gene.json
│   └── rubric_read_file_path_validation/
│       └── gene.json
├── evolve/                             ← 进化运行时数据
│   ├── genes.json                      ← Gene 池（兼容格式，迁移用）
│   ├── events.jsonl                    ← 进化事件日志（仅追加）
│   ├── candidates.jsonl                ← 候选优化方案
│   └── .git/                           ← JGit 仓库（管理 genes/ 目录）
└── traces/                             ← L4 结构化 Trace（阶段3新增）
    └── YYYY-MM-DD/
        └── session_xxx.jsonl
```

## 种子基因

首次运行时从 `classpath:evolve/genes.seed.json` 初始化，包含 4 个示例 Gene：

| ID | 类型 | 目标 | 作用 |
|----|------|------|------|
| `prompt_heimy_error_handling` | PROMPT | heimy | 控制错误处理行为（先分析根因再修复） |
| `rubric_heimy_code_quality` | RUBRIC | heimy | heimy 产出的代码质量评分标准 |
| `tooldesc_read_file` | TOOL_DESC | read_file | read_file 工具的描述文本 |
| `rubric_read_file_path_validation` | RUBRIC | read_file | read_file 工具的路径校验规则 |

## 完整闭环流程（规划中）

```
用户任务
   ↓
L1 Agent Loop（agent 包）— 模型规划 → 工具执行 → 观测反馈 → 再推理
   ↓ （Hook 机制介入）
L2 Verification Loop（verify 包，阶段2实现）
   ├─ 工具级校验（beforeToolCall / afterToolCall）→ LLM 自动修正重试
   ├─ 模型级校验（afterModelCall）→ 记录 Trace
   └─ 对话级校验（afterConversationRound）→ 确定性 + LLM Grader
   ↓
TraceRecorder → traces/{date}/{sessionId}.jsonl（阶段3实现）
   ↓
L4 Hill Climbing Loop（本包，阶段3完整实现）
   ├─ HillClimbingEngine 分析 Trace，发现高频缺陷
   ├─ GeneMutator LLM 优化配置内容
   ├─ EvolutionSafety 安全检查
   ├─ GeneStore + GeneRepository JGit 提交版本
   └─ Solidifier 根据 L2 通过率更新 epigeneticBoost
   ↓
GeneInjector（阶段3实现）— 优化后的 Gene 注入回 L1/L2
```

## 设计模式
- **配置单元模式**：所有可调配置统一为 Gene，类型化的配置单元
- **表观遗传模式**：基于 L2 校验通过率动态调整 Gene 权重
- **版本控制模式**：GeneRepository (JGit) 管理 Gene 进化历史
- **安全防护模式**：EvolutionSafety 防止错误进化
- **读写锁模式**：GeneStore 使用 ReentrantReadWriteLock 保证并发安全
- **Maker/Verifier 分离**：L2 LlmGrader 独立于 L1 主 Agent（阶段2实现）
- **不可变 record**：Gene/EvolutionEvent 全部为不可变 record，变更通过 with* 方法派生

## 与其他包的关系

| 关联包 | 关系 |
|--------|------|
| `cn.bitloom.agentic.agent` | GeneInjector（阶段3）将 PROMPT Gene 注入到 Agent 的 SystemMessage |
| `cn.bitloom.agentic.verify`（阶段2）| L2 LlmGrader 从 GeneStore 加载 RUBRIC Gene 作为评判标准 |
| `cn.bitloom.agentic.trace`（阶段3）| TraceRecorder 提供 Trace 数据，供 L4 HillClimbingEngine 分析 |
| `cn.bitloom.agentic.tool` | GeneMutator 工具（阶段4）暴露 L4 能力给 L1 Agent |
| `cn.bitloom.vm` / `cn.bitloom.controller` | GEP 页面展示 Gene 列表/事件/版本历史 |

## 后续阶段规划

| 阶段 | 内容 | 状态 |
|------|------|------|
| 阶段1 | Gene 系统重构（本文档涉及） | ✅ 已完成 |
| 阶段2 | L2 校验循环（verify 包，基于 Hook 机制） | ✅ 已完成 |
| 阶段3 | L4 爬山循环（HillClimbingEngine + GeneInjector + TraceRecorder） | ✅ 已完成 |
| 阶段4 | 工具注册 + GEP UI 改造 | ✅ 已完成 |
| 阶段5 | 清理与文档完善 | 待实施 |

详见方案文档：`d:\project\autiva\.trae\documents\Loop工程化L2L4自优化方案.md`
