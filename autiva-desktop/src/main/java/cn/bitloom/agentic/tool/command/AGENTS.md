# Command Tool — 架构文档

> **v12 架构（2026-06 无状态 ProcessBuilder 重构）**：删除 pty 包，改为无状态 ProcessBuilder 执行，引入分平台 ShellExecutor + CommandValidator。
> **v12 核心改进**：1) 删除 pty 包（PtyTerminal/PtySession/PtyResult），不再维护持久化 Shell 进程；2) 无状态 ProcessBuilder 执行（cmd.exe /v:on /c 或 bash -c）；3) 引入 ShellExecutor 接口 + WindowsShellExecutor / UnixShellExecutor 分平台实现；4) 引入 CommandValidator 接口 + WindowsCommandValidator / UnixCommandValidator 分平台验证；5) ShellSession 增加 cwd 持久化；6) ProcessManager 改用 java.lang.Process 替代 PtyHandle。

---

## 1. 设计原则

| 原则 | 实现 | 灵感来源 |
|------|------|----------|
| 无状态执行 | 每次命令创建新 ProcessBuilder 进程，不维护持久化 Shell | AgentScope ShellCommandTool |
| 分平台架构 | ShellExecutor 接口 + WindowsShellExecutor / UnixShellExecutor | SpringAI JManus ShellExecutorFactory |
| 分平台验证 | CommandValidator 接口 + WindowsCommandValidator / UnixCommandValidator | AgentScope CommandValidator |
| 双工具模型 | Command（执行）+ Process（管理） | OpenClaw `exec` + `process` |
| 智能后台化 | `yield_ms` 参数：前台运行超时自动转后台 | OpenClaw `yieldMs` |
| per-call 覆盖 | `workdir` / `env` 每次调用可覆盖持久化状态 | OpenClaw `workdir` / `env` |
| marker 协议 | `__CMD_MARK_<nano>||<exitCode>||<cwd>` 捕获退出码和 cwd | Trae Agent sentinel 协议 |
| 环境变量通过 ProcessBuilder 传递 | 不在脚本中注入 `$env:` / `export` | v6 保留 |
| 状态持久化 | cwd/env 写入 `~/.autiva/shell-state.json` | v4 保留→v12 增加 cwd |
| 输出零丢失 | `CountDownLatch` 确保读取线程完成后才返回输出 | v4 保留 |
| 线程安全 | ShellSession 所有修改方法 synchronized，env 用 ConcurrentHashMap | v9 保留 |
| 输入输出限制 | 命令长度 ≤8000 字符，输出 ≤1M 字符 + 30000 行 | v9 保留 |

---

## 2. 目录结构

```
cn/bitloom/agentic/tool/command/
├── AGENTS.md                      (本文件)
├── CommandTool.java               AbstractTool<CommandTool.Input> 入口（Command 工具）
├── ProcessTool.java               AbstractTool<ProcessTool.Input> 入口（Process 工具）
├── CommandResult.java             执行结果 record
├── CommandExecutor.java           核心执行器 — 无状态 ProcessBuilder
├── ProcessManager.java            后台进程管理（list/poll/log/write/kill/clear）
├── ShellSession.java              Shell 会话状态（cwd + env 持久化）
├── CommandSafety.java             破坏性命令检测（委托给 CommandValidator）
├── OutputSanitizer.java           ANSI / 控制字符清理 + 大小截断
├── EncodingHelper.java            UTF-8/GBK 编码回退
└── shell/                         分平台 Shell 抽象
    ├── ShellExecutor.java         Shell 执行器接口
    ├── WindowsShellExecutor.java  Windows cmd.exe 执行器
    ├── UnixShellExecutor.java     Unix bash 执行器
    ├── CommandValidator.java      命令验证器接口
    ├── WindowsCommandValidator.java Windows 命令验证器
    └── UnixCommandValidator.java  Unix 命令验证器
```

---

## 3. 关键类职责

### 3.1 ShellExecutor 接口

| 方法 | 职责 |
|------|------|
| `createProcessBuilder(wrappedCommand, workdir, env)` | 创建配置好的 ProcessBuilder |
| `wrapCommand(command, workdir)` | 包装用户命令：追加 cwd 设置、编码切换、marker |
| `parseOutput(raw, markerId, fallbackCwd, timedOut)` | 解析 marker 行，提取 exitCode 和 cwd |
| `filterEnv(env)` | 过滤环境变量（如 Windows 移除 WindowsApps） |
| `platformName()` | 平台名称（如 "Windows (cmd.exe)"） |
| `isWindows()` | 是否 Windows 平台 |
| `create()` | 工厂方法：根据平台自动选择实现 |

### 3.2 WindowsShellExecutor

- `createProcessBuilder()` → `cmd.exe /v:on /c "wrappedCommand"`
- `wrapCommand()` → `chcp 65001 > nul 2>&1 & cd /d "workdir" & command & echo MARKER^|^|!ERRORLEVEL!^|^|!CD!`
- `filterEnv()` → 移除 PATH 中的 `WindowsApps` 条目
- `parseOutput()` → 从 marker 行 `||` 分割提取 exitCode 和 cwd

### 3.3 UnixShellExecutor

- `createProcessBuilder()` → `bash -c "wrappedCommand"`
- `wrapCommand()` → `cd 'workdir' ; command ; echo 'MARKER||$?||$(pwd)'`
- `parseOutput()` → 从 marker 行 `||` 分割提取 exitCode 和 cwd

### 3.4 CommandValidator 接口

| 方法 | 职责 |
|------|------|
| `validate(command)` | 验证命令安全性，返回 ValidationResult |
| `create()` | 工厂方法：根据平台自动选择实现 |

### 3.5 CommandExecutor

每次 `execute()` 调用 = 一个全新 ProcessBuilder 进程：

1. 从 `ShellSession` 获取 cwd / env（支持 per-call 覆盖）
2. `shellExecutor.wrapCommand()` 包装命令
3. `shellExecutor.createProcessBuilder()` 创建 ProcessBuilder
4. 启动进程，启动异步读取线程
5. `CountDownLatch` 等待读取完成（含超时）
6. `shellExecutor.parseOutput()` 解析 marker 行
7. 更新 `ShellSession` 的 cwd
8. `OutputSanitizer` 清理输出

### 3.6 ShellSession

- 状态文件：`~/.autiva/shell-state.json`（包含 cwd + env）
- `resolveWorkdir(perCallWorkdir)` — per-call 优先，否则使用持久化 cwd
- `updateCwd(newCwd)` — 从 marker 行提取新 cwd 并持久化
- `mergedEnv(perCallEnv)` — 三层合并（系统 env + 持久化 env + per-call env）
- **线程安全**：所有修改方法 `synchronized`，env 使用 `ConcurrentHashMap`

### 3.7 ProcessManager

| 动作 | 方法 | 说明 |
|------|------|------|
| `list` | `list()` | 列出所有进程 |
| `poll` | `poll(id, waitMs)` | 增量拉取新输出 |
| `log` | `log(id, offset, limit)` | 读取完整输出，支持分页 |
| `write` | `write(id, data)` | 向 stdin 发送输入 |
| `kill` | `kill(id)` | 终止后台进程 |
| `clear` | `clear(id)` | 清除已完成的进程记录 |

**v12 变化**：`BackgroundProcess` 持有 `Process`（java.lang.Process）而非 `PtyHandle`。marker 检测通过 `ShellExecutor` 的解析方法实现。

### 3.8 CommandSafety

委托给平台对应的 `CommandValidator`。保留原有 `SafetyCheck` API 不变（向后兼容）。

### 3.9 OutputSanitizer

清理输出中的 ANSI 转义序列、`\r` 进度行、连续空行。截断到 30000 行 / 1M 字符。

---

## 4. 工具 API（Spring AI @Tool）

### `Command(command, description, timeout?, workdir?, env?, yield_ms?, background?)`

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| command | String | 必填 | 要执行的命令，最大 8000 字符 |
| description | String | 必填 | 5-10 字描述命令作用 |
| timeout | Long | 120000 | 超时毫秒，最大 600000 |
| workdir | String | 持久化 cwd | 工作目录覆盖 |
| env | Map | 持久化 env | 环境变量覆盖 |
| yield_ms | Long | null（纯前台） | 前台运行超过此毫秒数自动转后台 |
| background | Boolean | false | true 则立即转为后台执行 |

### `Process(action, session_id?, data?, offset?, limit?)`

| 参数 | 类型 | 必填 | 说明 |
|------|------|--------|------|
| action | String | 是 | list / poll / log / write / kill / clear |
| session_id | String | 除 list 外 | 后台进程 ID |
| data | String | write 时 | 要发送的数据 |
| offset | Integer | log 时 | 偏移行号，默认 0 |
| limit | Integer | log 时 | 返回行数，默认 200 |

---

## 5. 历史教训

| # | 根因 | 表现 | 修复 |
|---|------|------|------|
| A | 子进程 stdout 句柄被劫持 | `python -c "print('ok')` 返回空 | 流式读取 + `redirectErrorStream(true)` |
| B | daemon reader thread 竞态 | 所有命令返回 `(no output)` | CountDownLatch 同步 |
| C | Windows 管道缓冲区死锁 | 大输出命令卡死 | 流式读取 8KB buffer |
| D | GBK vs UTF-8 编码 | 中文乱码或空输出 | UTF-8 InputStreamReader + EncodingHelper 回退 |
| E | Pty4J 原生库不稳定 | ConPTY 初始化失败 → 全部超时 | v11 改用 ProcessBuilder |
| F | 持久化 Shell 进程复杂度高 | PtySession 崩溃恢复/drain prompt/重启 | v12 改为无状态执行 |
| G | IS_WINDOWS 散布 | 平台逻辑散布在多个类中 | ShellExecutor 接口封装所有平台差异 |
| H | CommandSafety 不可扩展 | 硬编码正则列表，无法按平台区分 | CommandValidator 接口 + 分平台实现 |

---

## 6. 关键实现细节

### v12 核心：无状态 ProcessBuilder

**为什么删除 pty 包？**

- PtySession 维护一个长期 Shell 进程，增加复杂度（重启、崩溃恢复、drain prompt 等）
- 无状态执行更简单可靠：每次命令创建新进程，执行完毕自动退出
- cwd/env 通过 ShellSession 持久化，不需要 Shell 进程来维护
- AgentScope 的 ShellCommandTool 也采用无状态模式

**v12 命令执行流程（Windows）**：

```java
CommandExecutor.execute("dir", 120000, null, null):
  // 1. 从 ShellSession 获取 cwd
  String cwd = shellSession.resolveWorkdir(null); // → "C:\Users\TRS"
  // 2. 包装命令
  String wrapped = windowsShellExecutor.wrapCommand("dir", "C:\\Users\\TRS");
  // → "chcp 65001 > nul 2>&1 & cd /d "C:\Users\TRS" & dir & echo __CMD_MARK_123^|^|!ERRORLEVEL!^|^|!CD!"
  // 3. 创建 ProcessBuilder
  ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/v:on", "/c", wrapped);
  pb.directory("C:\\Users\\TRS");
  pb.redirectErrorStream(true);
  // 4. 启动进程，异步读取 stdout
  // 5. CountDownLatch 等待读取完成（含超时）
  // 6. 解析 marker 行提取 exitCode 和 cwd
  // 7. 更新 shellSession 的 cwd
  // 8. OutputSanitizer 清理输出
```

**v12 命令执行流程（Unix）**：

```java
CommandExecutor.execute("ls -la", 120000, null, null):
  // 1. 从 ShellSession 获取 cwd
  String cwd = shellSession.resolveWorkdir(null); // → "/home/user"
  // 2. 包装命令
  String wrapped = unixShellExecutor.wrapCommand("ls -la", "/home/user");
  // → "cd '/home/user' ; ls -la ; echo '__CMD_MARK_123||$?||$(pwd)'"
  // 3. 创建 ProcessBuilder
  ProcessBuilder pb = new ProcessBuilder("bash", "-c", wrapped);
  pb.directory("/home/user");
  pb.redirectErrorStream(true);
  // 4-8 同 Windows
```

### v12 cwd 持久化

```
ShellSession 状态文件 (~/.autiva/shell-state.json):
{
  "cwd": "C:\\Users\\TRS\\project",
  "env": { "JAVA_HOME": "C:\\jdk-21" }
}

每次命令执行时：
1. resolveWorkdir(perCallWorkdir) → 返回 cwd（支持 per-call 覆盖）
2. wrapCommand() 在命令前追加 cd /d 或 cd
3. parseOutput() 从 marker 行提取新 cwd
4. updateCwd(newCwd) 持久化新 cwd
```

---

## 7. 快速烟测 checklist（v12）

1. `Command("echo hello", "测试1")` → 返回 "hello"
2. `Command("cd C:\\Windows && dir", "测试2")` → 路径正确，cwd 持久化
3. `Command("py -c \"print('中文测试')\"", "测试3")` → 中文不乱码
4. `Command("ping -n 30 127.0.0.1", "长任务", null, null, null, 3000, null)` → 3 秒后自动转后台
5. `Process("poll", session_id)` → 有进度输出
6. `Process("log", session_id, null, 0, 10)` → 返回前 10 行输出
7. `Process("list")` → 列出所有后台进程
8. `Process("kill", session_id)` → 终止成功
9. `Process("write", session_id, "input")` → 发送成功
10. `Command("type C:\\some\\utf8-file.py | findstr def", "管道测试")` → 管道中文正常
11. `Command("ls non_existent 2>nul || echo NOTFOUND", "错误处理测试")` → `||` 语法正常
12. `Command("超长命令...", "长度测试")` → 命令超过 8000 字符时返回错误
