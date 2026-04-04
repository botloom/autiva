# Workflow Config 包

## 概述
本包实现了工作流配置管理，支持从 JSON 文件加载工作流配置。

## 核心类

### WorkflowConfig
工作流配置实体。

**字段：**
- `id`: 工作流 ID
- `name`: 工作流名称
- `description`: 工作流描述
- `graph`: 图结构（节点和边）

### WorkflowConfigLoader
配置加载器，从 JSON 文件加载工作流配置。

**核心方法：**
- `loadWorkflowConfig(resourcePath)`: 从资源路径加载配置

**缓存机制：**
- 使用 ConcurrentHashMap 缓存已加载的配置
- 避免重复解析 JSON 文件

## 配置文件格式

```json
{
  "id": "workflow-001",
  "name": "示例工作流",
  "description": "工作流描述",
  "graph": {
    "nodeList": [
      {
        "id": "node-1",
        "type": "start",
        "name": "开始节点",
        "params": {
          "systemPrompt": "系统提示词",
          "toolSet": ["tool1", "tool2"],
          "retryCount": 3
        }
      },
      {
        "id": "node-2",
        "type": "process",
        "name": "处理节点",
        "params": {
          "systemPrompt": "处理提示词"
        }
      }
    ],
    "arcList": [
      {
        "tailId": "node-1",
        "headId": "node-2",
        "params": {
          "script": "#result == 'success'"
        }
      }
    ]
  }
}
```

## 使用示例

### 加载配置
```java
WorkflowConfig config = workflowConfigLoader.loadWorkflowConfig(
    "classpath:workflows/my-workflow.json"
);
```

### 创建工作流
```java
AbstractWorkflow workflow = workflowFactory.createWorkflowFromConfig(
    "classpath:workflows/my-workflow.json",
    context
);
```

## 配置说明

### 节点参数 (VertexParam)
- `systemPrompt`: 系统提示词
- `toolSet`: 可用工具集合
- `advisorSet`: Advisor 集合
- `retryCount`: 重试次数
- `extraParams`: 额外参数

### 边参数 (ArcParam)
- `script`: SpEL 条件表达式

## 注意事项
1. 配置文件使用 JSON 格式
2. 支持类路径资源和文件系统资源
3. 配置会被缓存，修改后需重启应用
4. 图结构必须有效（无环、有根节点）
