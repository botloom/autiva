# Workflow 包

## 概述
本包实现了基于图的工作流引擎，支持复杂的多步骤任务编排。

## 核心类

### AbstractNode
工作流节点抽象类。

**核心方法：**
- `run(params, ctx)`: 执行节点逻辑，返回 Mono<WorkFlowEvent>

### AbstractWorkflow
工作流抽象基类。

**核心方法：**
- `start()`: 启动工作流，返回 Flux<WorkFlowEvent>
- `getName()`: 获取工作流名称
- `eval(script)`: 执行 SpEL 表达式
- `stop()`: 停止工作流

### GraphWorkflow
基于图的工作流实现。

**执行流程：**
1. 找到所有根节点（入弧为空）
2. 从根节点开始递归执行
3. 按依赖关系执行后续节点
4. 支持条件分支（SpEL 表达式）
5. 支持重试机制

### WorkFlowContext
工作流上下文，存储执行过程中的数据。

**核心功能：**
- 参数存储和获取
- 消息流管理
- 运行状态控制

**核心方法：**
- `putParam(key, value)`: 存储参数
- `getParam(key, clazz)`: 获取参数
- `clear()`: 清除上下文

### WorkFlowEvent
工作流事件，表示工作流执行过程中的各种事件。

**事件类型：**
- `START`: 工作流开始
- `NODE_START`: 节点开始执行
- `NODE_COMPLETE`: 节点执行完成
- `COMPLETE`: 工作流完成
- `ERROR`: 发生错误
- `CANCELLED`: 工作流取消

### WorkflowFactory
工作流工厂类。

**核心方法：**
- `createWorkFlow(id, name, graph, context)`: 代码创建工作流
- `createWorkflowFromConfig(configPath, context)`: 从配置文件创建工作流

## 使用示例

### 创建工作流
```java
WorkFlowContext context = new WorkFlowContext();
context.putParam("input", "用户输入");

AbstractWorkflow workflow = workflowFactory.createWorkflowFromConfig(
    "classpath:workflows/my-workflow.json", 
    context
);

workflow.start()
    .subscribe(event -> {
        System.out.println("Event: " + event.getType());
    });
```

### 创建自定义节点
```java
@Component
public class MyNode extends AbstractNode {
    @Override
    public Mono<WorkFlowEvent> run(VertexParam params, WorkFlowContext ctx) {
        String input = ctx.getParam("input", String.class);
        // 处理逻辑...
        ctx.putParam("output", result);
        return Mono.just(WorkFlowEvent.createNodeCompleteEvent(...));
    }
}
```

## 设计模式
- 模板方法模式：AbstractWorkflow 定义骨架
- 工厂模式：WorkflowFactory 创建工作流
- 观察者模式：通过 Flux 发送事件

## 注意事项
1. 节点执行是异步的，返回 Mono
2. 使用 SpEL 表达式实现条件分支
3. 工作流执行完毕后自动清除上下文
4. 支持重试机制，可配置重试次数
