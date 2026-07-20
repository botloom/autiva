package cn.bitloom.agentic.tool.command;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 命令执行工具，无状态 ProcessBuilder 模式。
 *
 * <p>支持前台执行、智能后台化（yield_ms）和立即后台模式。
 * cwd/env 通过 ShellSession 持久化，每次执行时通过命令前缀注入。
 */
public class CommandTool extends AbstractTool<CommandTool.Input> {

    private static final String DESCRIPTION = """
            执行 Bash 命令，持久化会话维护 cwd 和环境变量。默认同步执行，2 分钟超时。长任务用 background=true 异步执行，配合 Process 工具轮询/终止。
            """;

    private final CommandExecutor executor;

    private final ProcessManager processManager;

    public record Input(
            @ToolParam(description = "要执行的命令") String command,
            @ToolParam(description = "5-10 字描述命令作用") String description,
            @ToolParam(description = "超时毫秒，默认 120000，最大 600000", required = false) Long timeout,
            @ToolParam(description = "工作目录覆盖，不传则使用持久化的 cwd", required = false) String workdir,
            @ToolParam(description = "环境变量覆盖（JSON 对象如 {\"KEY\":\"VALUE\"}），合并到持久化 env 之上", required = false) Map<String, String> env,
            @ToolParam(description = "前台运行超过此毫秒数自动转后台，不传则纯前台执行", required = false) Long yieldMs,
            @ToolParam(description = "true 则立即转为后台执行，忽略 yield_ms", required = false) Boolean background
    ) {}

    private CommandTool(Builder builder) {
        super("Command", DESCRIPTION, Input.class);
        this.executor = builder.executor;
        this.processManager = builder.processManager;
    }

    @Override
    public @NonNull ToolResult execute(Input input, ToolContext context) {
        // 验证命令长度
        if (input.command() != null && input.command().length() > CommandExecutor.MAX_COMMAND_LENGTH) {
            return ToolResult.error("命令过长 (" + input.command().length() + " 字符)，最大允许 "
                    + CommandExecutor.MAX_COMMAND_LENGTH + " 字符。请将命令拆分为多次执行。");
        }

        CommandSafety.SafetyCheck safety = CommandSafety.check(input.command());
        ToolResult result;
        if (Boolean.TRUE.equals(input.background())) {
            result = startImmediateBackground(input.command(), input.description(), input.workdir(), input.env());
        } else if (input.yieldMs() != null && input.yieldMs() > 0) {
            result = executeWithYield(input.command(), input.description(), input.timeout(), input.workdir(), input.env(), input.yieldMs());
        } else {
            result = executeForeground(input.command(), input.description(), input.timeout(), input.workdir(), input.env());
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

        return result;
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
            // 注册失败时销毁进程避免泄漏
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
            Process process = executor.startBackground(command, workdir, env);
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

    /**
     * 构建键值对格式的rawOutput字符串，带可选的输出部分。
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
     * 构建键值对格式的rawOutput字符串，带自定义输出标签。
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

        private CommandExecutor executor;
        private ProcessManager processManager;

        private Builder() {
        }

        public Builder executor(CommandExecutor executor) {
            this.executor = executor;
            return this;
        }

        public Builder processManager(ProcessManager processManager) {
            this.processManager = processManager;
            return this;
        }

        public CommandTool build() {
            if (this.executor == null) {
                ShellSession envSession = new ShellSession();
                this.executor = new CommandExecutor(envSession);
            }
            if (this.processManager == null) {
                this.processManager = new ProcessManager();
            }
            return new CommandTool(this);
        }
    }

}
