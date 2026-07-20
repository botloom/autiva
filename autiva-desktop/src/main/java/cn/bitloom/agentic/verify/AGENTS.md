# verify 包 — L2 校验循环

## 定位

L2 Verification Loop 的唯一入口。基于 Hook 机制嵌入 L1 Agent Loop 内部，
利用 Spring AI ToolCallingAdvisor 的递归特性实现三个粒度的即时校验与自动重试，
**零侵入** SessionRunner。

## 核心组件

```
verify/
├── Feedback.java              ← 统一返回类型（passed/message/score/severity）
├── VerificationHook.java      ← L2 入口，实现 IAgentHook
└── grader/
    ├── ToolGrader.java            ← 工具级校验接口
    ├── PathValidationGrader.java  ← 文件路径合法性校验
    ├── RegexToolGrader.java       ← 按 Rubric Gene 正则校验工具输入/结果
    ├── OutputGrader.java          ← 产出级校验接口
    ├── RegexOutputGrader.java     ← 按 Rubric Gene 正则校验最终产出
    ├── CompletenessGrader.java    ← 确定性完整性校验（非空/长度/兜底文本）
    └── LlmGrader.java             ← LLM 裁判（Maker/Verifier 分离）
```

## 三粒度校验机制

### ① 工具调用级（beforeToolCall / afterToolCall）

- **beforeToolCall**：参数校验，不通过 `block(reason)`，原因返回 LLM，LLM 自动调整参数重试
- **afterToolCall**：结果校验，不通过修改 result 为错误 JSON，LLM 下一轮递归自动修正
- 实现：`ToolGrader` 链式调用（PathValidationGrader → RegexToolGrader）
- Rubric 来源：`GeneStore.findByTypeAndTarget(RUBRIC, toolName)`

### ② 模型调用级（afterModelCall）

- 记录 lastOutput 到 ThreadLocal，供对话级校验使用
- 默认不阻断（避免中断工具调用链）

### ③ 对话轮次级（afterConversationRound）

- OutputGrader 链（确定性优先）：CompletenessGrader → RegexOutputGrader
- 通过后执行 LlmGrader（贵，最后执行）
- 失败通过 `EventBus.publishIn(MessageEvent.userMessage(...))` 触发重试
- 重试计数：`AtomicInteger` 保存在 `RuntimeContext.params` 中
- 最大重试次数：从 `AgentDefinition.verification().maxRetries()` 读取

## 状态管理

`VerificationState` 保存在 `RuntimeContext.params` 中：
- `modelCallCount`：模型调用次数
- `blockedToolCalls`：被 block 的工具调用列表
- `failedToolResults`：被修改为错误的工具结果列表
- `verified`：是否通过校验
- `feedbacks`：所有 Feedback 记录

ThreadLocal 用于 `afterConversationRound()`（无参方法）获取当前 RuntimeContext 和 lastOutput。
`finally` 块清理 ThreadLocal 避免内存泄漏。

## 配置（agent.md frontmatter）

```yaml
verification: true              # 启用 L2 校验
maxRetries: 2                   # 对话级最大重试
verifyLevels: [tool, output]    # 启用的校验粒度
```

通过 `AgentDefinition.verification()` 解析。

## 依赖关系

- 依赖：`GeneStore`（读取 RUBRIC Gene）、`EvolveConfig`（配置）、`IAgentHook` 接口
- 被依赖：`FileSystemSessionManager`（构造 VerificationHook 注入 Agent.Builder）
- LlmGrader 懒加载 `verifierClient` 避免循环依赖

## 关键约束

1. **Maker/Verifier 分离**：LlmGrader 使用独立 ChatClient（DEEPSEEK），不复用 Maker 上下文
2. **确定性优先**：OutputGrader 全部通过后才执行 LlmGrader（成本控制）
3. **无 Rubric 跳过**：LlmGrader 无 RUBRIC Gene 时返回 pass（不空跑 LLM）
4. **重试耗尽放行**：达到 maxRetries 后交付当前产出（避免死循环）

## 后续阶段

- **阶段3**：TraceRecorder + TraceAdvisor 在 `trace` 包实现，与本包协作
- **阶段4**：UI 增加"L2 校验报告"页签，展示通过率/失败原因统计
