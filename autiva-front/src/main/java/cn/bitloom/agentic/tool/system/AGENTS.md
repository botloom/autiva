# System Tool 包

## 概述
本包实现了系统级工具集，参考 OpenClaw 的内置工具设计，提供文件操作、命令执行、进程管理、定时任务等基础能力。这些工具是智能体的"手脚"，让智能体能够与文件系统、操作系统进行交互。

## 日志规范

所有系统工具调用都会记录日志，格式统一为 `[ToolCall] {工具名} - {操作描述}: {参数信息}`。

**日志格式示例：**
```
[ToolCall] read - 读取文件: filePath=/path/to/file
[ToolCall] read - 读取成功: filePath=/path/to/file, size=1024
[ToolCall] exec - 执行命令: command=ls -la, background=false
[ToolCall] exec - 命令完成: command=ls -la, exitCode=0
[ToolCall] write - 写入文件: filePath=/path/to/file
[ToolCall] write - 写入成功: filePath=/path/to/file, bytes=512
```

## 核心工具

### ReadTool
文件读取工具。

**工具名称：** `read`

**功能：**
- 读取文件内容（文本和图片）
- 自动截断过大文件（超过1MB截断到10KB）
- 图片文件显示基本信息而非内容

**参数：**
- `filePath`: 文件路径（绝对路径或相对路径）

**日志输出：**
- 调用时：`[ToolCall] read - 读取文件: filePath={}`
- 成功时：`[ToolCall] read - 读取成功: filePath={}, size={}`
- 失败时：`[ToolCall] read - 读取失败: filePath={}`

**使用示例：**
```
read("/path/to/file.txt")
read("src/main/java/App.java")
```

---

### WriteTool
文件写入工具。

**工具名称：** `write`

**功能：**
- 写入内容到文件
- 自动创建父目录
- 覆盖已存在文件

**参数：**
- `filePath`: 文件路径
- `content`: 要写入的内容

**安全限制：** 只能在 `~/.autiva` 或当前工作目录下写入

**日志输出：**
- 调用时：`[ToolCall] write - 写入文件: filePath={}`
- 创建目录：`[ToolCall] write - 创建父目录: {}`
- 成功时：`[ToolCall] write - 写入成功: filePath={}, bytes={}`
- 失败时：`[ToolCall] write - 写入失败: filePath={}`

**使用示例：**
```
write("/path/to/newfile.txt", "Hello World")
write("src/main/java/NewClass.java", "package com.example; ...")
```

---

### EditTool
精确文件编辑工具。

**工具名称：** `edit`

**功能：**
- 通过精确文本匹配进行替换
- 适用于"手术式"修改
- 只支持单处匹配替换

**参数：**
- `filePath`: 文件路径
- `oldText`: 要查找的精确文本
- `newText`: 要替换成的新文本

**限制：**
- 只能匹配单处文本
- 多处匹配会返回错误

**日志输出：**
- 调用时：`[ToolCall] edit - 编辑文件: filePath={}`
- 成功时：`[ToolCall] edit - 编辑成功: filePath={}, 替换 1 处文本`
- 失败时：`[ToolCall] edit - 编辑失败: filePath={}`

**使用示例：**
```
edit("/path/to/file.java", "oldValue", "newValue")
```

---

### ExecTool
命令执行工具。

**工具名称：** `exec`

**功能：**
- 在宿主机上执行 shell 命令
- 支持后台运行
- 可设置超时时间

**参数：**
- `command`: 要执行的命令
- `background`: 是否后台运行（默认 false）
- `timeoutSeconds`: 超时时间（默认 60 秒）

**日志输出：**
- 调用时：`[ToolCall] exec - 执行命令: command={}, background={}`
- 后台启动：`[ToolCall] exec - 后台进程启动: processId={}, command={}`
- 超时时：`[ToolCall] exec - 命令超时: command={}`
- 成功时：`[ToolCall] exec - 命令完成: command={}, exitCode={}`
- 失败时：`[ToolCall] exec - 执行失败: command={}`

**使用示例：**
```java
// 普通执行
exec("ls -la")

// 后台执行
exec("npm run dev", true)

// 带超时
exec("git clone https://github.com/repo", false, 30)
```

**返回信息：**
- 退出码
- 命令输出
- 超时提示

---

### ProcessTool
进程管理工具。

**工具名称：** `process_list`, `process_kill`, `process_log`, `process_status`

**功能：**
- `process_list`: 列出所有后台进程
- `process_kill`: 终止指定进程
- `process_log`: 获取进程实时输出
- `process_status`: 查看进程详细状态

**日志输出：**
- process_list 调用：`[ToolCall] process_list - 列出所有后台进程`
- process_list 完成：`[ToolCall] process_list - 查询完成, 共 {} 个进程`
- process_kill 调用：`[ToolCall] process_kill - 终止进程: processId={}`
- process_kill 成功：`[ToolCall] process_kill - 终止成功: processId={}`
- process_log 调用：`[ToolCall] process_log - 获取进程日志: processId={}`
- process_status 调用：`[ToolCall] process_status - 查看进程状态: processId={}`

**使用示例：**
```
process_list()
process_kill("proc-123456")
process_log("proc-123456")
process_status("proc-123456")
```

---

### CronTool
定时任务管理工具。

**工具名称：** `cron_create`, `cron_list`, `cron_delete`, `cron_trigger`

**依赖注入：**
- `CronManager`: 通过构造器注入，委托所有任务管理操作

**功能：**
- 创建一次性/周期性/cron 表达式的定时任务
- 任务触发时发送事件到 EventBus
- 支持手动触发

**参数：**
- `name`: 任务名称（唯一标识）
- `type`: 触发类型（once/interval/cron）
- `intervalSeconds`: 间隔秒数（interval 类型必填）
- `delaySeconds`: 延迟秒数（once 类型必填，interval 类型可选）
- `cronExpression`: cron 表达式（cron 类型必填）
- `message`: 触发时发送的消息

**日志输出：**
- cron_create 调用：`[ToolCall] cron_create - 创建定时任务: name={}, type={}`
- cron_create 成功：`[ToolCall] cron_create - 创建成功: name={}`
- cron_list 调用：`[ToolCall] cron_list - 列出所有定时任务`
- cron_list 完成：`[ToolCall] cron_list - 查询完成, 共 {} 个任务`
- cron_delete 调用：`[ToolCall] cron_delete - 删除定时任务: name={}`
- cron_delete 成功：`[ToolCall] cron_delete - 删除成功: name={}`
- cron_trigger 调用：`[ToolCall] cron_trigger - 手动触发定时任务: name={}`
- cron_trigger 成功：`[ToolCall] cron_trigger - 触发成功: name={}`

**实现细节：**
- 所有操作委托给 CronManager 处理
- CronManager 负责任务调度和状态管理
- 任务触发时通过 EventBus.inBoxPublish 发送消息
- 一次性任务执行后自动移除

**使用示例：**
```java
// 一次性任务（10秒后执行）
cron_create("task1", "once", null, 10, null, "要发送的消息")

// 周期性任务（每60秒执行）
cron_create("task2", "interval", 60, null, null, "周期性消息")

// 周期性任务（延迟10秒后开始，每60秒执行）
cron_create("task3", "interval", 60, 10, null, "延迟周期性消息")

// Cron 表达式任务（每小时执行）
cron_create("task4", "cron", null, null, "0 0 * * *", "每小时执行")

// 列出所有任务
cron_list()

// 手动触发任务
cron_trigger("task1")

// 删除任务
cron_delete("task1")
```

**任务类型说明：**
- `once`: 一次性任务，在指定的延迟秒数后执行一次，执行后自动删除
- `interval`: 周期性任务，按照指定的间隔秒数重复执行
- `cron`: Cron表达式任务，按照Cron表达式的时间规则执行

**EventBus 集成：**
- 任务触发时生成唯一的 sessionId: `cron-task-{任务名称}`
- 使用 UserMessage 封装消息内容
- 通过 EventBus.inBoxPublish 发送到事件总线

**与 CronManager 的关系：**
- CronTool 是工具层适配器
- 所有核心逻辑由 CronManager 实现
- 支持多个消费者共享同一个 CronManager 实例

---

### WebSearchTool
网页搜索工具。

**工具名称：** `web_search`

**功能：**
- 通过 DuckDuckGo 搜索
- 无需 API 密钥

**参数：**
- `query`: 搜索关键词
- `limit`: 返回结果数量（默认 10）

**日志输出：**
- 调用时：`[ToolCall] web_search - 搜索网页: query={}, limit={}`
- 无结果：`[ToolCall] web_search - 无结果: query={}`
- 成功时：`[ToolCall] web_search - 搜索成功: query={}, 结果数={}`
- 失败时：`[ToolCall] web_search - 搜索失败: query={}`

**使用示例：**
```
web_search("Java Spring AI tutorial")
web_search("OpenClaw agent framework", 5)
```

---

### WebFetchTool
网页内容抓取工具。

**工具名称：** `web_fetch`

**功能：**
- 抓取 URL 并提取可读内容
- HTML 转换为纯文本
- 自动去除脚本和样式

**参数：**
- `url`: 要抓取的网页 URL
- `maxLength`: 最大内容长度（默认 50000 字符）

**日志输出：**
- 调用时：`[ToolCall] web_fetch - 抓取网页: url={}`
- 成功时：`[ToolCall] web_fetch - 抓取成功: url={}, length={}`
- 失败时：`[ToolCall] web_fetch - 抓取失败: url={}`

**使用示例：**
```
web_fetch("https://example.com")
web_fetch("https://news.ycombinator.com", 10000)
```

---

## 安全机制

### 路径安全限制
- 文件操作限制在 `~/.autiva` 和当前工作目录下
- 防止路径遍历攻击

### 命令执行限制
- 默认 60 秒超时
- 输出截断（最多 1000 行）
- 后台进程需明确指定

## 与其他组件的关系

### 与 EventBus 的整合
- CronTool 触发时发布事件到 EventBus
- EventBus 负责事件路由

### 与 ToolManager 的整合
- 所有工具实现 ITool 接口
- Spring 自动扫描注册到 ToolManager

## 工具使用场景

| 场景 | 推荐工具 |
|------|---------|
| 读取代码文件 | `read` |
| 生成新文件 | `write` |
| 修改配置文件 | `edit` |
| 运行编译/测试 | `exec` |
| 启动服务 | `exec(background=true)` + `process_*` |
| 定时检查 | `cron_*` |
| 搜索资料 | `web_search` |
| 获取网页内容 | `web_fetch` |