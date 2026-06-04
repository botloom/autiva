package cn.bitloom.agentic.tool.command;

import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.tool.command.shell.Shell;
import cn.bitloom.agentic.tool.command.shell.ShellDetector;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.Map;

public class CommandTools {

    private final CommandExecutor executor;
    private final ProcessManager processManager;
    private final ShellSession session;

    public CommandTools(CommandExecutor executor,
                        ProcessManager processManager,
                        ShellSession session) {
        this.executor = executor;
        this.processManager = processManager;
        this.session = session;
    }

    @Tool(name = "Command", description = """
            在持久化状态的 Shell 中执行命令。灵感来源于 OpenClaw exec 工具。

            工作机制：
            - 每次调用 = 一个全新的进程（ProcessBuilder）
            - 当前工作目录（cwd）跨调用保持：写入 ~/.autiva/shell-state.json
            - 持久化的环境变量自动注入到新进程
            - 跨平台：Windows 使用 PowerShell，Unix 使用 Bash

            前台模式（默认）：
            - 同步执行，返回完整输出 + 退出码
            - 默认 timeout 120000ms（2 分钟），最大 600000ms（10 分钟）
            - 截断到 30000 行

            智能后台化（yield_ms > 0）：
            - 先以前台模式运行，如果超过 yield_ms 毫秒还没完成，自动转为后台
            - 返回 session_id，用 Process(action="poll") 拉取输出

            立即后台模式（background=true）：
            - 立即返回 session_id，命令在后台执行
            - 用 Process(action="poll") 拉取输出
            - 用 Process(action="write") 发送输入
            - 用 Process(action="kill") 终止

            Per-call 覆盖：
            - workdir：覆盖当前 cwd（不影响持久化状态）
            - env：覆盖环境变量（合并到持久化 env 之上）

            安全检测：
            - 自动检测破坏性命令（rm -rf、dd、mkfs 等）
            - 检测到危险命令时会发出警告但仍执行（由智能体自行判断）

            重要提示：
            - Windows PowerShell 5.1 不支持 && 语法！请用分号 ; 连接多条命令
            - 例如：cd C:\\path; python script.py（不要用 cd C:\\path && python script.py）
            - Windows 上请用 py 而非 python 启动 Python（python 可能指向 Windows Store 重定向器）
            """)
    public ToolResult command(
            @ToolParam(description = "要执行的命令") String command,
            @ToolParam(description = "5-10 字描述命令作用") String description,
            @ToolParam(description = "超时毫秒，默认 120000，最大 600000", required = false) Long timeout,
            @ToolParam(description = "工作目录覆盖，不传则使用持久化的 cwd", required = false) String workdir,
            @ToolParam(description = "环境变量覆盖（JSON 对象如 {\"KEY\":\"VALUE\"}），合并到持久化 env 之上", required = false) Map<String, String> env,
            @ToolParam(description = "前台运行超过此毫秒数自动转后台，不传则纯前台执行", required = false) Long yieldMs,
            @ToolParam(description = "true 则立即转为后台执行，忽略 yield_ms", required = false) Boolean background) {

        // Validate command length
        if (command != null && command.length() > CommandExecutor.MAX_COMMAND_LENGTH) {
            return ToolResult.error("命令过长 (" + command.length() + " 字符)，最大允许 "
                    + CommandExecutor.MAX_COMMAND_LENGTH + " 字符。请将命令拆分为多次执行。");
        }

        // Check if workdir is invalid (different from persisted cwd)
        String workdirNotice = null;
        if (workdir != null && !workdir.isEmpty()) {
            String resolvedCwd = session.resolveWorkdir(workdir);
            if (!resolvedCwd.equals(workdir)) {
                workdirNotice = "⚠ 工作目录 '" + workdir + "' 不存在，已回退到 '" + resolvedCwd + "'";
            }
        }

        CommandSafety.SafetyCheck safety = CommandSafety.check(command);
        ToolResult result;
        if (Boolean.TRUE.equals(background)) {
            result = startImmediateBackground(command, description, workdir, env);
        } else if (yieldMs != null && yieldMs > 0) {
            result = executeWithYield(command, description, timeout, workdir, env, yieldMs);
        } else {
            result = executeForeground(command, description, timeout, workdir, env);
        }

        if (safety.isDestructive() || safety.isWarning()) {
            String warningText = safety.isDestructive()
                    ? "⚠ 破坏性命令检测 [" + safety.rule() + "] - 请谨慎执行\n\n"
                    : "⚠ 注意 [" + safety.rule() + "] - 可能覆盖文件\n\n";
            String newRawOutput = warningText + (result.getRawOutput() != null ? result.getRawOutput() : "");
            return ToolResult.builder()
                    .status(ToolResult.Status.WARNING)
                    .message(warningText.trim() + " | " + result.getMessage())
                    .data(result.getData())
                    .rawOutput(newRawOutput)
                    .build();
        }

        // Append workdir notice if applicable
        if (workdirNotice != null) {
            String newRawOutput = workdirNotice + "\n\n" + (result.getRawOutput() != null ? result.getRawOutput() : "");
            return ToolResult.builder()
                    .status(result.getStatus())
                    .message(result.getMessage())
                    .data(result.getData())
                    .rawOutput(newRawOutput)
                    .build();
        }

        return result;
    }

    @Tool(name = "Process", description = """
            管理后台命令进程。灵感来源于 OpenClaw process 工具。

            动作（action）：
            - list：列出所有后台进程（运行中 + 已完成）
            - poll：拉取后台进程的新输出（增量）
            - log：读取进程的完整输出（支持 offset/limit 分页）
            - write：向进程 stdin 发送输入（自动追加换行）
            - kill：终止后台进程
            - clear：清除已完成的进程记录

            poll vs log：
            - poll 返回自上次检查以来的新输出（增量消费）
            - log 返回完整输出（支持分页），适合查看历史

            waiting_for_input 提示：
            - 当后台进程超过 15 秒无输出且 stdin 仍可写时，
              poll 和 log 会标记 waiting_for_input=true
            - 此时可用 Process(action="write") 发送输入
            """)
    public ToolResult process(
            @ToolParam(description = "动作：list / poll / log / write / kill / clear") String action,
            @ToolParam(description = "后台进程 ID（list 不需要）", required = false) String sessionId,
            @ToolParam(description = "write 动作要发送的数据", required = false) String data,
            @ToolParam(description = "log 动作的偏移行号，默认 0", required = false) Integer offset,
            @ToolParam(description = "log 动作返回的行数，默认 200", required = false) Integer limit) {

        return switch (action) {
            case "list" -> handleList();
            case "poll" -> handlePoll(sessionId);
            case "log" -> handleLog(sessionId, offset, limit);
            case "write" -> handleWrite(sessionId, data);
            case "kill" -> handleKill(sessionId);
            case "clear" -> handleClear(sessionId);
            default -> ToolResult.error("未知动作: " + action + "。支持: list / poll / log / write / kill / clear");
        };
    }

    private ToolResult executeForeground(String command, String description, Long timeout, String workdir, Map<String, String> env) {
        long effectiveTimeout = timeout != null ? timeout : CommandExecutor.DEFAULT_TIMEOUT_MS;
        long start = System.currentTimeMillis();
        CommandResult result = executor.execute(command, effectiveTimeout, workdir, env);
        long elapsed = System.currentTimeMillis() - start;

        ToolResult.Status status = result.exitCode() == 0 ? ToolResult.Status.SUCCESS : ToolResult.Status.ERROR;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("description", description);
        data.put("elapsed_ms", elapsed);
        data.put("exit_code", result.exitCode());
        if (result.timedOut()) {
            data.put("timed_out", true);
        }

        return ToolResult.builder()
                .status(status)
                .message(description + " (" + elapsed + "ms)")
                .data(data)
                .rawOutput(buildRawOutput(data, result.output()))
                .build();
    }

    private ToolResult executeWithYield(String command, String description, Long timeout, String workdir, Map<String, String> env, long yieldMs) {
        CommandExecutor.YieldResult result = executor.executeWithYield(command, yieldMs, workdir, env);

        if (result.completed()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("description", description);
            data.put("exit_code", result.exitCode());

            return ToolResult.builder()
                    .status(result.exitCode() == 0 ? ToolResult.Status.SUCCESS : ToolResult.Status.ERROR)
                    .message(description)
                    .data(data)
                    .rawOutput(buildRawOutput(data, result.output()))
                    .build();
        }

        String id;
        try {
            id = processManager.register(result.backgroundProcess(), command, description);
        } catch (Exception e) {
            // If register fails, kill the process to avoid leak
            result.backgroundProcess().destroyForcibly();
            return ToolResult.error("注册后台进程失败: " + e.getMessage());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session_id", id);
        data.put("status", "running (auto-backgrounded after " + yieldMs + "ms)");
        data.put("description", description);

        String rawOutput = buildRawOutput(data, "partial output", result.output());
        rawOutput += "\n使用 Process(action=\"poll\", session_id=\"" + id + "\") 获取后续输出。";

        return ToolResult.builder()
                .status(ToolResult.Status.SUCCESS)
                .message("已转后台: " + id)
                .data(data)
                .rawOutput(rawOutput)
                .build();
    }

    private ToolResult startImmediateBackground(String command, String description, String workdir, Map<String, String> env) {
        try {
            Process process = executor.startBackgroundProcess(command, workdir, env);
            String id = processManager.register(process, command, description);
            String rawOutput = "session_id: " + id + "\nstatus: running\ndescription: " + description
                    + "\n使用 Process(action=\"poll\", session_id=\"" + id + "\") 获取输出。";

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("session_id", id);
            data.put("status", "running");
            data.put("description", description);

            return ToolResult.builder()
                    .status(ToolResult.Status.SUCCESS)
                    .message("后台启动: " + id)
                    .data(data)
                    .rawOutput(rawOutput)
                    .build();
        } catch (Exception e) {
            return ToolResult.error("启动后台命令失败: " + e.getMessage());
        }
    }

    private ToolResult handleList() {
        ProcessManager.ProcessListResult result = processManager.list();
        if (result.processes().isEmpty()) {
            return ToolResult.success("没有后台进程");
        }
        StringBuilder rawSb = new StringBuilder();
        rawSb.append("后台进程列表:\n\n");
        for (ProcessManager.ProcessInfo info : result.processes()) {
            rawSb.append("- ").append(info.sessionId())
                    .append(" [").append(info.status()).append(']');
            if (info.exitCode() != null) {
                rawSb.append(" exit=").append(info.exitCode());
            }
            rawSb.append(" name=").append(info.name());
            rawSb.append(" elapsed=").append(info.elapsedMs() / 1000).append('s');
            if (info.waitingForInput()) {
                rawSb.append(" ⚠ waiting_for_input");
            }
            rawSb.append('\n');
        }
        return ToolResult.builder()
                .status(ToolResult.Status.SUCCESS)
                .message(result.processes().size() + " 个后台进程")
                .data("count", result.processes().size())
                .rawOutput(rawSb.toString())
                .build();
    }

    private ToolResult handlePoll(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return ToolResult.error("poll 需要 session_id 参数");
        }
        ProcessManager.ProcessSnapshot snap = processManager.poll(sessionId, 0);
        if (snap.isError()) {
            return ToolResult.error(snap.error());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session_id", sessionId);
        data.put("status", snap.status());
        if (snap.exitCode() != null) {
            data.put("exit_code", snap.exitCode());
        }
        if (snap.elapsedMs() != null) {
            data.put("elapsed_ms", snap.elapsedMs());
        }

        String rawOutput = buildRawOutput(data,
                snap.output() != null && !snap.output().isEmpty() ? snap.output() : null);
        if (snap.output() == null || snap.output().isEmpty()) {
            rawOutput = rawOutput.replace("\n(no output)", "\n(no new output)");
        }

        return ToolResult.builder()
                .status(ToolResult.Status.SUCCESS)
                .message(sessionId + " - " + snap.status())
                .data(data)
                .rawOutput(rawOutput)
                .build();
    }

    private ToolResult handleLog(String sessionId, Integer offset, Integer limit) {
        if (sessionId == null || sessionId.isEmpty()) {
            return ToolResult.error("log 需要 session_id 参数");
        }
        int off = offset != null ? offset : 0;
        int lim = limit != null ? limit : 200;
        ProcessManager.ProcessLog logResult = processManager.log(sessionId, off, lim);
        if (logResult.isError()) {
            return ToolResult.error(logResult.error());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session_id", sessionId);
        data.put("status", logResult.status());
        if (logResult.exitCode() != null) {
            data.put("exit_code", logResult.exitCode());
        }
        data.put("lines", logResult.returnedLines() + "/" + logResult.totalLines());
        data.put("offset", logResult.offset());
        if (logResult.waitingForInput()) {
            data.put("waiting_for_input", true);
        }

        StringBuilder rawSb = new StringBuilder();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            rawSb.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        if (logResult.waitingForInput()) {
            rawSb.append("⚠ waiting_for_input: true\n");
        }
        String output = logResult.output();
        if (output != null && !output.isEmpty()) {
            rawSb.append('\n').append(output);
        }
        if (logResult.offset() + logResult.returnedLines() < logResult.totalLines()) {
            rawSb.append("\n--- 更多行: Process(action=\"log\", session_id=\"").append(sessionId)
                    .append("\", offset=").append(logResult.offset() + logResult.returnedLines())
                    .append(") ---");
        }

        return ToolResult.builder()
                .status(ToolResult.Status.SUCCESS)
                .message(sessionId + " - " + logResult.returnedLines() + "/" + logResult.totalLines() + " 行")
                .data(data)
                .rawOutput(rawSb.toString())
                .build();
    }

    private ToolResult handleWrite(String sessionId, String data) {
        if (sessionId == null || sessionId.isEmpty()) {
            return ToolResult.error("write 需要 session_id 参数");
        }
        if (data == null || data.isEmpty()) {
            return ToolResult.error("write 需要 data 参数");
        }
        boolean ok = processManager.write(sessionId, data);
        if (ok) {
            return ToolResult.success("已发送输入到 " + sessionId);
        }
        return ToolResult.error("发送失败：未找到 " + sessionId + " 或进程已退出");
    }

    private ToolResult handleKill(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return ToolResult.error("kill 需要 session_id 参数");
        }
        boolean ok = processManager.kill(sessionId);
        if (ok) {
            return ToolResult.success("已终止 " + sessionId);
        }
        return ToolResult.error("未找到 " + sessionId);
    }

    private ToolResult handleClear(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return ToolResult.error("clear 需要 session_id 参数");
        }
        boolean ok = processManager.clear(sessionId);
        if (ok) {
            return ToolResult.success("已清除 " + sessionId);
        }
        return ToolResult.error("清除失败：未找到 " + sessionId + " 或进程仍在运行");
    }

    /**
     * Build a key-value formatted rawOutput string from entries, with an optional output section.
     */
    private static String buildRawOutput(Map<String, Object> entries, String output) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : entries.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        if (output != null && !output.isEmpty()) {
            sb.append("\noutput:\n").append(output);
        } else {
            sb.append("\n(no output)");
        }
        return sb.toString();
    }

    /**
     * Build a key-value formatted rawOutput string with a custom output label.
     */
    private static String buildRawOutput(Map<String, Object> entries, String outputLabel, String output) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : entries.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        if (output != null && !output.isEmpty()) {
            sb.append("\n").append(outputLabel).append(":\n").append(output);
        }
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Shell shell;

        public Builder shell(Shell shell) {
            this.shell = shell;
            return this;
        }

        public CommandTools build() {
            Shell effectiveShell = shell != null ? shell : ShellDetector.detect();
            ShellSession session = new ShellSession(effectiveShell);
            CommandExecutor exec = new CommandExecutor(effectiveShell, session);
            ProcessManager procMgr = new ProcessManager();
            return new CommandTools(exec, procMgr, session);
        }
    }
}
