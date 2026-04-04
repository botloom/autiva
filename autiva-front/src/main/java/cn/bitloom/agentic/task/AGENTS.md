# Task 包

## 概述
本包实现了任务管理系统，支持任务的创建、更新、依赖管理和状态跟踪。

## 核心类

### Task
任务实体类。

**字段：**
- `id`: 任务 ID（自增）
- `subject`: 任务主题
- `description`: 任务描述
- `status`: 任务状态（pending/in_progress/completed）
- `blockedBy`: 阻塞此任务的任务 ID 列表
- `blocks`: 此任务阻塞的任务 ID 列表
- `owner`: 任务所有者
- `createdAt`: 创建时间
- `updatedAt`: 更新时间

### TaskStatusEnum
任务状态枚举，定义任务在智能体系统中的完整生命周期：
- `PENDING`: 待处理
- `ZHONGSHU_PLANNING`: 中书省规划中
- `ZHONGSHU_PLANNED`: 中书省规划完成
- `MENXIA_REVIEWING`: 门下省审核中
- `MENXIA_APPROVED`: 门下省批准
- `MENXIA_REJECTED`: 门下省驳回
- `SHANGSHU_DISPATCHED`: 尚书省已派发
- `DOING`: 执行中
- `PENDING_REVIEW`: 待审核
- `BLOCKED`: 被阻塞
- `DONE`: 完成
- `FAILED`: 失败

### TaskManager
任务管理器。

**核心方法：**
- `create(subject, description)`: 创建任务
- `update(taskId, status, addBlockedBy, addBlocks)`: 更新任务
- `list()`: 列出所有任务
- `getTask(taskId)`: 获取任务详情
- `scanUnclaimed()`: 扫描未认领的任务
- `claim(taskId)`: 认领任务

## 使用示例

### 创建任务
```java
String taskJson = taskManager.create("实现登录功能", "实现用户登录功能，包括表单验证和后端接口");
// 返回 JSON 格式的任务信息
```

### 更新任务状态
```java
taskManager.update(taskId, "in_progress", null, null);
```

### 添加依赖关系
```java
// 任务 2 依赖任务 1
taskManager.update(2L, null, List.of(1L), null);
```

### 认领任务
```java
String result = taskManager.claim(taskId);
```

## 依赖管理
任务之间可以建立依赖关系：
- `blockedBy`: 此任务依赖的其他任务
- `blocks`: 此任务阻塞的其他任务
- 任务完成时自动清除依赖关系

## 工具集成
通过 TaskTool 暴露给智能体：
- `taskCreate`: 创建任务
- `taskUpdate`: 更新任务
- `taskList`: 列出任务
- `taskGet`: 获取任务详情
- `scanUnclaimedTasks`: 扫描未认领任务
- `claimTask`: 认领任务

## 注意事项
1. 任务 ID 自动生成，从 1 开始递增
2. 依赖关系是双向维护的
3. 完成任务会自动清除相关依赖
4. 使用 ConcurrentHashMap 保证线程安全
