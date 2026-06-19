# Task Repository 包

## 概述
后台任务仓库，管理子代理的后台执行任务。来自 spring-ai-agent-utils 项目。

## 核心接口

### TaskRepository
后台任务仓库接口。

**方法：**
- `getTasks()`: 获取所有后台任务
- `putTask(String, Supplier<String>)`: 创建并存储后台任务
- `removeTask(String)`: 移除任务
- `clear()`: 清空所有任务

## 核心类

### DefaultTaskRepository
默认任务仓库实现。

**特性：**
- 基于 ConcurrentHashMap 存储
- 使用守护线程池（ExecutorService）执行后台任务
- 线程安全

### BackgroundTask
后台任务封装。

**特性：**
- 基于 CompletableFuture 实现
- 提供状态查询、等待、取消等操作
- 支持任务ID标识

**方法：**
- `getTaskId()`: 获取任务ID
- `isDone()`: 检查任务是否完成
- `isCancelled()`: 检查任务是否已取消
- `get()`: 阻塞等待任务结果
- `get(long, TimeUnit)`: 带超时等待任务结果
- `cancel(boolean)`: 取消任务
