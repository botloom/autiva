# Exception 包

## 概述
本包定义了应用的统一异常类体系，所有自定义异常继承自 `AutivaException`，与进化系统（GEP）深度集成。每个异常携带 `errorCode` 和 `recoverable` 标记，可自动映射为进化信号（SignalType）。

## 设计理念

异常不仅是错误报告机制，更是进化系统的信号源。当异常发生时：
1. `errorCode` → 映射为 `SignalType`，供 `SignalExtractor` 提取
2. `recoverable` → 标记是否可恢复，影响策略引擎的决策
3. 工厂方法 → 确保异常消息的一致性和可追溯性

## 异常类体系

```
AutivaException (abstract, root)
├── AgentException          智能体领域
├── ToolException           工具领域
├── StorageException        存储领域
├── EvolveException         进化领域
├── SecurityViolationException 安全领域
└── WorkFlowException       工作流领域
```

## 核心类

### AutivaException
所有自定义异常的抽象基类。

**字段：**
- `errorCode: String` — 错误码，映射到 SignalType
- `recoverable: boolean` — 是否可恢复

**核心方法：**
- `getErrorCode()`: 获取错误码
- `isRecoverable()`: 是否可恢复
- `toSignalType()`: 将错误码映射为进化信号类型

**错误码 → 信号类型映射：**
| errorCode | SignalType |
|-----------|------------|
| AGENT_NOT_FOUND, AGENT_CONFIG_ERROR, SUBAGENT_* | LOG_ERROR |
| TOOL_VALIDATION_ERROR | LOG_ERROR |
| TOOL_EXECUTION_ERROR, TOOL_NOT_FOUND | ERRSIG |
| TOOL_BYPASS | TOOL_BYPASS |
| STORAGE_READ_ERROR, STORAGE_WRITE_ERROR | MEMORY_MISSING |
| EVOLVE_GENE_NOT_FOUND, EVOLVE_CYCLE_FAILED | CAPABILITY_GAP |
| EVOLVE_SOLIDIFY_FAILED | REPAIR_LOOP_DETECTED |
| SECURITY_VIOLATION | LOG_ERROR |
| WORKFLOW_* | LOG_ERROR |

### AgentException
智能体领域异常。

**工厂方法：**
- `notFound(name)`: 智能体不存在
- `configError(detail, cause)`: 配置错误
- `subagentNotFound(name)`: 子智能体不存在
- `subagentAlreadyExists(name)`: 子智能体已存在
- `subagentExecutionFailed(name, cause)`: 子智能体执行失败
- `subagentResolverNotFound(reference)`: 子代理解析器未找到
- `subagentExecutorNotFound(kind)`: 子代理执行器未找到

### ToolException
工具领域异常。

**工厂方法：**
- `validationError(detail)`: 参数验证失败
- `executionError(toolName, cause)`: 执行失败
- `executionError(toolName, detail)`: 执行失败（带描述）
- `notFound(toolName)`: 工具不存在
- `bypass(toolName, reason)`: 工具被绕过

### StorageException
存储领域异常。

**工厂方法：**
- `readError(path, cause)`: 读取失败
- `writeError(path, cause)`: 写入失败
- `readError(path)`: 读取失败（无cause）
- `writeError(path)`: 写入失败（无cause）
- `dirError(path, cause)`: 目录操作失败
- `dirNotFound(path)`: 目录不存在
- `notADir(path)`: 路径不是目录

### EvolveException
进化领域异常。

**工厂方法：**
- `geneNotFound(geneId)`: 基因不存在
- `capsuleNotFound(capsuleId)`: 胶囊不存在
- `cycleFailed(reason)`: 进化周期失败
- `solidifyFailed(reason)`: 固化失败
- `canaryFailed(module)`: 金丝雀检查失败
- `storageError(detail, cause)`: 进化存储错误

### SecurityViolationException
安全违规异常。

**工厂方法：**
- `absolutePath(path)`: 绝对路径不允许
- `pathTraversal(path)`: 路径遍历攻击

### WorkFlowException
工作流领域异常。

**工厂方法：**
- `configError(detail, cause)`: 配置错误
- `configError(detail)`: 配置错误（无cause）
- `executionError(node, cause)`: 执行失败
- `nodeNotFound(nodeId)`: 节点不存在

## 使用规范

### 抛出异常
```java
// 使用工厂方法（推荐）
throw AgentException.subagentNotFound(name);
throw StorageException.writeError(path, e);
throw EvolveException.cycleFailed("未检测到信号");

// 直接构造（需要自定义 errorCode）
throw new ToolException("TOOL_TIMEOUT", "工具超时", true);
```

### 捕获异常
```java
try {
    agent.run();
} catch (AutivaException e) {
    Signal signal = signalExtractor.extractFromException(e);
    log.error("[{}] {}", e.getErrorCode(), e.getMessage());
}
```

### 与进化系统集成
```java
// 从异常提取信号
SignalExtractor extractor = new SignalExtractor();
AutivaException ex = StorageException.readError("/data/genes.json", ioException);
Signal signal = extractor.extractFromException(ex);
// signal.type() → SignalType.MEMORY_MISSING
```

## 设计原则
1. 所有自定义异常必须继承 `AutivaException`
2. 优先使用工厂方法创建异常，确保 errorCode 一致
3. 异常消息使用中文，与项目整体风格一致
4. 每个异常必须携带有意义的 errorCode，用于信号映射
5. `recoverable=true` 标记可重试的操作（如网络超时、临时文件锁）
6. 不再使用原生 `RuntimeException`，替换为对应的领域异常
7. 不再使用 `SecurityException`，替换为 `SecurityViolationException`
