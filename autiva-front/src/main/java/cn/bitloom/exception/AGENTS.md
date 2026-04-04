# Exception 包

## 概述
本包定义了应用的自定义异常类，用于区分不同类型的错误。

## 异常类

### AgentException
智能体相关异常。

**使用场景：**
- 智能体初始化失败
- 智能体通信错误
- 智能体状态异常

**构造方法：**
- `AgentException()`: 无消息异常
- `AgentException(String message)`: 带消息异常
- `AgentException(String message, Throwable cause)`: 带消息和原因的异常

### ToolException
工具相关异常。

**使用场景：**
- 工具调用失败
- 工具参数错误
- 工具不存在

**构造方法：**
- `ToolException()`: 无消息异常
- `ToolException(String message)`: 带消息异常
- `ToolException(String message, Throwable cause)`: 带消息和原因的异常

### WorkFlowException
工作流相关异常。

**使用场景：**
- 工作流配置错误
- 节点执行失败
- 图结构无效

**构造方法：**
- `WorkFlowException()`: 无消息异常
- `WorkFlowException(String message)`: 带消息异常
- `WorkFlowException(String message, Throwable cause)`: 带消息和原因的异常

## 使用示例

### 抛出异常
```java
public void executeAgent() {
    if (agent == null) {
        throw new AgentException("智能体未初始化");
    }
}

public void callTool(String toolName) {
    if (!tools.containsKey(toolName)) {
        throw new ToolException("工具不存在: " + toolName);
    }
}

public void loadWorkflow(String path) {
    try {
        // 加载配置...
    } catch (IOException e) {
        throw new WorkFlowException("加载工作流配置失败: " + path, e);
    }
}
```

### 捕获异常
```java
try {
    agent.run();
} catch (AgentException e) {
    log.error("智能体执行失败", e);
    // 处理异常...
}
```

## 设计原则
1. 继承 RuntimeException，作为非检查异常
2. 提供多种构造方法
3. 保持异常类简洁，不添加额外逻辑
4. 异常消息应该清晰描述问题

## 扩展指南
创建新的异常类：
```java
public class MyException extends RuntimeException {
    public MyException() {}
    public MyException(String message) { super(message); }
    public MyException(String message, Throwable cause) { super(message, cause); }
}
```

## 注意事项
1. 异常应该用于异常情况，不是正常控制流
2. 异常消息应该包含足够的上下文信息
3. 捕获异常时应该记录日志
4. 避免异常吞没，至少记录日志
