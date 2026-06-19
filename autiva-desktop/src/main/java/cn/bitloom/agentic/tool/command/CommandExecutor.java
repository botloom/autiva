package cn.bitloom.agentic.tool.command;

import cn.bitloom.agentic.tool.command.shell.ShellExecutor;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 无状态命令执行器 — 每次执行创建新 ProcessBuilder 进程。
 *
 * <p>灵感来源：
 * <ul>
 *   <li>AgentScope 的 ShellCommandTool（无状态 ProcessBuilder 执行）</li>
 *   <li>Trae Agent 的 BashTool（sentinel/marker 协议捕获退出码）</li>
 * </ul>
 *
 * <p>cwd/env 通过 {@link ShellSession} 持久化，每次执行时通过命令前缀注入。
 * 退出码和 cwd 通过 marker 行捕获（{@code __CMD_MARK_<nano>||<exitCode>||<cwd>}）。
 */
@Slf4j
public class CommandExecutor implements Closeable {

    public static final long DEFAULT_TIMEOUT_MS = 120_000L;
    public static final long MAX_TIMEOUT_MS = 600_000L;
    public static final long DEFAULT_YIELD_MS = 10_000L;
    public static final int MAX_OUTPUT_LINES = 30_000;
    public static final int MAX_COMMAND_LENGTH = 8000;

    private final ShellExecutor shellExecutor;
    private final ShellSession shellSession;

    public CommandExecutor(ShellSession shellSession) {
        this.shellExecutor = ShellExecutor.create();
        this.shellSession = shellSession;
    }

    public CommandExecutor(ShellExecutor shellExecutor, ShellSession shellSession) {
        this.shellExecutor = shellExecutor;
        this.shellSession = shellSession;
    }

    // ── foreground ──

    public CommandResult execute(String command, long timeoutMs, String workdir, Map<String, String> env) {
        long effectiveTimeout = Math.min(Math.max(timeoutMs, 1000L), MAX_TIMEOUT_MS);
        String cwd = shellSession.resolveWorkdir(workdir);
        Map<String, String> mergedEnv = shellExecutor.filterEnv(shellSession.mergedEnv(env));
        String wrappedCmd = shellExecutor.wrapCommand(command, cwd);
        String markerId = extractMarkerId(wrappedCmd);

        ProcessBuilder pb = shellExecutor.createProcessBuilder(wrappedCmd, cwd, mergedEnv);
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            log.error("[CommandExecutor] process start failed", e);
            return CommandResult.error("进程启动失败: " + e.getMessage());
        }

        // 异步读取 stdout
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        CountDownLatch latch = new CountDownLatch(1);

        Thread reader = new Thread(() -> {
            try (InputStream stdout = process.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = stdout.read(buf)) != -1) {
                    synchronized (buffer) { buffer.write(buf, 0, n); }
                    // 检测 marker 出现
                    String current = buffer.toString(StandardCharsets.UTF_8);
                    if (markerId != null && current.contains(markerId)) break;
                }
            } catch (IOException e) {
                log.debug("[CommandExecutor] reader ended: {}", e.getMessage());
            } finally {
                latch.countDown();
            }
        }, "cmd-reader");
        reader.setDaemon(true);
        reader.start();

        boolean completed;
        try {
            completed = latch.await(effectiveTimeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return CommandResult.error("命令执行被中断");
        }

        if (!completed) {
            log.warn("[CommandExecutor] timed out after {}ms", effectiveTimeout);
            process.destroyForcibly();
            try { latch.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        }

        // 解析输出
        String raw;
        synchronized (buffer) { raw = buffer.toString(StandardCharsets.UTF_8); }

        // 使用 EncodingHelper 解码
        String decoded = EncodingHelper.decodeBest(buffer.toByteArray());

        ShellExecutor.ParseResult parseResult = shellExecutor.parseOutput(decoded, markerId, cwd, !completed);

        // 更新 session cwd
        if (parseResult.cwd() != null && !parseResult.cwd().isEmpty()) {
            shellSession.updateCwd(parseResult.cwd());
        }

        // 清理输出
        String cleaned = OutputSanitizer.clean(parseResult.output());
        cleaned = OutputSanitizer.truncate(cleaned, MAX_OUTPUT_LINES);

        return CommandResult.success(cleaned, parseResult.exitCode(), !completed);
    }

    // ── background ──

    /**
     * 启动后台进程，返回 Process 句柄供 ProcessManager 管理。
     */
    public Process startBackground(String command, String workdir, Map<String, String> env) {
        String cwd = shellSession.resolveWorkdir(workdir);
        Map<String, String> mergedEnv = shellExecutor.filterEnv(shellSession.mergedEnv(env));
        String wrappedCmd = shellExecutor.wrapCommand(command, cwd);

        ProcessBuilder pb = shellExecutor.createProcessBuilder(wrappedCmd, cwd, mergedEnv);
        try {
            return pb.start();
        } catch (IOException e) {
            log.error("[CommandExecutor] background process start failed", e);
            throw new UncheckedIOException(e);
        }
    }

    // ── yield ──

    public YieldResult executeWithYield(String command, long yieldMs, String workdir, Map<String, String> env) {
        Process process = startBackground(command, workdir, env);
        String cwd = shellSession.resolveWorkdir(workdir);
        String wrappedCmd = shellExecutor.wrapCommand(command, cwd);
        String markerId = extractMarkerId(wrappedCmd);

        // 尝试在 yieldMs 内读取完成
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        CountDownLatch latch = new CountDownLatch(1);

        Thread reader = new Thread(() -> {
            try (InputStream stdout = process.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = stdout.read(buf)) != -1) {
                    synchronized (buffer) { buffer.write(buf, 0, n); }
                    String current = buffer.toString(StandardCharsets.UTF_8);
                    if (markerId != null && current.contains(markerId)) break;
                }
            } catch (IOException e) {
                log.debug("[CommandExecutor] yield reader ended: {}", e.getMessage());
            } finally {
                latch.countDown();
            }
        }, "cmd-yield-reader");
        reader.setDaemon(true);
        reader.start();

        boolean completed;
        try {
            completed = latch.await(yieldMs > 0 ? yieldMs : DEFAULT_YIELD_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new YieldResult(true, "Interrupted", -1, null);
        }

        if (completed || !process.isAlive()) {
            // 进程已完成
            String raw;
            synchronized (buffer) { raw = buffer.toString(StandardCharsets.UTF_8); }
            String decoded = EncodingHelper.decodeBest(buffer.toByteArray());
            ShellExecutor.ParseResult parseResult = shellExecutor.parseOutput(decoded, markerId, cwd, false);
            if (parseResult.cwd() != null && !parseResult.cwd().isEmpty()) {
                shellSession.updateCwd(parseResult.cwd());
            }
            String cleaned = OutputSanitizer.clean(parseResult.output());
            cleaned = OutputSanitizer.truncate(cleaned, MAX_OUTPUT_LINES);
            return new YieldResult(true, cleaned, parseResult.exitCode(), null);
        }

        // 超时，转后台
        String partial = readPartial(process);
        return new YieldResult(false, OutputSanitizer.clean(partial), null, process);
    }

    /** 从进程 stdout 读取当前可用数据（非阻塞） */
    public static String readPartial(Process process) {
        try {
            int avail = process.getInputStream().available();
            if (avail <= 0) return "";
            byte[] buf = new byte[Math.min(avail, 65536)];
            int n = process.getInputStream().read(buf);
            return n > 0 ? new String(buf, 0, n, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    /** 从 wrapCommand 结果中提取 markerId */
    private String extractMarkerId(String wrappedCmd) {
        int idx = wrappedCmd.indexOf(MARKER_PREFIX);
        if (idx < 0) return null;
        // markerId 格式：__CMD_MARK_<nano>
        int end = idx;
        while (end < wrappedCmd.length() && !isDelimiter(wrappedCmd.charAt(end))) {
            end++;
        }
        return wrappedCmd.substring(idx, end);
    }

    private boolean isDelimiter(char c) {
        return c == '^' || c == '|' || c == '\'' || c == '\n' || c == '\r';
    }

    private static final String MARKER_PREFIX = ShellExecutor.MARKER_PREFIX;

    @Override
    public void close() {
        // 无状态执行器，无需关闭资源
    }

    record YieldResult(boolean completed, String output, Integer exitCode, Process backgroundProcess) {
        boolean needsBackground() { return !completed && backgroundProcess != null; }
    }
}
