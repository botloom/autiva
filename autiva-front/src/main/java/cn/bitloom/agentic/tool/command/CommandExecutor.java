package cn.bitloom.agentic.tool.command;

import cn.bitloom.agentic.tool.command.pty.PtyResult;
import cn.bitloom.agentic.tool.command.pty.PtySession;
import cn.bitloom.agentic.tool.command.pty.PtyTerminal;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.util.Map;

/**
 * Unified PTY command executor — foreground via {@link PtySession}, background via {@link PtyTerminal}.
 *
 * <p>Each {@code CommandExecutor} owns one {@link PtySession} (one PTY per conversation).
 * Foreground commands are serialized through the session; background/yield commands
 * spawn separate PTY processes.</p>
 *
 * <p>cwd is maintained <b>by the PTY itself</b> — the persistent shell naturally
 * tracks its working directory. No external cwd persistence needed.</p>
 */
@Slf4j
public class CommandExecutor implements Closeable {

    public static final long DEFAULT_TIMEOUT_MS = 120_000L;
    public static final long MAX_TIMEOUT_MS = 600_000L;
    public static final long DEFAULT_YIELD_MS = 10_000L;
    public static final int MAX_OUTPUT_LINES = 30_000;
    public static final int MAX_COMMAND_LENGTH = 8000;

    private final PtySession pty;
    private final ShellSession envSession;

    public CommandExecutor(ShellSession envSession) {
        this.envSession = envSession;
        this.pty = PtySession.create(System.getProperty("user.home"), envSession.mergedEnv(null));
    }

    // ── foreground ──

    public CommandResult execute(String command, long timeoutMs, String workdir, Map<String, String> env) {
        long effectiveTimeout = Math.min(Math.max(timeoutMs, 1000L), MAX_TIMEOUT_MS);
        Map<String, String> mergedEnv = envSession.mergedEnv(env);

        // Prepend cd if workdir override specified
        String effectiveCmd = command;
        if (workdir != null && !workdir.isEmpty()) {
            effectiveCmd = (PtySession.IS_WINDOWS
                    ? "cd /d \"" + workdir + "\" & "
                    : "cd '" + workdir.replace("'", "'\\''") + "' ; ")
                    + command;
        }

        PtyResult r = pty.execute(effectiveCmd, effectiveTimeout);
        String cleaned = OutputSanitizer.clean(r.output());
        cleaned = OutputSanitizer.truncate(cleaned, MAX_OUTPUT_LINES);
        return CommandResult.success(cleaned, r.exitCode(), false);
    }

    // ── background / yield ──

    PtyTerminal.PtyHandle startBackground(String command, String workdir, Map<String, String> env) {
        String cwd = workdir != null ? workdir : System.getProperty("user.home");
        Map<String, String> mergedEnv = envSession.mergedEnv(env);
        return pty.startBackground(command, cwd, mergedEnv);
    }

    public YieldResult executeWithYield(String command, long yieldMs, String workdir, Map<String, String> env) {
        String cwd = workdir != null ? workdir : System.getProperty("user.home");
        Map<String, String> mergedEnv = envSession.mergedEnv(env);

        PtyTerminal.PtyHandle handle = pty.startBackground(command, cwd, mergedEnv);
        try {
            PtyResult r = PtyTerminal.readUntilMarker(handle, yieldMs > 0 ? yieldMs : DEFAULT_YIELD_MS);
            if (r.exitCode() >= 0 || !handle.isAlive()) {
                handle.destroy();
                String cleaned = OutputSanitizer.clean(r.output());
                cleaned = OutputSanitizer.truncate(cleaned, MAX_OUTPUT_LINES);
                return new YieldResult(true, cleaned, r.exitCode(), null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handle.destroy();
            return new YieldResult(true, "Interrupted", -1, null);
        }

        return new YieldResult(false,
                OutputSanitizer.clean(PtySession.readPartial(handle)),
                null, handle);
    }

    @Override
    public void close() { pty.close(); }

    record YieldResult(boolean completed, String output, Integer exitCode, PtyTerminal.PtyHandle backgroundHandle) {
        boolean needsBackground() { return !completed && backgroundHandle != null; }
    }
}
