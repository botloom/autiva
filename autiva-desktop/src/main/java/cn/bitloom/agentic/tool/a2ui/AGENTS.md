# A2UI 工具模块

## 概述
本包实现 A2UI 动态界面生成工具,Agent 通过此工具生成 A2UI v0.9.1 消息。

## A2UITool
继承 AbstractTool<A2UITool.Input>,工具名 "A2UI"。

### Input 参数
- `operation` - 操作类型: create/update_components/update_data/delete
- `surfaceId` - Surface 唯一标识
- `components` - 组件列表 JSON(仅 update_components)
- `path` - 数据路径 JSON Pointer(仅 update_data)
- `value` - 数据值 JSON(仅 update_data)
- `theme` - 主题配置 JSON(仅 create,可选)

### 执行流程
1. 从 ToolContext 获取 sessionId
2. 根据 operation 构建对应的 A2UIMessage
3. 通过 A2UIHandler 回调发送到 ToolUIBridge
4. 返回 ToolResult.success(result)

### A2UIHandler 接口
```java
@FunctionalInterface
interface A2UIHandler {
    CompletableFuture<String> handle(A2UIMessage message, String sessionId);
}
```
由 ToolUIBridge.handleA2UIMessage 实现。

### 组件 JSON 解析
- `parseComponents(String)` - 解析组件 JSON 数组
- `parseComponent(JsonNode)` - 解析单个组件(id/component/properties/action/checks)
- `parseAction(JsonNode)` - 解析动作(Event/FunctionCall)
- `parseCheck(JsonNode)` - 解析验证规则

### Builder
```java
A2UITool.builder()
    .handler(toolUIBridge::handleA2UIMessage)
    .build();
```

## 注册
在 Toolkit.buildAllTools() 中注册:
```java
tools.add(A2UITool.builder()
        .handler(toolUIBridge::handleA2UIMessage).build());
```
