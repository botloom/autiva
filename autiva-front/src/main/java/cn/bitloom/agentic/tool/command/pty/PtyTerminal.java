package cn.bitloom.agentic.tool.command.pty;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Execute commands via {@link ProcessBuilder} — unified foreground + background.
 *
 * <p>Windows uses cmd.exe, Unix uses bash. Stdout/stderr merged via
 * {@code redirectErrorStream(true)}.</p>
 *
 * <p>Inspired by AgentScope's ToolExecutor design: single execution path,
 * marker-based exit-code capture, no external library dependency.</p>
 */
@Slf4j
public final class PtyTerminal {

    public static final String MARKER_PREFIX = "__PTY_MARK_";
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    private PtyTerminal() {}

    // ── shell discovery ──

    static String[] windowsShell() {
        return new String[]{"cmd.exe", "/v:on"};
    }

    // ── foreground ──

    public static PtyResult execute(String command, String workdir, Map<String, String> env, long timeoutMs) {
        PtyHandle handle = start(command, workdir, env, true);
        try {
            return readUntilMarker(handle, timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PtyResult.error("PTY interrupted");
        } finally {
            handle.destroy();
        }
    }

    // ── background ──

    public static PtyHandle start(String command, String workdir, Map<String, String> env) {
        return start(command, workdir, env, false);
    }

    private static PtyHandle start(String command, String workdir, Map<String, String> env, boolean sendExit) {
        String markerId = MARKER_PREFIX + System.nanoTime();
        String[] shellCmd = IS_WINDOWS ? windowsShell() : new String[]{"bash", "--noprofile", "--norc"};

        String wrapped = IS_WINDOWS
                ? wrapCmd(command, workdir, markerId, sendExit)
                : wrapBash(command, workdir, markerId, sendExit);

        Process process;
        try {
            ProcessBuilder pb = new ProcessBuilder(shellCmd);
            pb.directory(workdir != null ? new java.io.File(workdir) : new java.io.File("."));
            pb.redirectErrorStream(true);
            if (env != null) pb.environment().putAll(env);
            process = pb.start();
        } catch (IOException e) {
            log.error("[PtyTerminal] process start failed", e);
            throw new UncheckedIOException(e);
        }

        try {
            OutputStream stdin = process.getOutputStream();
            stdin.write((wrapped + "\n").getBytes(StandardCharsets.UTF_8));
            stdin.flush();
            stdin.close(); // signal EOF to cmd/bash so it executes and exits
        } catch (IOException e) {
            process.destroyForcibly();
            log.error("[PtyTerminal] stdin write failed", e);
            throw new UncheckedIOException(e);
        }

        return new PtyHandle(process, markerId, workdir, System.currentTimeMillis());
    }

    // ── marker reader ──

    public static PtyResult readUntilMarker(PtyHandle handle, long timeoutMs) throws InterruptedException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        CountDownLatch latch = new CountDownLatch(1);

        Thread reader = new Thread(() -> {
            try (InputStream stdout = handle.process.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = stdout.read(buf)) != -1) {
                    synchronized (buffer) { buffer.write(buf, 0, n); }
                    if (buffer.toString(StandardCharsets.UTF_8).contains(handle.markerId)) break;
                }
            } catch (IOException e) {
                log.debug("[PtyTerminal] reader ended: {}", e.getMessage());
            } finally {
                latch.countDown();
            }
        }, "pty-reader");
        reader.setDaemon(true);
        reader.start();

        boolean completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        if (!completed) {
            log.warn("[PtyTerminal] timed out after {}ms", timeoutMs);
            handle.process.destroyForcibly();
            latch.await(2, TimeUnit.SECONDS);
        }

        String raw;
        synchronized (buffer) { raw = buffer.toString(StandardCharsets.UTF_8); }
        return parseResult(raw, handle.markerId, handle.fallbackCwd, !completed);
    }

    // ── wrappers ──

    /**
     * cmd.exe wrapper using delayed expansion ({@code /v:on}) for exit code capture.
     * <p>Pipe char in echo must be escaped: {@code ^|}</p>
     */
    static String wrapCmd(String command, String workdir, String markerId, boolean exit) {
        StringBuilder sb = new StringBuilder();
        sb.append("chcp 65001 > nul 2>&1 & "); // switch to UTF-8 codepage
        if (workdir != null && !workdir.isEmpty()) {
            sb.append("cd /d \"").append(workdir).append("\" & ");
        }
        sb.append(command);
        sb.append(" & echo ").append(markerId).append("^|^|!ERRORLEVEL!^|^|!CD!");
        if (exit) sb.append(" & exit");
        return sb.toString();
    }

    static String wrapBash(String command, String workdir, String markerId, boolean exit) {
        StringBuilder sb = new StringBuilder();
        if (workdir != null && !workdir.isEmpty()) {
            sb.append("cd '").append(workdir.replace("'", "'\\''")).append("' ; ");
        }
        sb.append(command);
        sb.append(" ; echo '").append(markerId).append("||$?||$(pwd)'");
        if (exit) sb.append(" ; exit");
        return sb.toString();
    }

    // ── parsing ──

    public static PtyResult parseResult(String raw, String markerId, String fallbackCwd, boolean timedOut) {
        int idx = raw.indexOf(markerId);
        if (idx < 0) {
            if (timedOut) return new PtyResult(raw, -1, nvl(fallbackCwd));
            log.warn("[PtyTerminal] marker '{}' not found", markerId);
            return new PtyResult(raw, -1, nvl(fallbackCwd));
        }
        String output = raw.substring(0, idx);
        int lineEnd = raw.indexOf('\n', idx);
        String markerLine = lineEnd >= 0 ? raw.substring(idx, lineEnd) : raw.substring(idx);
        // Split on "||" (avoids conflict with Windows drive letters like C:\)
        String[] parts = markerLine.split("\\|\\|");
        int exitCode = -1;
        if (parts.length > 1 && !parts[1].isEmpty()) {
            try { exitCode = Integer.parseInt(parts[1].trim()); }
            catch (NumberFormatException e) { log.debug("[PtyTerminal] bad exit code: {}", parts[1]); }
        }
        String cwd = parts.length > 2 ? parts[2].trim() : nvl(fallbackCwd);
        String cleaned = output.replace("\r\n", "\n")
                .replaceAll("\u001B\\[[0-?]*[ -/]*[@-~]", "")
                .strip();
        return new PtyResult(cleaned, exitCode, cwd);
    }

    private static String nvl(String s) { return s != null ? s : ""; }

    // ── handle ──

    public record PtyHandle(Process process, String markerId, String fallbackCwd, long startTimeMs) implements Closeable {
        public InputStream stdout() { return process.getInputStream(); }
        public OutputStream stdin() { return process.getOutputStream(); }
        public boolean isAlive() { return process.isAlive(); }
        public void destroy() { process.destroyForcibly(); }
        @Override public void close() { destroy(); }
    }
}
