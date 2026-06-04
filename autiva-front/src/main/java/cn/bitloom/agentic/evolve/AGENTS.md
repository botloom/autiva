# Evolve 包

## 概述
本包实现了基于 GEP（基因组演化协议）的 AI Agent 自演化引擎，参考 EvoMap 的 Evolver 项目设计。核心流水线为：**观察 → 分析 → 选择 → 生成 → 验证 → 固化**。

## 设计理念

参考 EvoMap 的三层架构：
1. **MCP 协议**：给 Agent 接上手和脚（工具连接）
2. **Skill 系统**：教 Agent 怎么做（执行 SOP）
3. **GEP 协议**：让 Agent 的经验可跨 Agent 传承（DNA 遗传系统）

核心愿景：**"One agent learns, a million inherit."**

## 目录结构

```
cn.bitloom.agentic.evolve/
├── config/
│   └── EvolveConfig.java              # 进化配置（策略、阈值、路径）
├── signal/
│   ├── Signal.java                    # 信号 record
│   ├── SignalType.java                # 信号类型枚举
│   ├── SignalExtractor.java           # 信号提取器（正则+关键词）
│   └── SignalHistory.java             # 信号历史分析
├── gene/
│   ├── Gene.java                      # Gene record
│   ├── GeneCategory.java              # Gene 分类枚举
│   ├── GeneSelector.java              # Gene 选择引擎
│   ├── GeneStore.java                 # Gene/Capsule 持久化
│   └── Capsule.java                   # Capsule record
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

### GeneStore
Gene/Capsule 的持久化管理，支持 JSON + JSONL 双格式，使用 ReentrantReadWriteLock 保证并发安全。

**方法：**
- `loadGenes()`: 加载所有基因
- `loadEnabledGenes()`: 加载启用的基因
- `loadCapsules()`: 加载所有胶囊
- `upsertGene(Gene)`: 更新或插入基因
- `deleteGene(String)`: 删除基因
- `toggleGene(String, boolean)`: 启用/禁用基因
- `deleteCapsule(String)`: 删除胶囊
- `appendEvent(EvolutionEvent)`: 追加进化事件
- `readRecentEvents(int)`: 读取最近N条事件

### SignalExtractor
信号提取器，实现正则匹配 + 关键词加权评分的两层策略。

### GeneSelector
基因选择引擎，基于信号匹配度 + 表观遗传值 + 策略权重计算综合得分，支持修复循环强制创新和停滞探索。

### StrategyEngine
策略引擎，根据历史分析动态调整策略预设（AUTO模式下自动切换）。

### EvolvePromptAssembler
GEP 提示词组装器，将信号 + 基因 + 策略组装为标准化的 GEP 提示词。

### Solidifier
固化器，执行金丝雀检查后提交成功演化，更新表观遗传值，连续成功≥3次自动创建胶囊。

### CanaryCheck
金丝雀检查，验证核心模块可正常加载。

## 资源文件

```
~/.autiva/evolve/
├── genes.json                         # Gene 池
├── capsules.json                      # Capsule 存储
├── events.jsonl                       # 演化事件日志（仅追加）
└── candidates.jsonl                   # 候选方案日志
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
- Builder模式：所有工具使用 Builder 创建
- 读写锁模式：GeneStore 使用 ReentrantReadWriteLock 保证并发安全
- 表观遗传模式：基于历史成功率动态调整基因权重
