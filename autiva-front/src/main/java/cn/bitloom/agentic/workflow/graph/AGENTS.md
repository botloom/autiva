# Workflow Graph 包

## 概述
本包实现了基于十字链表的图数据结构，用于表示工作流的节点和边关系。

## 核心类

### Graph
图结构，使用十字链表实现。

**核心方法：**
- `create()`: 创建空图
- `addVertex(id, type, param)`: 添加节点
- `addArc(tailId, headId, param)`: 添加边
- `getVertex(id)`: 获取节点
- `getInArc(vertexId)`: 获取入边列表
- `getOutArc(vertexId)`: 获取出边列表
- `getRootVertex()`: 获取所有根节点
- `createGraphFromJson(json)`: 从 JSON 创建图

### VertexNode
节点类，表示图中的一个节点。

**字段：**
- `id`: 节点 ID
- `type`: 节点类型
- `name`: 节点名称
- `params`: 节点参数
- `firstOut`: 第一条出边
- `firstIn`: 第一条入边

### ArcNode
边类，表示图中的一条有向边。

**字段：**
- `tailVexId`: 尾节点 ID（边的起点）
- `headVexId`: 头节点 ID（边的终点）
- `tailLink`: 指向下一个同尾节点的边
- `headLink`: 指向下一个同头节点的边
- `parms`: 边参数

### VertexParam
节点参数类。

**字段：**
- `systemPrompt`: 系统提示词
- `retryCount`: 重试次数
- `toolSet`: 可用工具集合
- `advisorSet`: Advisor 集合
- `extraParams`: 额外参数

### ArcParam
边参数类。

**字段：**
- `script`: SpEL 条件表达式

## 十字链表结构

```
节点A ──出边──> 节点B
  │              │
  入边           入边
  │              │
节点C ──出边──> 节点D
```

**特点：**
- 每个节点维护入边链表和出边链表
- 可以快速查找前驱和后继节点
- 适合有向图的遍历

## 使用示例

### 创建图
```java
Graph graph = Graph.create();
graph.addVertex("node-1", "start", new VertexParam());
graph.addVertex("node-2", "process", new VertexParam());
graph.addArc("node-1", "node-2", new ArcParam());
```

### 遍历图
```java
List<VertexNode> roots = graph.getRootVertex();
for (VertexNode root : roots) {
    List<ArcNode> outArcs = graph.getOutArc(root.getId());
    // 处理后续节点...
}
```

### 从 JSON 创建
```java
JSONObject json = ...;
Graph graph = Graph.createGraphFromJson(json);
```

## 设计说明
- 使用十字链表存储图结构
- 支持快速查找前驱和后继
- 支持从 JSON 反序列化
- 适合 DAG（有向无环图）结构

## 注意事项
1. 添加边时，节点必须已存在
2. 根节点是入边为空的节点
3. 图应该是有向无环图（DAG）
4. 边参数中的 script 用于条件分支
