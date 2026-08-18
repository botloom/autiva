package cn.bitloom.agentic.tool.command;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 命令执行工具（v13 持久化 Shell 会话）。
 *
 * <p>对标 Claude Code 的 BashTool：前台命令走持久 shell 进程（{@link PersistentShellSession}），
 * {@code cd}/{@code export}/{@code source}/shell 函数/后台任务天然持久；后台命令（{@code run_in_background=true}）
 * 启动独立 detached 进程，由 {@link ProcessManager} 管理。
 *
 * <p>stdout 与 stderr 分离返回，超长输出头+尾保留。
 */
@Slf4j
public class CommandTool extends AbstractTool<CommandTool.Input> {

    private static final String DESCRIPTION = """
            执行命令，持久化 Shell 会话维护 cwd 和环境变量（cd/export/source/函数天然持久）。默认前台同步执行，2 分钟超时。长任务用 run_in_background=true 异步执行，配合 Process 工具轮询/终止。stdout 与 stderr 分离返回。
            """;

    private final PersistentShellRegistry shellRegistry;
    private final CommandExecutor backgroundExecutor;
    private final ProcessManager processManager;

    public record Input(
            @ToolParam(description = "要执行的命令") String command,
            @ToolParam(description = "5-10 字描述命令作用") String description,
            @ToolParam(description = "超时毫秒，默认 120000，最大 600000", required = false) Long timeout,
            @ToolParam(description = "是否后台运行，后台进程用 Process 工具轮询/终止", required = false) Boolean run_in_background,
            @ToolParam(description = "是否复用上条命令的 prompt 上下文（跳过清理残留输出），默认 false", required = false) Boolean reuse_prompt
    ) {}

    private CommandTool(Builder builder) {
        super("Command", DESCRIPTION, Input.class);
        this.shellRegistry = builder.shellRegistry;
        this.backgroundExecutor = builder.backgroundExecutor;
        this.processManager = builder.processManager;
    }

    @Override
    public @NonNull ToolResult execute(Input input, @Nullable ToolContext context) {
        log.info("[CommandTool] execute called: command='{}', description='{}', timeout={}, run_in_background={}, reuse_prompt={}",
                input.command(), input.description(), input.timeout(), input.run_in_background(), input.reuse_prompt());

        if (input.command() == null || input.command().isEmpty()) {
            log.warn("[CommandTool] command is empty");
            return ToolResult.error("command 参数不能为空");
        }
        if (input.command().length() > PersistentShellSession.MAX_COMMAND_LENGTH) {
            return ToolResult.error("命令过长 (" + input.command().length() + " 字符)，最大允许 "
                    + PersistentShellSession.MAX_COMMAND_LENGTH + " 字符。请将命令拆分为多次执行。");
        }

        CommandSafety.SafetyCheck safety = CommandSafety.check(input.command());
        log.debug("[CommandTool] safety check: destructive={}, warning={}, rule={}",
                safety.isDestructive(), safety.isWarning(), safety.rule());

        String sessionId = extractSessionId(context);
        String projectPath = extractProjectPath(context);
        log.debug("[CommandTool] sessionId from context: {}, projectPath: {}", sessionId, projectPath);

        ToolResult result;
        if (Boolean.TRUE.equals(input.run_in_background())) {
            result = startBackground(input.command(), input.description());
        } else {
            result = executeForeground(input.command(), input.description(),
                    input.timeout(), Boolean.TRUE.equals(input.reuse_prompt()), sessionId, projectPath);
        }

        log.info("[CommandTool] execute result: status={}, message='{}'",
                result.getStatus(), result.getMessage());

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

    private ToolResult executeForeground(String command, String description, Long timeout, boolean reusePrompt, String sessionId, String projectPath) {
        long effectiveTimeout = timeout != null ? timeout : PersistentShellSession.DEFAULT_TIMEOUT_MS;
        long start = System.currentTimeMillis();
        log.info("[CommandTool] executeForeground: sessionId='{}', projectPath='{}', effectiveTimeout={}ms",
                sessionId, projectPath, effectiveTimeout);
        PersistentShellSession shellSession = shellRegistry.getOrCreate(sessionId, projectPath);
        CommandResult result = shellSession.execute(command, effectiveTimeout, reusePrompt);
        long elapsed = System.currentTimeMillis() - start;
        log.info("[CommandTool] executeForeground done: exitCode={}, timedOut={}, elapsed={}ms",
                result.exitCode(), result.timedOut(), elapsed);

        ToolResult.Status status = result.exitCode() != null && result.exitCode() == 0
                ? ToolResult.Status.SUCCESS : ToolResult.Status.ERROR;

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
                .rawOutput(buildRawOutput(data, result.output(), result.stderr()))
                .build();
    }

    private ToolResult startBackground(String command, String description) {
        try {
            Process process = backgroundExecutor.startBackground(command, null, null);
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

    private String extractSessionId(ToolContext context) {
        if (context == null || context.getContext() == null) {
            return null;
        }
        Object id = context.getContext().get("sessionId");
        return id instanceof String sid ? sid : null;
    }

    private String extractProjectPath(ToolContext context) {
        if (context == null || context.getContext() == null) {
            return null;
        }
        Object path = context.getContext().get("projectPath");
        return path instanceof String p ? p : null;
    }

    /**
     * 构建键值对格式的 rawOutput，stdout 与 stderr 分离展示。
     */
    private static String buildRawOutput(Map<String, Object> entries, String stdout, String stderr) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : entries.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        if (stdout != null && !stdout.isEmpty()) {
            sb.append("\nstdout:\n").append(stdout);
        } else {
            sb.append("\n(no stdout)");
        }
        if (stderr != null && !stderr.isEmpty()) {
            sb.append("\n\nstderr:\n").append(stderr);
        }
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private PersistentShellRegistry shellRegistry;
        private CommandExecutor backgroundExecutor;
        private ProcessManager processManager;

        private Builder() {
        }

        public Builder shellRegistry(PersistentShellRegistry shellRegistry) {
            this.shellRegistry = shellRegistry;
            return this;
        }

        public Builder backgroundExecutor(CommandExecutor backgroundExecutor) {
            this.backgroundExecutor = backgroundExecutor;
            return this;
        }

        public Builder processManager(ProcessManager processManager) {
            this.processManager = processManager;
            return this;
        }

        public CommandTool build() {
            if (this.shellRegistry == null) {
                throw new IllegalStateException("PersistentShellRegistry 必须注入");
            }
            if (this.backgroundExecutor == null) {
                this.backgroundExecutor = new CommandExecutor(new ShellSession());
            }
            if (this.processManager == null) {
                this.processManager = new ProcessManager();
            }
            return new CommandTool(this);
        }
    }

}
