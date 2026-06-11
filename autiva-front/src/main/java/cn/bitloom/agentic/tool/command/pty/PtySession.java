package cn.bitloom.agentic.tool.command.pty;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Persistent shell session — one per conversation.
 *
 * <p>Auto‑restarts when the underlying process dies (timeout, crash, etc).
 * Commands execute sequentially via {@code synchronized}.</p>
 *
 * <p>Uses {@link ProcessBuilder} for shell lifecycle management.</p>
 */
@Slf4j
public class PtySession implements Closeable {

    public static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    private Process process;
    private OutputStream stdin;
    private InputStream stdout;
    private String[] shellCmd;
    private Map<String, String> env;
    private String workdir;

    public PtySession(Process process, String[] shellCmd, Map<String, String> env, String workdir) {
        this.process = process;
        this.shellCmd = shellCmd;
        this.env = env;
        this.workdir = workdir;
        this.stdin = process.getOutputStream();
        this.stdout = process.getInputStream();
    }

    public static PtySession create(String workdir, Map<String, String> env) {
        String[] shellCmd = IS_WINDOWS
                ? PtyTerminal.windowsShell()
                : new String[]{"bash", "--noprofile", "--norc"};
        Map<String, String> filteredEnv = IS_WINDOWS ? filterWindowsApps(env) : env;
        Process p = startProcess(shellCmd, filteredEnv, workdir);
        log.info("[PtySession] created, shell={}, workdir={}", shellCmd[0], workdir);
        return new PtySession(p, shellCmd, filteredEnv, workdir);
    }

    // ── execute ──

    public synchronized PtyResult execute(String command, long timeoutMs) {
        String marker = PtyTerminal.MARKER_PREFIX + System.nanoTime();
        String wrapped = IS_WINDOWS
                ? PtyTerminal.wrapCmd(command, null, marker, false)
                : PtyTerminal.wrapBash(command, null, marker, false);

        try {
            stdin.write((wrapped + "\n").getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (IOException e) {
            log.warn("[PtySession] write failed, restarting PTY", e);
            restart();
            try {
                stdin.write((wrapped + "\n").getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            } catch (IOException e2) {
                return PtyResult.error("PTY write failed after restart: " + e2.getMessage());
            }
        }

        PtyResult r = readUntilMarker(marker, timeoutMs);
        if (r.exitCode() == -1 && !r.output().isEmpty() && r.output().contains(marker + "||")) {
            // Timed out — restart PTY so subsequent commands work
            restart();
        }
        return r;
    }

    // ── background ──

    public PtyTerminal.PtyHandle startBackground(String command, String workdir, Map<String, String> env) {
        return PtyTerminal.start(command, workdir, env);
    }

    // ── internals ──

    private synchronized void restart() {
        log.info("[PtySession] restarting shell");
        try { process.destroyForcibly(); } catch (Exception ignored) {}
        Map<String, String> filteredEnv = IS_WINDOWS ? filterWindowsApps(env) : env;
        Process p = startProcess(shellCmd, filteredEnv, workdir);
        this.process = p;
        this.stdin = p.getOutputStream();
        this.stdout = p.getInputStream();
        drainPrompt();
    }

    private void drainPrompt() {
        try {
            Thread.sleep(200); // let PS finish init
            byte[] buf = new byte[8192];
            while (stdout.available() > 0) {
                // noinspection ResultOfMethodCallIgnored
                stdout.read(buf, 0, Math.min(buf.length, stdout.available()));
            }
        } catch (Exception ignored) {}
    }

    private static Process startProcess(String[] shellCmd, Map<String, String> env, String workdir) {
        try {
            ProcessBuilder pb = new ProcessBuilder(shellCmd);
            pb.directory(workdir != null ? new java.io.File(workdir) : new java.io.File("."));
            pb.redirectErrorStream(true);
            if (env != null) pb.environment().putAll(env);
            return pb.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start shell process", e);
        }
    }

    /** Remove WindowsApps entries from PATH to prevent Store redirector hangs. */
    private static Map<String, String> filterWindowsApps(Map<String, String> env) {
        String path = env.get("PATH");
        if (path == null) path = System.getenv("PATH");
        if (path == null) return env;
        StringBuilder filtered = new StringBuilder();
        for (String entry : path.split(";")) {
            if (!entry.toLowerCase().contains("\\windowsapps")) {
                if (filtered.length() > 0) filtered.append(';');
                filtered.append(entry);
            }
        }
        Map<String, String> result = new java.util.LinkedHashMap<>(env);
        result.put("PATH", filtered.toString());
        return result;
    }

    private PtyResult readUntilMarker(String marker, long timeoutMs) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PtyResult> resultRef = new AtomicReference<>();

        Thread reader = new Thread(() -> {
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = stdout.read(buf)) != -1) {
                    buffer.write(buf, 0, n);
                    if (buffer.toString(StandardCharsets.UTF_8).contains(marker)) {
                        resultRef.set(PtyTerminal.parseResult(
                                buffer.toString(StandardCharsets.UTF_8), marker, null, false));
                        break;
                    }
                }
            } catch (IOException e) {
                log.debug("[PtySession] reader ended: {}", e.getMessage());
            } finally {
                latch.countDown();
            }
        }, "pty-session-reader");
        reader.setDaemon(true);
        reader.start();

        try {
            boolean done = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!done) {
                log.warn("[PtySession] timed out after {}ms", timeoutMs);
                process.destroyForcibly();
                // Reader thread may still be running; latch will eventually count down
                latch.await(1, TimeUnit.SECONDS);
                return new PtyResult(buffer.toString(StandardCharsets.UTF_8), -1, "");
            }
            PtyResult r = resultRef.get();
            return r != null ? r : new PtyResult(buffer.toString(StandardCharsets.UTF_8), -1, "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PtyResult.error("PTY interrupted");
        }
    }

    public static String readPartial(PtyTerminal.PtyHandle handle) {
        try {
            int avail = handle.stdout().available();
            if (avail <= 0) return "";
            byte[] buf = new byte[Math.min(avail, 65536)];
            int n = handle.stdout().read(buf);
            return n > 0 ? new String(buf, 0, n, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    @Override
    public synchronized void close() {
        try { process.destroyForcibly(); } catch (Exception ignored) {}
        log.info("[PtySession] closed");
    }
}
