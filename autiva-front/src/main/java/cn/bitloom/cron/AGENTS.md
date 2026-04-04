# Cron 包

## 概述
本包实现了定时任务管理功能，提供统一的调度任务管理服务。CronManager 是核心管理类，被 CronTool 和 TaskPageController 共同使用。

## 核心类

### CronManager
定时任务管理器，提供任务的创建、查询、删除和触发功能。

**Spring 注解：** `@Component`

**依赖注入：**
- `TaskScheduler`: Spring 任务调度器

**核心方法：**
- `createTask()`: 创建定时任务（需要sessionId）
- `getAllTasks(sessionId)`: 获取指定sessionId下的所有任务（传null获取所有任务）
- `getTask(sessionId, name)`: 获取指定任务（需要验证sessionId）
- `deleteTask(sessionId, name)`: 删除任务（需要验证sessionId权限）
- `triggerTask(sessionId, name)`: 手动触发任务（需要验证sessionId权限）

**任务存储：**
- 使用 `ConcurrentHashMap` 存储任务信息
- Key: 任务名称
- Value: CronTaskInfo 对象

## 数据模型

### CronTaskInfo
定时任务信息类。

**属性：**
- `sessionId`: String - 会话ID（用于任务隔离和权限控制）
- `name`: String - 任务名称（唯一标识）
- `type`: String - 任务类型（once/interval/cron）
- `intervalSeconds`: Integer - 间隔秒数（interval 类型）
- `delaySeconds`: Integer - 延迟秒数（once/interval 类型）
- `cronExpression`: String - Cron 表达式（cron 类型）
- `message`: String - 触发时发送的消息
- `scheduledFuture`: ScheduledFuture - 调度任务句柄
- `createTime`: Instant - 创建时间

## 任务类型

### once - 一次性任务
- 在指定的延迟秒数后执行一次
- 执行后自动从任务列表中移除
- 必须指定 `delaySeconds` 参数

**示例：**
```java
cronManager.createTask("reminder", "once", null, 10, null, "休息一下");
```

### interval - 周期性任务
- 按照固定的间隔秒数重复执行
- 必须指定 `intervalSeconds` 参数
- 可选指定 `delaySeconds` 作为初始延迟

**示例：**
```java
// 立即开始，每60秒执行一次
cronManager.createTask("health-check", "interval", 60, null, null, "检查系统状态");

// 延迟10秒后开始，每60秒执行一次
cronManager.createTask("delayed-check", "interval", 60, 10, null, "延迟检查");
```

### cron - Cron表达式任务
- 按照 Cron 表达式的时间规则执行
- 必须指定 `cronExpression` 参数
- 支持标准 Cron 表达式格式

**示例：**
```java
// 每小时执行
cronManager.createTask("hourly-report", "cron", null, null, "0 0 * * * ?", "生成报告");

// 每天早上9点执行
cronManager.createTask("daily-task", "cron", null, null, "0 0 9 * * ?", "每日任务");
```

## SessionId 隔离机制

任务管理采用sessionId隔离机制，确保不同会话的任务相互独立：

**权限控制：**
- 创建任务时必须指定sessionId
- 删除、触发任务时验证sessionId匹配
- 查询任务时可按sessionId过滤（传null获取所有任务）

**示例：**
```java
// 创建任务（指定sessionId）
cronManager.createTask("reminder", "once", null, 10, null, "休息一下", "session-123");

// 获取指定sessionId下的任务
Map<String, CronTaskInfo> tasks = cronManager.getAllTasks("session-123");

// 获取所有任务（不过滤）
Map<String, CronTaskInfo> allTasks = cronManager.getAllTasks(null);

// 删除任务（验证sessionId）
cronManager.deleteTask("session-123", "reminder");

// 触发任务（验证sessionId）
cronManager.triggerTask("session-123", "reminder");
```

## EventBus 集成

任务触发时通过 EventBus 发送消息：

**SessionId 格式：**
```
任务触发时使用任务创建时指定的sessionId
```

**消息类型：**
- `UserMessage`: 封装任务消息内容

**发送方式：**
```java
EventBus.inBoxPublish(sessionId, userMessage);
```

## 日志规范

所有操作都会记录日志，格式统一为 `[CronManager] {操作描述}: {参数信息}`。

**日志格式示例：**
```
[CronManager] 创建定时任务: name=task1, type=once
[CronManager] 创建成功: name=task1
[CronManager] 定时任务触发: name=task1
[CronManager] 任务消息已发送到EventBus: name=task1, sessionId=cron-task-task1
[CronManager] 删除定时任务: name=task1
[CronManager] 删除成功: name=task1
```

## 使用场景

### 1. 通过工具调用（CronTool）
智能体通过工具调用创建和管理定时任务：
```java
// 在智能体对话中
用户: "10秒后提醒我休息"
AI: 调用 cron_create("reminder", "once", null, 10, null, "休息一下")
```

### 2. 通过UI管理（TaskPageController）
用户通过任务管理页面查看和管理任务：
- 查看所有任务列表
- 手动触发任务
- 删除任务
- 刷新任务列表

## 设计模式

### 单一职责原则
- CronManager: 负责任务调度核心逻辑
- CronTool: 负责工具接口适配
- TaskPageController: 负责 UI 交互

### 依赖注入
- 通过 Spring 的依赖注入机制共享 CronManager 实例
- 确保任务状态的统一管理

## 注意事项

1. 任务名称必须唯一，重复创建会抛出异常
2. 一次性任务执行后自动删除，无需手动清理
3. 任务创建时必须指定sessionId，用于任务隔离和权限控制
4. 删除和触发任务时会验证sessionId，无权限会抛出异常
5. 所有时间参数使用秒为单位
6. Cron 表达式遵循标准格式（6或7位）
