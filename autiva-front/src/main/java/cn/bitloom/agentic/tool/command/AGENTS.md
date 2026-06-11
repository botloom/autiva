# Command Tool — 架构文档

> **v11 架构（2026-06 ProcessBuilder 重构）**：移除 pty4j 依赖，全平台统一 ProcessBuilder 执行路径。
> **v11 核心改进**：1) 移除 pty4j 库依赖（约 5MB），改用 JDK 原生 `ProcessBuilder`；2) 零外部依赖，纯 JDK 实现；3) 前后台统一 `ProcessBuilder + cmd.exe` 路径；4) `PtyHandle` 包装 `java.lang.Process` 而非 `pty4j.PtyProcess`。
> **v10 核心改进**：淘汰 PowerShell → cmd.exe，LLM 语法 100% 兼容，删除 shell/ 包 6 个文件 + CLIXML 处理。
> **双工具模型**（Command + Process）+ **智能后台化**（yield_ms）+ **per-call 参数**（workdir/env）+ **破坏性命令检测**。

---

## 1. 设计原则

| 原则 | 实现 | 灵感来源 |
|------|------|----------|
| 双工具模型 | Command（执行）+ Process（管理），替代 5 个碎片工具 | OpenClaw `exec` + `process` |
| 智能后台化 | `yield_ms` 参数：前台运行超时自动转后台 | OpenClaw `yieldMs` |
| per-call 覆盖 | `workdir` / `env` 每次调用可覆盖持久化状态 | OpenClaw `workdir` / `env` |
| 破坏性命令检测 | 正则匹配 `rm -rf`、`dd`、`mkfs`、`Remove-Item -Recurse -Force` 等危险命令 | Hermes Agent `_DESTRUCTIVE_PATTERNS` |
| PTY 伪终端（Windows） | PTY4J + cmd.exe，模拟真实终端，通过 stdin 写入命令 | v10 新增→v11 移除 |
| ProcessBuilder（Windows） | `ProcessBuilder` + `cmd.exe /v:on`，纯 JDK，stderr 合并到 stdout | v11 新增 |
| 环境变量通过 ProcessBuilder 传递 | 不在脚本中注入 `$env:` / `export`，避免特殊字符问题 | v6 保留 |
| 状态持久化 ≠ 进程持久化 | cwd/env 写入 `~/.autiva/shell-state.json` | v4 保留 |
| 输出零丢失 | `CountDownLatch` 确保读取线程完成后才返回输出 | v4 保留 |
| 跨平台抽象 | Windows=PTY+cmd.exe，Unix=ProcessBuilder+bash/sh | v4 保留→v10 简化 |
| 无重复代码 | Unix 路径保留共享类（EncodingHelper/AbstractPosixShell） | v9 保留 |
| 线程安全 | ShellSession 所有修改方法 synchronized，env 用 ConcurrentHashMap | v9 保留 |
| 输入输出限制 | 命令长度 ≤8000 字符，输出 ≤1M 字符 + 30000 行 | v9 保留 |

---

## 2. 目录结构

```
cn/bitloom/agentic/tool/command/
├── AGENTS.md                      (本文件)
├── CommandTools.java              Spring AI @Tool 入口（Command + Process 双工具，旧版，保留兼容）
├── CommandTool.java               AbstractTool<CommandTool.Input> 入口（Command 工具，新版）
├── ProcessTool.java               AbstractTool<ProcessTool.Input> 入口（Process 工具，新版）
├── CommandResult.java             执行结果 record
├── CommandExecutor.java           核心执行器 — 统一 PTY 路径
├── ProcessManager.java            后台进程管理（list/poll/log/write/kill/clear）
├── ShellSession.java              Shell 会话状态（cwd + env 持久化）
├── CommandSafety.java             破坏性命令检测
├── OutputSanitizer.java           ANSI / 控制字符清理 + 大小截断
├── EncodingHelper.java            UTF-8/GBK 编码回退（后台进程使用）
└── pty/                           PTY 子包
    ├── PtyTerminal.java           ProcessBuilder + cmd.exe 执行核心（全平台统一）
    └── PtyResult.java             PTY 执行结果 record
```

---

## 3. 关键类职责

### 3.1 `CommandTools`（双工具入口）

**Command 工具** — 执行命令：
- 前台模式：同步执行，返回完整输出 + 退出码
- 智能后台化（`yield_ms`）：先前台运行，超时自动转后台
- 立即后台（`background=true`）：立即返回 session_id
- per-call 参数：`workdir` / `env` 覆盖持久化状态
- 安全检测：自动检测破坏性命令并发出警告

**Process 工具** — 管理后台进程：
- `list` / `poll` / `log` / `write` / `kill` / `clear`

### 3.2 `CommandExecutor`

每次 `execute()` 调用 = 一个全新进程：

1. 从 `ShellSession` 获取 cwd / env（支持 per-call 覆盖）
2. `Shell.createProcessBuilder(command, cwd, env)` 直接创建 ProcessBuilder
3. 启动进程，启动 OutputReader 线程
4. `process.waitFor()` 等待完成（含超时）
5. `OutputReader.await()` — CountDownLatch 确保读取完成
6. 清理输出，更新 session

**v6 关键变化**：不再写临时脚本文件。命令直接通过 Shell 的 `-Command` / `-c` 参数传递。

### 3.3 `Shell` 接口（v9 简化）

| 方法 | 职责 |
|------|------|
| `name()` | Shell 名称（如 "PowerShell Core (pwsh)"） |
| `buildProcessCommand(command, cwd)` | 构建进程命令行（如 `pwsh -NoProfile -Command "Set-Location 'cwd'; command; Write-Output __PWD__..."`) |
| `createProcessBuilder(command, cwd, env)` | 创建完整 ProcessBuilder（env 通过 ProcessBuilder.environment() 传递） |
| `pwdPattern()` | PWD 标记正则（用于从输出提取新 cwd） |
| `resolveCwd(cwd)` | **v9 新增 default 方法**：验证 cwd 有效性，无效回退到 user.home |

**v9 新增类**：
- `EncodingHelper` — 共享编码工具（UTF-8/GBK 回退 + BOM 检测），消除 CommandExecutor/ProcessManager 中的重复
- `AbstractPosixShell` — POSIX Shell 基类，消除 BashShell/ShShell 雷同

### 3.4 `ShellSession`

- 状态文件：`~/.autiva/shell-state.json`
- `resolveWorkdir(perCallWorkdir)` / `mergedEnv(perCallEnv)` 三层合并
- `updateFromOutput(output)` 使用 `Shell.pwdPattern()` 提取新 cwd
- **v9 线程安全**：`updateFromOutput()`/`setCwd()`/`setEnv()` 全部 `synchronized`，`env` 使用 `ConcurrentHashMap`

### 3.5 `ProcessManager`

| 动作 | 方法 | 说明 |
|------|------|------|
| `list` | `list()` | 列出所有进程 |
| `poll` | `poll(id, waitMs)` | 增量拉取新输出 |
| `log` | `log(id, offset, limit)` | 读取完整输出，支持分页 |
| `write` | `write(id, data)` | 向 stdin 发送输入 |
| `kill` | `kill(id)` | 终止后台进程 |
| `clear` | `clear(id)` | 清除已完成的进程记录 |

### 3.6 `CommandSafety`

检测破坏性命令（`rm -rf`、`dd`、`mkfs`、`curl|sh`、`Remove-Item -Recurse -Force`、`Stop-Computer`、`iwr|iex` 等），发出警告但不阻止执行。
- **v9 新增**：11 个 PowerShell 特有危险命令模式（`Remove-Item -Recurse -Force`、`Stop-Computer`、`Restart-Computer`、`Set-ExecutionPolicy Unrestricted`、`Invoke-WebRequest|Invoke-Expression`、`iwr|iex`、`Format-Volume`、`Clear-Disk`、`Remove-Service`、`Set-ItemProperty -Name Path`）

### 3.7 `OutputSanitizer`

清理输出中的 ANSI 转义序列、CLIXML、`\r` 进度行、连续空行。截断到 30000 行。
- **v9 新增**：`MAX_OUTPUT_CHARS = 1_000_000`（约 1MB），超长输出截断并提示
- **v9 优化**：`truncate()` 改用 `indexOf` 计数替代 `split`，避免创建完整数组

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
|------|------|------|------|
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
| D | GBK vs UTF-8 编码 | 中文乱码或空输出 | UTF-8 InputStreamReader + PowerShell 编码设置 |
| E | Pty4J 原生库不稳定 | ConPTY 初始化失败 → 全部超时 | 纯 JDK ProcessBuilder |
| F | 文件重定向输出丢失 | v3 偶尔返回空输出 | 流式读取替代文件重定向 |
| G | IS_WINDOWS 散布 | 平台逻辑散布在多个类中 | Shell 接口封装所有平台差异 |
| H | 5 个碎片工具 | Bash/BashOutput/KillBash/SendInput/GetCwd 语义分散 | 双工具模型：Command + Process |
| I | 短命令也必须选模式 | 前台/后台必须提前决定 | yield_ms 智能后台化 |
| J | 无法 per-call 覆盖 | workdir/env 只能通过持久化状态修改 | per-call workdir/env 覆盖 |
| K | 无安全检测 | `rm -rf /` 直接执行 | CommandSafety 破坏性命令检测 |
| L | 后台输出只能增量 | poll 消费后无法回看 | log 动作支持分页读取 |
| M | 无法判断进程等待输入 | 交互式命令卡住无提示 | waiting_for_input 检测 |
| N | yield_ms 默认启用导致所有命令失败 | 所有命令走 executeWithYield 返回 exit_code:1 | yield_ms 改为 opt-in |
| O | 后台进程 stdin 被提前关闭 | 后台化后 sendInput 失败 | 延迟关闭 stdin |
| P | 环境变量名含特殊字符导致脚本解析失败 | `$env:ProgramFiles(x86)` 括号被解析为方法调用 | **v6 根治**：环境变量通过 ProcessBuilder.environment() 传递，不再注入脚本 |
| Q | PowerShell 错误输出 GBK 编码 | 脚本解析失败时错误信息用 GBK → 乱码 | **v6 根治**：`-Command` 模式先设编码再执行命令，不依赖脚本文件 |
| R | OutputSanitizer 吞噬所有 Windows 输出 | `stripProgressCarriageReturns` 在 `\r\n` 规范化之前执行 | 先 `s.replace("\r\n", "\n")` 再 `stripProgressCarriageReturns` |
| S | **临时脚本文件引入大量问题** | BOM、编码、特殊字符、文件清理、权限 | **v6 根治**：去掉临时脚本文件，直接 `-Command` / `-c` 运行 |
| T | **缺少 `-parameters` 编译参数** | `command` 参数为 `null`，PowerShell 执行 `null` 报错 | 在 `pom.xml` 中添加 `-parameters` 编译参数 |
| U | **PowerShell `-Command` 解析用户命令中的特殊字符** | `python -c "open('file','w',encoding='utf-8')"` 中引号、括号、逗号被 PowerShell 解析为自身语法 | **v7 根治**：改用 `-EncodedCommand` + Base64 UTF-16LE，完全绕过 PowerShell 解析器 |
| V | **PowerShell 解析失败时错误信息 GBK 编码** | `-Command` 模式下编码设置在用户命令之前，但解析失败时编码设置还没执行 → GBK 乱码 | **v7 根治**：`-EncodedCommand` 模式下编码设置是编码命令的一部分，一定先执行 |
| W | **CLIXML 正则 `#>` 结尾标记不存在** | PowerShell CLIXML 输出可能不以 `#>` 结尾，导致 CLIXML 块不被剥离，GBK 乱码残留 | 修复正则为 `(?:#>\|</Objs>)` 匹配两种结尾 |
| X | **OutputReader 只用 UTF-8 解码** | PowerShell 错误输出用 GBK 编码，UTF-8 解码产生 `\ufffd` 替换字符 → 乱码 | OutputReader 改为字节缓冲 + GBK 回退：先 UTF-8，如有替换字符则尝试 GBK |
| Y | **PowerShell 5.1 管道模式默认 CLIXML 序列化** | 所有命令返回 `(no output)`：PS 5.1 在 `-EncodedCommand` 模式下强制触发 CLIXML，`-OutputFormat Text` 对 `-EncodedCommand` 无效 | **v8 根治**：重写 `extractClixmlContent()` 提取 CLIXML 头和 `<Objs>` 之间的纯文本行 |
| Z | **CLIXML 中有效输出是纯文本行而非 `<S>` 节点** | v8 首版 `extractClixmlContent()` 只提取 `<S>` 节点，但 PS 5.1 的 CLIXML 格式是纯文本夹在 `#< CLIXML\n` 和 `<Objs` 之间，没有 `<S>` 节点 | **v8 根治**：改为提取 `#< CLIXML` 换行后到 `<Objs` 开始前的纯文本行，同时保留 `<S>` 节点提取（用于错误流） |
| AA | **后台进程用 BufferedReader(UTF-8) 读行，不经过 OutputSanitizer** | `ProcessManager.BackgroundProcess.readLoop()` 用 `BufferedReader` + `InputStreamReader(UTF-8)` 按行读取，1) 不经过 `OutputSanitizer.clean()` 导致 CLIXML 原始输出暴露；2) UTF-8 解码 GBK 内容丢失行；3) poll/log 返回原始 CLIXML 文本 | **v8 根治**：改用 `ByteArrayOutputStream` 字节缓冲 + `decodeBest()` + `OutputSanitizer.clean()`，与前台 `OutputReader` 完全一致 |
| AB | **LLM 使用 `&&` 语法在 PS 5.1 中报错** | PS 5.1 不支持 `&&` 作为语句分隔符，LLM 经常用 `cd ... && command` | **v8 根治**：在 Command 工具 description 中添加提示"Windows PowerShell 5.1 不支持 && 语法！请用分号 ; 连接多条命令" |
| AC | **`decodeBest()` 重复** | CommandExecutor 和 ProcessManager 各自维护一份完全相同的解码逻辑 | **v9 根治**：提取 `EncodingHelper` 共享类 |
| AD | **`resolveCwd()` 三个 Shell 实现完全相同** | PowerShellShell/BashShell/ShShell 各自重复目录验证逻辑 | **v9 根治**：提取到 `Shell` 接口 `default` 方法 |
| AE | **BashShell/ShShell 高度雷同** | 两个类除了 `--noprofile --norc` 参数外完全相同 | **v9 根治**：提取 `AbstractPosixShell` 基类 |
| AF | **后台进程 `decodeAndEnqueueNewOutput` O(n²)** | 每次有新字节到达时都从头解码+清洗所有字节，长时间运行进程性能严重退化 | **v9 根治**：增量解码新字节，非 CLIXML 场景 O(1)，CLIXML 场景仍全量但更优 |
| AG | **`ShellSession` 线程安全不完整** | `state` 是 volatile 但 `updateFromOutput()`/`setCwd()`/`setEnv()` 不是 synchronized，并发命令可能损坏状态 | **v9 根治**：所有修改方法加 synchronized，env 改用 ConcurrentHashMap |
| AH | **`extractClixmlContent` Strategy 3 误匹配** | `clixmlHeaderEnd == -1` 时 `indexOf("#>", -1)` 搜索整个字符串，可能误匹配非 CLIXML 内容 | **v9 根治**：加 `if (clixmlHeaderEnd >= 0)` 前置条件 |
| AI | **缺少 PowerShell 危险命令检测** | CommandSafety 只有 Unix/bash 模式，Windows 下 `Remove-Item -Recurse -Force` 等不检测 | **v9 根治**：添加 11 个 PowerShell 特有模式 |
| AJ | **`isLikelyWaitingForInput` 依赖反射类名** | 用 `processStdin.getClass().getSimpleName().contains("OutputStream")` 判断 stdin 是否可写，依赖 JVM 内部实现 | **v9 根治**：改用 `stdinClosed` 标志 |
| AK | **`executeWithYield` 进程泄漏** | `processManager.register()` 抛异常时已启动的进程不会被清理 | **v9 根治**：register 失败时 `destroyForcibly()` |
| AL | **无命令长度限制** | Shell 命令行有长度限制（Windows ~8191），超长命令可能静默截断 | **v9 根治**：添加 `MAX_COMMAND_LENGTH=8000` 校验 |
| AM | **无输出字节大小限制** | 只限制行数（30000 行），单行可能极长 | **v9 根治**：添加 `MAX_OUTPUT_CHARS=1_000_000` |
| AN | **workdir 无效时静默降级** | 回退到 home 目录但不通知调用方，LLM 不知道实际执行目录与请求不同 | **v9 根治**：workdir 无效时在 rawOutput 中添加回退通知 |
| AO | **PS 5.1 `Get-Content` 默认用系统编码读文件** | `type utf8file.py \| other_cmd` 时 UTF-8 文件被 GBK 解读 → 中文乱码（如 `鏌ユ壘鍙兘`），管道传递到下游进程后乱码扩散 | **v9 根治**：PS 5.1 命令前缀中添加 `$PSDefaultParameterValues['Get-Content:Encoding']='UTF8'` 等，强制文件读写默认 UTF-8；PS 7 (pwsh) 默认 UTF-8 无需此 workaround |
| AP | **LLM 使用 `\|\|` 语法在 PS 5.1 中报错** | PS 5.1 不支持 `\|\|` 作为语句分隔符，LLM 用 `command \|\| fallback` 报错 | **v9 根治**：在 Command 工具 description 中扩展提示，涵盖 `\|\|` 和 cmd.exe 语法（`2>nul` 等） |
| AQ | **LLM 混用 cmd.exe 和 PowerShell 语法** | `dir /s 2>nul \|\| echo "NOT FOUND"` 在 PS 5.1 中全部报错 | **v9 根治**：在 Command 工具 description 中明确提示不要使用 cmd.exe 语法 |
| AR | **提示文本写死 PS 5.1，pwsh 用户被误限** | 系统检测到 pwsh 时仍提示"不支持 &&"，实际上 pwsh 支持；`$PSDefaultParameterValues` 对 pwsh 多余 | **v9 根治**：1) PowerShellShell.buildPrefix() 根据 `isCore` 区分，PS 7 跳过 5.1 workaround；2) 首次执行命令时动态注入 Shell 环境信息（名称+版本提示）；3) description 改为同时涵盖 PS 5.1 和 PS 7 |
| AS | **PowerShell 从根本上不适合 LLM 生成命令** | PS 语法体系与所有 LLM 训练数据中的 Shell 都不兼容：`&&`/`||`/heredoc/`&` in URL/`2>nul`/quote escaping 全部不兼容；v9 的版本适配是打不完的补丁 | **v10 根治**：全平台统一 PTY4J + 本地 Shell（Windows=cmd.exe，Unix=bash）。PTY 伪终端提供真实终端环境，所有常见语法原生支持，零版本适配需求 |
| AT | **Shell 抽象层污染执行路径** | `Shell`/`ShellDetector`/`PowerShellShell`/`AbstractPosixShell` 整个抽象层只服务于 ProcessBuilder 路径；`updateFromOutput()` 依赖 `shell.pwdPattern()`；`CommandExecutor` 有 Windows/Unix 两条执行路径 | **v10 简化**：1) `ShellSession` 去掉 `Shell` 引用；2) `CommandExecutor` 只有一条 PTY 路径；3) `CommandTools.Builder` 不再需要 Shell 参数 |
| AU | **CLIXML 死代码残留** | OutputSanitizer 的 `extractClixmlContent()`（~80 行）+ ProcessManager 的 `clixmlDetected` 分支（~30 行）在 PTY 后完全无用到 | **v10 清理**：删除 shell/ 包 6 个文件 + OutputSanitizer CLIXML 提取 + ProcessManager CLIXML 检测分支 |
| AV | **后台/yield 混用 ProcessBuilder** | `startBackgroundProcess` 和 `executeWithYield` 走 ProcessBuilder，与前台 PTY 路径不一致 | **v10 统一**：`PtyTerminal.start()` 返回 PtyHandle，前后台统一 PTY 路径；`ProcessManager` 管理 PtyProcess（非 java.lang.Process）+ marker 检测退出码 |
| AW | **pty4j 原生库依赖过重** | pty4j 约 5MB，依赖本地 so/dll，ConPTY 初始化可能失败 | **v11 根治**：移除 pty4j，改用 JDK 原生 `ProcessBuilder.start()`，前后台统一 ProcessBuilder 路径 |

---

## 6. 关键实现细节

### v11 核心：ProcessBuilder 替代 pty4j

**为什么移除 pty4j？**

pty4j 提供 PTY（伪终端）功能，但：
- 依赖本地 so/dll 库（约 5MB），增加部署复杂度
- ConPTY 初始化可能因 Windows 版本/权限问题失败
- 对于非交互式命令执行，ProcessBuilder 功能完全足够
- JDK 原生 API，零额外依赖

**v11 命令包装（`PtyTerminal.wrapCmd`）**：

```
chcp 65001 > nul 2>&1 &          ← 切换到 UTF-8 codepage
cd /d "C:\Users\TRS" &           ← 导航到工作目录
<用户原始命令> &                   ← LLM 直接生成的命令，零转义
echo __PTY_MARK_<nano>^|^|!ERRORLEVEL!^|^|!CD! &  ← 捕获退出码和当前目录（^|^| 转义为 ||）
exit                              ← 关闭 Shell（触发 stdout EOF）
```

**关键设计决策**：
- 使用 `cmd.exe /v:on` 的**延迟展开** (`!ERRORLEVEL!`)——`%ERRORLEVEL%` 在解析时求值，会得到旧值
- `redirectErrorStream(true)` — stderr 合并到 stdout，统一处理
- `stdin.close()` — 写完命令后关闭 stdin，触发 cmd.exe 执行并退出
- `ProcessBuilder.directory()` — 设置工作目录
- `ProcessBuilder.environment().putAll()` — 设置环境变量

### v11 命令执行流程（Windows）

```java
PtyTerminal.start("dir", "C:\\Users", env):
  // 1. 创建 ProcessBuilder
  new ProcessBuilder("cmd.exe", "/v:on")
      .directory("C:\\Users")
      .redirectErrorStream(true)
      .start()
  // 2. 通过 stdin 写入包装后的命令：
  //    chcp 65001 > nul 2>&1 & cd /d "C:\Users" & dir & echo __PTY_MARK_<id>^|^|!ERRORLEVEL!^|^|!CD! & exit
  // 3. 关闭 stdin 触发执行
  // 4. 后台线程从 stdout 读取所有字节直到 EOF
  // 5. 解析 marker 行提取 exitCode 和 cwd
  // 6. return PtyHandle(process, markerId, "C:\\Users", startTime)
```

### v11 持久化会话（Windows）

```java
PtySession.create("C:\\Users", env):
  // 1. 创建持久化 ProcessBuilder 进程
  new ProcessBuilder("cmd.exe", "/v:on")
      .directory("C:\\Users")
      .redirectErrorStream(true)
      .start()
  // 2. PtySession.execute("dir", 120000):
  //    stdin.write("chcp 65001 > nul 2>&1 & dir & echo __PTY_MARK_<id>^|^|!ERRORLEVEL!^|^|!CD!\n")
  //    读取输出直到 marker，解析结果
  // 3. 超时/崩溃时：process.destroyForcibly() + 重建 ProcessBuilder

### v8→v9 核心改进：代码质量 + 性能 + 健壮性（保留在 Unix 路径）

**1. 消除重复代码**：
- `EncodingHelper`：共享 UTF-8/GBK 回退解码 + BOM 检测，CommandExecutor.OutputReader 和 ProcessManager.BackgroundProcess 统一使用
- `Shell.resolveCwd()`：default 方法替代三个 Shell 实现中完全相同的目录验证逻辑
- `AbstractPosixShell`：基类封装 bash/sh 共享的命令构建、ProcessBuilder 创建、引号转义逻辑
- `CommandTools.buildRawOutput()`：辅助方法替代多处手动 StringBuilder 拼接

**2. 后台进程增量解码优化**：
```
v8（O(n²)）：
1. 每次有新字节 → 从头解码所有字节 → 全量清洗 → 与上次清洗结果做 substring 差量

v9（增量）：
1. 只解码新到达的字节（System.arraycopy）
2. 非 CLIXML：对新字节做轻量清洗，O(1)
3. CLIXML：仍需全量重洗（因为 CLIXML 提取需要完整上下文），但用 lastCleanedLength 做差量
4. getFullOutput() 始终从原始字节重新解码+清洗，保证一致性
```

**3. ShellSession 线程安全**：
```
v8：state 是 volatile，但 updateFromOutput()/setCwd()/setEnv() 无同步
v9：三个方法全部 synchronized，env 改用 ConcurrentHashMap
```

**4. 输入输出限制**：
- 命令长度：`MAX_COMMAND_LENGTH = 8000`（Windows cmd line ~8191 字符限制）
- 输出大小：`MAX_OUTPUT_CHARS = 1_000_000`（约 1MB），在 `OutputSanitizer.clean()` 末尾截断
- 输出行数：`MAX_OUTPUT_LINES = 30_000`（保留）

**5. workdir 无效通知**：
- `CommandTools.command()` 检查 workdir 是否被回退
- 回退时在 rawOutput 前添加 "⚠ 工作目录 'xxx' 不存在，已回退到 'yyy'"

### v6 核心改进：直接执行，不写脚本文件

**之前（v5）**：
```
1. Shell.wrapScript() 生成脚本内容
2. 写入临时 .ps1 / .sh 文件（需要 BOM、编码处理）
3. Shell.buildProcessCommand(scriptPath) 构建命令行
4. pwsh -File temp_script.ps1
```

**之后（v6）**：
```
1. Shell.buildProcessCommand(command, cwd) 直接构建命令行
2. pwsh -Command "Set-Location 'cwd'; command; Write-Output __PWD__..."
3. 环境变量通过 ProcessBuilder.environment() 传递
```

**消除的问题**：
- 不再需要临时文件（BOM、编码、清理、权限）
- 不再需要 `wrapScript()` / `scriptExtension()`
- 环境变量特殊字符问题（`ProgramFiles(x86)`）彻底消除
- PowerShell `-Command` 模式下编码设置在命令执行前生效

### v6→v7 核心改进：`-Command` → `-EncodedCommand`

**v6（`-Command` 模式）**：
```
1. 构建完整命令字符串（编码设置 + Set-Location + 用户命令 + PWD 标记）
2. pwsh -Command "完整命令字符串"
3. 问题：PowerShell 解析器会解析用户命令中的引号、括号、逗号等特殊字符
4. 问题：解析失败时编码设置还没执行 → 错误信息 GBK 编码 → 乱码
```

**v7（`-EncodedCommand` 模式）**：
```
1. 构建完整命令字符串（编码设置 + Set-Location + 用户命令 + PWD 标记）
2. 将命令字符串转为 UTF-16LE 字节，再 Base64 编码
3. pwsh -EncodedCommand <base64>
4. 优势：PowerShell 不解析编码后的命令内容，特殊字符不会被误解析
5. 优势：编码设置是编码命令的一部分，一定先执行，错误信息也是 UTF-8
```

### v7→v8 核心改进：CLIXML 纯文本提取

**v7 问题**：
```
1. PS 5.1 的 -EncodedCommand 强制触发 CLIXML 序列化
2. -OutputFormat Text 对 -EncodedCommand 无效（诊断测试铁证）
3. CLIXML 格式：#< CLIXML\n<纯文本输出>\n<Objs ...>...</Objs>
4. 有效输出是纯文本行（非 <S> 节点），夹在 CLIXML 头和 <Objs> 之间
5. v8 首版 extractClixmlContent() 只提取 <S> 节点 → 找不到 → 返回空
```

**v8 修复**：
```
1. extractClixmlContent() 改为三策略提取：
   - 策略1：提取 #< CLIXML 换行后到 <Objs 开始前的纯文本行（主输出）
   - 策略2：提取 <S> 节点文本（错误/警告流）
   - 策略3：提取 </Objs> 后的残留内容
2. 添加 -OutputFormat Text 参数（虽然对 -EncodedCommand 无效，但对 -Command 有效）
3. 添加 UTF-8 BOM (EF BB BF) 检测和跳过
4. 增强诊断日志：after clean 也输出 first 200 chars
```

**诊断测试结果**：
```
pwsh: NOT FOUND（系统只有 powershell 5.1）
powershell -Command echo hello → after clean: [hello] ✅
powershell -EncodedCommand echo hello → CLIXML → after clean: [hello] ✅
cmd /c echo hello → after clean: [hello] ✅
py -c "print('hello')" → after clean: [hello from py] ✅
python -c "print('hello')" → 无输出（Windows Store 重定向器，非真正 Python）
```

### v10 命令执行流程（Windows）

```java
PtyTerminal.execute("dir", "C:\\Users", env, 120000):
  // 1. 启动 PTY
  PtyProcess.exec(["cmd.exe", "/v:on"], env, "C:\\Users")
  // 2. 通过 stdin 写入包装后的命令：
  //    chcp 65001 > nul 2>&1 & cd /d "C:\Users" & dir & echo __PTY_MARK_<id>^|!ERRORLEVEL!^|!CD! & exit
  // 3. 后台线程从 stdout 读取所有字节直到 EOF
  // 4. 解析 marker 行提取 exitCode 和 cwd
  // 5. return PtyResult("clean output", 0, "C:\\Users")
```

### Bash 命令构建（Unix，不变）

```java
buildProcessCommand("ls -la", "/home/user"):
  bash --noprofile --norc -c
    "cd '/home/user' ; ls -la ; echo \"__PWD__$(pwd)\""
```

### PWD 跟踪

**Windows PTY**: cwd 从 marker 行 `!CD!` 直接提取，写入 `ShellSession`。

**Unix**: `__PWD__` 标记行保留在输出中供 `ShellSession.updateFromOutput()` 提取 cwd，返回给 LLM 前通过 `stripPwdMarker()` 剥离。

### Bug T 详细分析：`-parameters` 编译参数缺失

**Spring AI @Tool 参数映射机制**：

Spring AI 的 `MethodToolCallback` 使用 Java 反射 API `parameter.getName()` 来：
1. `JsonSchemaGenerator.generateForMethodInput()` — 生成 JSON Schema 属性名
2. `MethodToolCallback.buildMethodArguments()` — 从 LLM 返回的 JSON arguments Map 中查找参数值

**没有 `-parameters` 时**：
- `parameter.getName()` 返回 `arg0`, `arg1` 等合成名称
- JSON Schema 属性名变为 `arg0`, `arg1`（但描述仍是 "要执行的命令" 等）
- LLM 看到描述后"自作主张"用 `command`, `description` 作为参数名
- `buildMethodArguments` 查找 `arg0` → Map 中没有 → 返回 `null`
- PowerShell 收到 `null` → `Set-Location '...'; null; Write-Output...` → 报错

**修复**：在 `pom.xml` 的 `maven-compiler-plugin` 中添加 `<parameters>true</parameters>` 或 `<arg>-parameters</arg>`，
使 `parameter.getName()` 返回实际参数名，JSON Schema 和参数查找一致。

**注意**：此问题影响所有使用 `@Tool` + `@ToolParam` 注解的方法参数映射，不仅仅是 CommandTools。
使用 `FunctionToolCallback` + record + `inputType` 的工具（如 TaskTool、SkillManager）不受影响，
因为 record 字段名不依赖 `-parameters`。

---

## 7. 快速烟测 checklist（v11）

1. `Command("echo hello", "测试1")` → 返回 "hello"
2. `Command("cd C:\\Windows && dir", "测试2")` → 路径正确，`&&` 语法正常工作
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
