# System Tool 包

## 概述
本包实现了系统级工具集，参考 Claude Code 的内置工具设计，提供文件操作、命令执行、进程管理、定时任务、文件搜索等基础能力。这些工具是智能体的"手脚"，让智能体能够与文件系统、操作系统进行交互。

## 日志规范

所有系统工具调用都会记录日志，格式统一为 `[ToolCall] {工具名} - {操作描述}: {参数信息}`。

**日志格式示例：**
```
[ToolCall] read - 读取文件: filePath=/path/to/file
[ToolCall] read - 读取成功: filePath=/path/to/file, lines=1-100, total=500
[ToolCall] exec - 执行命令: command=ls -la, background=false
[ToolCall] exec - 命令完成: command=ls -la, exitCode=0, duration=50ms
[ToolCall] glob - 搜索文件: pattern=**/*.java, path=/project/src
[ToolCall] grep - 搜索内容: pattern=TODO, outputMode=files
```

## 核心工具

### ReadTool
文件读取工具。

**工具名称：** `read`

**功能：**
- 读取文件内容（文本和图片）
- 支持分页读取（offset/limit）
- 显示行号
- 自动截断过长行（超过500字符）
- 二进制文件检测

**参数：**
- `filePath`: 文件路径（绝对路径或相对路径）
- `offset`: 起始行号（从1开始），默认为1
- `limit`: 读取的行数，默认2000行

**输出格式：**
```
文件: /path/to/file.java (大小: 10.5 KB, 总行数: 500)
显示: 第 1-100 行 (共 100 行)

     1→package com.example;
     2→
     3→public class Example {
     ...
```

**日志输出：**
- 调用时：`[ToolCall] read - 读取文件: filePath={}, offset={}, limit={}`
- 成功时：`[ToolCall] read - 读取成功: filePath={}, lines={}-{}, total={}`
- 失败时：`[ToolCall] read - 读取失败: filePath={}`

**使用示例：**
```java
read("/path/to/file.txt")
read("/path/to/file.java", 100, 50)  // 从第100行开始读取50行
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

---

### EditTool
精确文件编辑工具。

**工具名称：** `edit`

**功能：**
- 通过精确文本匹配进行替换
- 支持替换单处或所有匹配
- 智能匹配预览
- 相似文本提示

**参数：**
- `filePath`: 文件路径
- `oldText`: 要查找的精确文本
- `newText`: 要替换成的新文本
- `replaceAll`: 是否替换所有匹配，默认false

**输出示例：**
```
成功编辑文件: /path/to/file.java
- 替换了 3 处文本
```

**多匹配提示：**
```
错误：找到 5 处匹配，但 replaceAll=false。

edit 工具默认只替换第一处匹配。
选项：
1. 设置 replaceAll=true 替换所有匹配
2. 提供更精确的上下文来唯一标识要替换的位置

匹配位置预览：
  ...public void method() {
      ^^^^^^^^^^^^^^^^^^^
```

**日志输出：**
- 调用时：`[ToolCall] edit - 编辑文件: filePath={}, replaceAll={}`
- 成功时：`[ToolCall] edit - 编辑成功: filePath={}, 替换 N 处文本`
- 失败时：`[ToolCall] edit - 编辑失败: filePath={}`

---

### ExecTool
命令执行工具。

**工具名称：** `exec`, `process_list`, `process_status`, `process_log`, `process_kill`

**功能：**
- 在宿主机上执行 shell 命令
- 支持后台运行
- 可设置超时时间
- 可设置工作目录
- 进程管理（列表、状态、日志、终止）

**参数（exec）：**
- `command`: 要执行的命令
- `background`: 是否后台运行（默认 false）
- `timeoutSeconds`: 超时时间（默认 60 秒）
- `workingDir`: 工作目录

**输出示例：**
```
命令: npm run build
退出码: 0
执行时间: 5230ms

输出:
> project@1.0.0 build
> webpack --mode production
...
```

**后台进程输出：**
```
已在后台启动进程
- 进程ID: proc-1234567890-1
- 命令: npm run dev
- 超时: 60秒

提示：使用 process_status 查看状态，process_log 获取输出，process_kill 终止进程
```

**进程管理工具：**
- `process_list`: 列出所有后台进程
- `process_status`: 查看进程详细状态
- `process_log`: 获取进程输出日志
- `process_kill`: 终止进程

**日志输出：**
- 调用时：`[ToolCall] exec - 执行命令: command={}, background={}, workingDir={}`
- 后台启动：`[ToolCall] exec - 后台进程启动: processId={}, command={}`
- 超时时：`[ToolCall] exec - 命令超时: command={}`
- 成功时：`[ToolCall] exec - 命令完成: command={}, exitCode={}, duration={}ms`

---

### GlobTool
文件模式搜索工具。

**工具名称：** `glob`

**功能：**
- 使用通配符模式搜索文件
- 支持 `**`、`*`、`?` 等通配符
- 按修改时间排序结果
- 自动跳过隐藏目录和 VCS 目录

**参数：**
- `pattern`: 通配符模式（如 `**/*.java`、`src/**/*.ts`）
- `path`: 搜索目录（默认当前工作目录）
- `limit`: 最大结果数（默认100）

**输出示例：**
```
搜索模式: **/*.java
搜索目录: /project/src
找到文件: 25 个
搜索耗时: 15ms

文件列表:
  main/java/com/example/App.java
  main/java/com/example/Service.java
  ...
```

**通配符说明：**
- `*`: 匹配任意数量的字符（不包括目录分隔符）
- `**`: 匹配任意数量的字符（包括目录分隔符）
- `?`: 匹配单个字符
- `[abc]`: 匹配 a、b 或 c

**使用示例：**
```java
glob("**/*.java")                    // 搜索所有 Java 文件
glob("src/**/*.ts", "/project")      // 在指定目录搜索 TypeScript 文件
glob("test/**/*Test.java", null, 50) // 限制结果数量
```

---

### GrepTool
文件内容搜索工具。

**工具名称：** `grep`

**功能：**
- 在文件内容中搜索正则表达式模式
- 支持多种输出模式（内容、文件列表、计数）
- 支持忽略大小写
- 支持上下文行显示
- 自动跳过二进制文件和 VCS 目录

**参数：**
- `pattern`: 要搜索的正则表达式模式
- `path`: 搜索路径（默认当前工作目录）
- `glob`: 文件类型过滤（如 `*.java`）
- `outputMode`: 输出模式
  - `content`: 显示匹配行（支持上下文）
  - `files`: 仅显示文件名（默认）
  - `count`: 显示匹配计数
- `ignoreCase`: 是否忽略大小写（默认 false）
- `context`: 显示匹配行前后的上下文行数
- `headLimit`: 限制输出行数/文件数（默认250）

**输出示例（files 模式）：**
```
找到 10 个文件
搜索耗时: 50ms

src/main/java/App.java
src/main/java/Service.java
...
```

**输出示例（content 模式）：**
```
找到 15 处匹配
搜索耗时: 80ms

文件: src/main/java/App.java
  10:public class App {
  11:    private Service service;
  12:    // TODO: implement

文件: src/main/java/Service.java
  5:// TODO: add error handling
...
```

**输出示例（count 模式）：**
```
找到 25 处匹配
搜索耗时: 45ms

src/main/java/App.java: 5
src/main/java/Service.java: 8
...
```

**使用示例：**
```java
grep("TODO")                              // 搜索包含 TODO 的文件
grep("TODO", null, "*.java", "content")   // 在 Java 文件中搜索并显示内容
grep("error|exception", null, null, "files", true)  // 忽略大小写搜索
grep("function\\s+\\w+", null, "*.ts", "content", false, 2)  // 显示上下文
```

---

### CronTool
定时任务管理工具。

**工具名称：** `cron_create`, `cron_list`, `cron_delete`, `cron_trigger`

**功能：**
- 创建一次性/周期性/cron 表达式的定时任务
- 任务触发时发送事件到 EventBus
- 支持手动触发

---

### WebSearchTool
网页搜索工具。

**工具名称：** `web_search`

**功能：**
- 通过 DuckDuckGo 搜索（无需 API 密钥）
- 支持域名过滤（allowedDomains / blockedDomains）
- 搜索结果包含标题、URL、摘要
- 搜索耗时统计

**参数：**
- `query`: 搜索关键词
- `limit`: 返回结果数量（默认 10，最大 50）
- `allowedDomains`: 只搜索这些域名的内容（逗号分隔）
- `blockedDomains`: 排除这些域名的结果（逗号分隔）

**输出示例：**
```
搜索关键词: Spring Boot 教程
结果数量: 10
搜索耗时: 1250ms

──────────────────────────────────────────────────

1. Spring Boot Quick Start Guide
   URL: https://spring.io/guides/gs/spring-boot
   摘要: Spring Boot makes it easy to create stand-alone...

2. Getting Started with Spring Boot
   URL: https://www.baeldung.com/spring-boot-start
   摘要: Learn how to create a simple Spring Boot application...

提示: 使用 web_fetch 工具可以获取网页的详细内容
```

**使用示例：**
```java
web_search("Spring Boot 教程")
web_search("Java 21 features", 5)  // 限制5个结果
web_search("React hooks", 10, "react.dev,github.com", null)  // 只搜索指定域名
```

---

### WebFetchTool
网页内容抓取工具。

**工具名称：** `web_fetch`

**功能：**
- 抓取 URL 并提取可读的网页内容
- HTML 智能转换为纯文本
- 重定向检测（自动跟踪最多5次重定向）
- 二进制文件检测
- 预批准域名标记
- 超时设置

**参数：**
- `url`: 要抓取的网页 URL
- `maxLength`: 最大获取内容长度（默认 50000 字符）
- `timeout`: 超时时间（默认 30000 毫秒）

**输出示例：**
```
URL: https://spring.io/guides/gs/spring-boot
状态码: 200
Content-Type: text/html; charset=utf-8
内容大小: 45.2 KB
耗时: 850ms
预批准域名: 是

──────────────────────────────────────────────────

Building an Application with Spring Boot
This guide provides a walkthrough of how to build
a simple Spring Boot application...
```

**重定向检测输出：**
```
检测到重定向:
- 原始 URL: https://example.com/old
- 最终 URL: https://example.com/new
- 状态码: 301

如需直接访问重定向后的 URL，请使用新 URL 重新调用。
```

---

### AskUserQuestionTool
用户提问工具。

**工具名称：** `ask_user`

**功能：**
- 向用户提问并等待回答
- 支持单选和多选
- 支持自定义输入
- 通过事件系统与前端交互

**参数：**
- `questions`: 问题列表（JSON 数组格式，最多4个问题）

**问题格式：**
```json
[
  {
    "header": "认证方式",
    "question": "您希望使用哪种认证方式？",
    "multiSelect": false,
    "options": [
      {"label": "JWT (推荐)", "description": "使用 JWT Token 进行认证"},
      {"label": "Session", "description": "使用传统 Session 认证"}
    ]
  }
]
```

**输出示例：**
```
用户回答:

1. 您希望使用哪种认证方式？
   回答: JWT (推荐)
   选中选项: JWT (推荐)
```

**使用场景：**
- 澄清需求
- 获取用户决策
- 收集用户偏好
- 选择实现方案

---

## 安全机制

### 路径安全限制
- 文件操作限制在 `~/.autiva` 和当前工作目录下
- 防止路径遍历攻击

### 命令执行限制
- 默认 60 秒超时
- 输出截断（最多 1000 行，每行最多 500 字符）
- 后台进程需明确指定

### 文件搜索限制
- 自动跳过 VCS 目录（.git, .svn 等）
- 自动跳过二进制文件
- 结果数量限制

## 工具使用场景

| 场景 | 推荐工具 |
|------|---------|
| 读取代码文件 | `read` (支持分页) |
| 生成新文件 | `write` |
| 修改配置文件 | `edit` |
| 运行编译/测试 | `exec` |
| 启动服务 | `exec(background=true)` + `process_*` |
| 定时检查 | `cron_*` |
| 搜索资料 | `web_search` |
| 获取网页内容 | `web_fetch` |
| 按文件名搜索 | `glob` |
| 按内容搜索 | `grep` |
| 向用户提问 | `ask_user` |
| 域名限定搜索 | `web_search(allowedDomains=...)` |
