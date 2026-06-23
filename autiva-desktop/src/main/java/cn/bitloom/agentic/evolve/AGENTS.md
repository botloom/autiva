# Evolve 包

## 概述
本包实现了基于 GeneOS 的 AI Agent 自进化操作系统。核心流水线为：**执行记录 → 经验提取 → 进化决策 → 基因突变/路由更新/规则沉淀 → JGit提交 → 下次执行改进**。

## 设计理念

基于 GeneOS 三层架构：
1. **Gene 执行层**：可执行基因（Shell/Java/Script/Strategy），通过 GeneRuntime 统一执行
2. **经验引擎层**：从执行日志中提取失败模式，驱动进化决策
3. **进化引擎层**：基因突变 + 路由更新 + 规则沉淀，JGit 版本控制

核心愿景：**"基于文件系统 + Git 的可进化能力操作系统"**

## 目录结构

```
cn.bitloom.agentic.evolve/
├── config/
│   └── EvolveConfig.java              # 进化配置（策略、阈值、路径、安全参数）
├── signal/
│   ├── Signal.java                    # 信号 record
│   ├── SignalType.java                # 信号类型枚举
│   ├── SignalExtractor.java           # 信号提取器（正则+关键词）
│   └── SignalHistory.java             # 信号历史分析
├── gene/
│   ├── Gene.java                      # Gene record（含可执行代码字段）
│   ├── GeneCategory.java              # Gene 分类枚举
│   ├── GeneRuntimeType.java           # Gene 运行时类型枚举
│   ├── GeneSelector.java              # Gene 选择引擎
│   ├── GeneStore.java                 # Gene/Capsule 持久化 + JGit集成
│   └── Capsule.java                   # Capsule record
├── execution/
│   ├── ExecutionLog.java              # 执行日志 record
│   └── ExecutionRecorder.java         # 执行记录器
├── runtime/
│   ├── GeneRuntime.java               # 基因运行时统一入口
│   ├── GeneExecutor.java              # 执行器接口
│   ├── GeneResult.java                # 执行结果 record
│   ├── StrategyGeneExecutor.java      # 策略文本执行器
│   ├── ShellGeneExecutor.java         # Shell脚本执行器
│   └── JavaGeneExecutor.java          # Java代码执行器
├── experience/
│   ├── Experience.java                # 经验 record
│   ├── ExperienceTarget.java          # 经验目标枚举（GENE/ROUTING/MEMORY）
│   └── ExperienceEngine.java          # 经验引擎（LLM驱动模式挖掘）
├── mutation/
│   └── GeneMutator.java               # 基因突变器（LLM改代码）
├── routing/
│   ├── RoutingEntry.java              # 路由条目 record
│   └── RoutingEngine.java             # 动态路由引擎
├── memory/
│   ├── MemoryRule.java                # 记忆规则 record
│   └── MemoryEngine.java              # 规则记忆引擎
├── safety/
│   └── EvolutionSafety.java           # 进化安全系统
├── repository/
│   └── GeneRepository.java            # JGit基因版本仓库
├── migration/
│   └── GeneMigration.java             # 数据迁移（genes.json → 目录结构）
├── strategy/
│   ├── StrategyPreset.java            # 策略预设枚举
│   └── StrategyEngine.java            # 策略引擎
├── prompt/
│   └── EvolvePromptAssembler.java     # GEP 提示词组装器
├── solidify/
│   ├── EvolutionEvent.java            # 演化事件 record
│   ├── Solidifier.java                # 固化器
│   └── CanaryCheck.java              # 金丝雀检查
├── EvolutionEngine.java               # 进化引擎主类
└── AGENTS.md                          # 包文档
```

## 核心概念

### Gene（基因）
紧凑可复用的演化指令，自适应行为的最小单元。包含：
- `signalsMatch`：匹配的信号类型列表
- `strategy`：策略步骤
- `constraints`：约束条件
- `validation`：验证检查
- `epigeneticBoost`：表观遗传值（基于历史成功率动态调整）
- `antiPatterns`：反模式警告
- `runtimeType`：运行时类型（STRATEGY/SHELL/JAVA/SCRIPT）
- `code`：可执行代码
- `version`：版本号
- `parentId`：父基因ID（突变来源）

### GeneRuntimeType（基因运行时类型）
- `STRATEGY`：纯策略文本（向后兼容）
- `SHELL`：Shell脚本执行
- `JAVA`：Java代码执行
- `SCRIPT`：通用脚本执行

### ExecutionLog（执行日志）
记录每次基因/工具执行的结构化日志，写入 `~/.autiva/logs/executions/YYYY-MM-DD.jsonl`。

### Experience（经验）
从执行日志中提取的结构化失败模式，包含：
- `pattern`：识别的失败模式
- `rootCause`：根本原因
- `fix`：修复方案
- `target`：影响目标（GENE/ROUTING/MEMORY）
- `confidence`：置信度（<0.7不触发进化）

### RoutingEntry（路由条目）
动态路由映射，将意图模式映射到推荐基因。

### MemoryRule（记忆规则）
从经验沉淀的规则，触发模式 → 执行动作。

### Capsule（胶囊）
高阶演化资产，将相关基因与附加上下文打包。通常由连续成功的进化事件自动生成（≥3次成功）。

### Signal（信号）
从对话和日志中提取的结构化观察结果，类型包括：
- 错误类：LOG_ERROR, ERRSIG, RECURRING_ERROR
- 用户需求类：USER_FEATURE_REQUEST, USER_IMPROVEMENT_SUGGESTION
- 性能类：PERF_BOTTLENECK, CAPABILITY_GAP
- 状态类：STABLE_SUCCESS_PLATEAU, EVOLUTION_STAGNATION
- 循环检测类：REPAIR_LOOP_DETECTED, FORCE_INNOVATION_AFTER_REPAIR_LOOP
- 工具类：TOOL_BYPASS, HIGH_TOOL_USAGE
- 缺失类：MEMORY_MISSING, SESSION_LOGS_MISSING
- 探索类：EXPLORE_OPPORTUNITY, EVOLUTION_SATURATION

### EvolutionEvent（进化事件）
不可变审计记录，捕获信号、选定基因、生成提示词及结果。

## 核心类

### EvolutionEngine
进化引擎主类，编排整个进化流水线。

**Spring 注解：** `@Component`

**核心方法：**
- `runCycle(List<String>)`: 执行一次完整的进化周期
- `getEvolutionContext(List<String>)`: 获取当前进化上下文
- `solidify(EvolutionEvent)`: 固化成功的演化
- `evolve(Experience)`: 经验驱动进化（基因突变/路由更新/规则沉淀）
- `extractAndEvolve()`: 批量提取经验并进化
- `queryGenes(GeneCategory)`: 查询基因
- `queryGeneDetail(String)`: 查看基因详情
- `queryCapsules()`: 查询胶囊
- `queryEvents(int)`: 查看进化事件
- `recommendGenes(List<String>)`: 获取推荐基因
- `applyGene(String, String)`: 应用基因
- `applyCapsule(String, String)`: 应用胶囊

### EvolveConfig
集中式配置类，所有阈值支持运行时修改。

**配置项：**
- `strategyPreset`: 策略预设（BALANCED/INNOVATE/HARDEN/REPAIR_ONLY/AUTO）
- `signalDedupWindow`: 信号去重窗口（默认8）
- `repairLoopThreshold`: 修复循环阈值（默认3）
- `saturationThreshold`: 饱和阈值（默认5）
- `maxPromptLength`: 最大提示词长度（默认24000）
- `epigeneticBoostOnSuccess`: 成功时表观遗传增强（默认1.2）
- `epigeneticDecay`: 失败时表观遗传衰减（默认0.95）
- `experienceConfidenceThreshold`: 经验置信度阈值（默认0.7）
- `mutationFrequencyLimitPerHour`: 每小时突变频率限制（默认10）
- `maxComplexityIncrease`: 最大复杂度增长倍数（默认1.5）

### GeneStore
Gene/Capsule 的持久化管理，支持 JSON + 目录结构双格式，JGit版本控制，使用 ReentrantReadWriteLock 保证并发安全。

**方法：**
- `loadGenes()`: 加载所有基因（优先从目录结构加载）
- `loadEnabledGenes()`: 加载启用的基因
- `loadCapsules()`: 加载所有胶囊
- `upsertGene(Gene)`: 更新或插入基因（自动JGit提交）
- `deleteGene(String)`: 删除基因
- `toggleGene(String, boolean)`: 启用/禁用基因
- `deleteCapsule(String)`: 删除胶囊
- `appendEvent(EvolutionEvent)`: 追加进化事件
- `readRecentEvents(int)`: 读取最近N条事件
- `loadGeneCode(String)`: 加载基因可执行代码
- `saveGeneCode(String, String)`: 保存基因可执行代码
- `getGeneHistory(String)`: 获取基因JGit历史
- `revertGene(String, String)`: 回滚基因到指定版本
- `diffGene(String, String, String)`: 比较基因版本差异

### GeneRuntime
基因运行时统一入口，根据GeneRuntimeType分发到对应执行器。

### ExperienceEngine
经验引擎，使用LLM从执行日志中提取结构化失败模式。

**方法：**
- `extract(List<ExecutionLog>)`: 从日志提取单个经验
- `batchExtract(int)`: 批量提取经验

### GeneMutator
基因突变器，使用LLM修复/优化基因代码。

### RoutingEngine
动态路由引擎，将意图模式映射到推荐基因。

**方法：**
- `route(String)`: 根据意图匹配最佳基因
- `updateFromExperience(Experience)`: 从经验更新路由
- `addRoute(String, String, double)`: 手动添加路由
- `removeRoute(String)`: 删除路由
- `listRoutes()`: 列出所有路由

### MemoryEngine
规则记忆引擎，从经验沉淀可复用规则。

**方法：**
- `addRuleFromExperience(Experience)`: 从经验添加规则
- `addManualRule(String, String, double)`: 手动添加规则
- `queryRules(String)`: 查询匹配规则
- `hitRule(String)`: 增加规则命中计数
- `deleteRule(String)`: 删除规则

### EvolutionSafety
进化安全系统，防止"错误进化"。

**检查项：**
- 基因ID一致性
- 突变后代码非空
- 爆炸半径增长限制
- 突变频率限制
- 代码长度增长限制

### GeneRepository
JGit基因版本仓库，提供commit/history/diff/revert操作。

### GeneMigration
数据迁移工具，将genes.json迁移到目录结构，保留原文件备份。

### SignalExtractor
信号提取器，实现正则匹配 + 关键词加权评分的两层策略。

### GeneSelector
基因选择引擎，基于信号匹配度 + 表观遗传值 + 策略权重计算综合得分。

### Solidifier
固化器，执行金丝雀检查后提交成功演化，更新表观遗传值，连续成功≥3次自动创建胶囊。

### CanaryCheck
金丝雀检查，验证核心模块可正常加载。

## 资源文件

```
~/.autiva/evolve/
├── genes.json                         # Gene 池（兼容）
├── genes/                             # Gene 目录结构（新）
│   └── {geneId}/
│       ├── gene.json                  # Gene元数据
│       ├── impl.java                  # 可执行代码
│       └── versions/                  # 版本历史
│           ├── v1.json
│           └── v2.json
├── capsules.json                      # Capsule 存储
├── events.jsonl                       # 演化事件日志（仅追加）
├── candidates.jsonl                   # 候选方案日志
├── routing.json                       # 路由表
└── memory/
    └── rules.jsonl                    # 规则记忆

~/.autiva/logs/executions/
└── YYYY-MM-DD.jsonl                   # 执行日志
```

## 完整闭环流程

```
用户任务
   ↓
Agent Runtime
   ↓
Gene Execution (GeneRuntime)
   ↓
ExecutionRecorder → logs/executions/*.jsonl
   ↓
ExperienceEngine (LLM模式挖掘)
   ↓
EvolutionEngine.evolve(Experience)
   ├── GeneMutator → 突变基因代码
   ├── RoutingEngine → 更新路由表
   └── MemoryEngine → 沉淀规则
   ↓
EvolutionSafety → 安全验证
   ↓
GeneRepository.commit() → JGit提交
   ↓
下次执行改进
```

## 策略预设

| 策略 | 创新 | 优化 | 修复 | 适用场景 |
|------|------|------|------|----------|
| BALANCED | 50% | 30% | 20% | 日常运行 |
| INNOVATE | 80% | 15% | 5% | 系统稳定，快速迭代 |
| HARDEN | 20% | 40% | 40% | 重大变更后，聚焦稳定 |
| REPAIR_ONLY | 0% | 20% | 80% | 紧急修复模式 |
| AUTO | 动态 | 动态 | 动态 | 根据历史自动调整 |

## 种子基因

首次运行时从 `classpath:evolve/genes.seed.json` 初始化，包含4个基础基因：
1. `gene_repair_from_errors`：修复类，匹配错误信号
2. `gene_optimize_prompt_and_assets`：优化类，匹配性能信号
3. `gene_innovate_from_opportunity`：创新类，匹配需求信号
4. `gene_tool_integrity`：修复类，匹配工具完整性信号

## 设计模式
- 策略模式：StrategyPreset + StrategyEngine 动态调整进化策略
- 观察者模式：信号提取 → 基因选择 → 提示词组装的流水线
- 事件溯源模式：ExecutionRecorder → ExperienceEngine → EvolutionEngine 闭环
- 读写锁模式：GeneStore 使用 ReentrantReadWriteLock 保证并发安全
- 表观遗传模式：基于历史成功率动态调整基因权重
- 版本控制模式：GeneRepository (JGit) 管理基因进化历史
- 安全防护模式：EvolutionSafety 防止错误进化
