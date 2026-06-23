package cn.bitloom.agentic.tool.command;

import cn.bitloom.agentic.tool.command.shell.ShellExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 管理后台进程 — register, poll, log, write, kill, clear。
 *
 * <p>使用 {@link Process}（java.lang.Process）替代原来的 {@code PtyHandle}。
 * marker 检测通过 {@link ShellExecutor} 提供的解析方法实现。
 */
@Slf4j
@Component
public class ProcessManager {

    private static final long DEFAULT_BG_TIMEOUT_MS = 600_000L;
    private static final long CLEANUP_INTERVAL_MS = 300_000L;
    private static final long COMPLETED_TTL_MS = 60_000L;

    private final Map<String, BackgroundProcess> processes = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(0);
    private final ShellExecutor shellExecutor;

    public ProcessManager() {
        this.shellExecutor = ShellExecutor.create();
        Thread cleanup = new Thread(this::cleanupLoop, "bg-process-cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    public ProcessManager(ShellExecutor shellExecutor) {
        this.shellExecutor = shellExecutor;
        Thread cleanup = new Thread(this::cleanupLoop, "bg-process-cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    /** 注册后台进程并开始读取输出。 */
    public String register(Process process, String command, String description) {
        String id = "proc_" + idSeq.incrementAndGet();
        BackgroundProcess bg = new BackgroundProcess(id, process, command, description, shellExecutor);
        processes.put(id, bg);
        bg.start();
        log.info("[ProcessManager] registered {} cmd={}", id, command);
        return id;
    }

    public ProcessSnapshot poll(String id, long waitMs) {
        BackgroundProcess bg = processes.get(id);
        if (bg == null) return ProcessSnapshot.error("Unknown session_id: " + id);
        if (waitMs > 0) bg.awaitNewOutput(waitMs);
        return bg.snapshot();
    }

    public ProcessLog log(String id, int offset, int limit) {
        BackgroundProcess bg = processes.get(id);
        if (bg == null) return ProcessLog.error("Unknown session_id: " + id);
        String full = bg.getFullOutput();
        String[] lines = full.split("\n", -1);
        int total = lines.length;
        int start = Math.max(0, offset);
        int count = limit > 0 ? limit : 200;
        List<String> result = new ArrayList<>();
        for (int i = start; i < Math.min(start + count, total); i++) result.add(lines[i]);
        return new ProcessLog(
                String.join("\n", result), total, start, result.size(),
                bg.isAlive() ? "running" : "completed", bg.exitCode(),
                bg.isLikelyWaitingForInput(), null);
    }

    public ProcessListResult list() {
        List<ProcessInfo> infos = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, BackgroundProcess> e : processes.entrySet()) {
            BackgroundProcess bg = e.getValue();
            infos.add(new ProcessInfo(
                    e.getKey(), deriveName(bg.command),
                    bg.isAlive() ? "running" : "completed",
                    bg.exitCode(), now - bg.startTimeMs,
                    bg.isLikelyWaitingForInput()));
        }
        return new ProcessListResult(infos);
    }

    public boolean write(String id, String data) {
        BackgroundProcess bg = processes.get(id);
        return bg != null && bg.sendInput(data);
    }

    public boolean kill(String id) {
        BackgroundProcess bg = processes.get(id);
        if (bg == null) return false;
        bg.destroy();
        log.info("[ProcessManager] killed {}", id);
        return true;
    }

    public boolean clear(String id) {
        BackgroundProcess bg = processes.get(id);
        if (bg == null) return false;
        if (bg.isAlive()) return false;
        processes.remove(id);
        log.debug("[ProcessManager] cleared {}", id);
        return true;
    }

    private String deriveName(String cmd) {
        if (cmd == null || cmd.isBlank()) return "unknown";
        String t = cmd.trim();
        int s = t.indexOf(' ');
        String v = s > 0 ? t.substring(0, s) : t;
        int ls = v.lastIndexOf('/'), bs = v.lastIndexOf('\\');
        int c = Math.max(ls, bs);
        if (c >= 0) v = v.substring(c + 1);
        return v.length() > 20 ? v.substring(0, 20) : v;
    }

    private void cleanupLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try { Thread.sleep(CLEANUP_INTERVAL_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            long now = System.currentTimeMillis();
            processes.entrySet().removeIf(e -> {
                BackgroundProcess bg = e.getValue();
                if (!bg.isAlive() && (now - bg.completedAtMs > COMPLETED_TTL_MS)) {
                    log.debug("[ProcessManager] cleaning up: {}", e.getKey());
                    return true;
                }
                if (bg.isAlive() && (now - bg.startTimeMs > DEFAULT_BG_TIMEOUT_MS)) {
                    log.warn("[ProcessManager] killing timed-out: {}", e.getKey());
                    bg.destroy();
                    return true;
                }
                return false;
            });
        }
    }

    // ── records ──

    public record ProcessSnapshot(String status, String output, Integer exitCode, Long elapsedMs, String error) {
        public boolean isError() { return error != null; }
        public static ProcessSnapshot error(String msg) { return new ProcessSnapshot("error", "", null, null, msg); }
    }
    public record ProcessLog(String output, int totalLines, int offset, int returnedLines,
                             String status, Integer exitCode, boolean waitingForInput, String error) {
        public boolean isError() { return error != null; }
        public static ProcessLog error(String msg) { return new ProcessLog("", 0, 0, 0, "error", null, false, msg); }
    }
    public record ProcessListResult(List<ProcessInfo> processes) {}
    public record ProcessInfo(String sessionId, String name, String status, Integer exitCode, long elapsedMs, boolean waitingForInput) {}

    // ── background process ──

    static class BackgroundProcess {
        private static final long INPUT_WAIT_IDLE_MS = 15_000L;
        private static final int MAX_BUFFER_SIZE = 512 * 1024;        // 保留最后 512KB
        private static final int TRUNCATE_THRESHOLD = 1024 * 1024;    // 超过 1MB 时触发截断

        final Process process;
        final String id;
        final String command;
        final String description;
        final long startTimeMs;
        private final ShellExecutor shellExecutor;
        private final ByteArrayOutputStream rawBuffer = new ByteArrayOutputStream();
        private final ConcurrentLinkedQueue<String> newOutputQueue = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean exited = new AtomicBoolean(false);
        private final AtomicReference<Integer> exitCode = new AtomicReference<>(null);
        private final AtomicReference<String> finalCwd = new AtomicReference<>();
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition newOutputSignal = lock.newCondition();
        private volatile long lastOutputTimeMs;
        private volatile boolean stdinClosed = false;
        volatile long completedAtMs = 0;
        private Thread readerThread;

        BackgroundProcess(String id, Process process, String command, String description, ShellExecutor shellExecutor) {
            this.id = id;
            this.process = process;
            this.command = command;
            this.description = description;
            this.shellExecutor = shellExecutor;
            this.startTimeMs = System.currentTimeMillis();
            this.lastOutputTimeMs = System.currentTimeMillis();
        }

        void start() {
            readerThread = new Thread(this::readLoop, "bg-proc-" + id);
            readerThread.setDaemon(true);
            readerThread.start();
        }

        private void readLoop() {
            try (InputStream stdout = process.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = stdout.read(buf)) != -1) {
                    synchronized (rawBuffer) {
                        rawBuffer.write(buf, 0, n);
                        // 超过阈值时截断，保留最后 MAX_BUFFER_SIZE 字节
                        if (rawBuffer.size() > TRUNCATE_THRESHOLD) {
                            byte[] all = rawBuffer.toByteArray();
                            rawBuffer.reset();
                            rawBuffer.write(all, all.length - MAX_BUFFER_SIZE, MAX_BUFFER_SIZE);
                            log.debug("[BackgroundProcess] buffer truncated to {} bytes", MAX_BUFFER_SIZE);
                        }
                    }
                    lastOutputTimeMs = System.currentTimeMillis();
                    decodeAndEnqueue();
                    signalNewOutput();

                    // 检测 marker — 命令执行完毕
                    String current = rawBuffer.toString(StandardCharsets.UTF_8);
                    if (current.contains(ShellExecutor.MARKER_PREFIX)) {
                        // 简单检测：如果 marker 出现，说明命令已完成
                        break;
                    }
                }
            } catch (IOException e) {
                log.debug("[BackgroundProcess] reader ended: {}", e.getMessage());
            }

            // 等待进程退出
            try {
                int code = process.waitFor();
                exitCode.set(code);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exitCode.set(-1);
            }

            // 尝试从输出中解析 marker
            String raw;
            synchronized (rawBuffer) { raw = rawBuffer.toString(StandardCharsets.UTF_8); }
            String decoded = EncodingHelper.decodeBest(rawBuffer.toByteArray());

            // 查找 marker 并解析
            String markerId = findMarkerId(decoded);
            if (markerId != null) {
                ShellExecutor.ParseResult r = shellExecutor.parseOutput(decoded, markerId, null, false);
                exitCode.set(r.exitCode());
                finalCwd.set(r.cwd());
            }

            exited.set(true);
            completedAtMs = System.currentTimeMillis();
            signalNewOutput();
            log.info("[BackgroundProcess] completed, exit={}", exitCode.get());
        }

        /** 从输出中查找 marker ID */
        private String findMarkerId(String output) {
            int idx = output.indexOf(ShellExecutor.MARKER_PREFIX);
            if (idx < 0) return null;
            int end = idx;
            while (end < output.length() && !isDelimiter(output.charAt(end))) {
                end++;
            }
            return output.substring(idx, end);
        }

        private boolean isDelimiter(char c) {
            return c == '^' || c == '|' || c == '\'' || c == '\n' || c == '\r';
        }

        /** 解码新字节并加入输出队列 */
        private void decodeAndEnqueue() {
            byte[] all;
            synchronized (rawBuffer) { all = rawBuffer.toByteArray(); }
            String current = EncodingHelper.decodeBest(all);
            // 找到 marker 之前的输出
            int markerIdx = current.indexOf(ShellExecutor.MARKER_PREFIX);
            String output = markerIdx >= 0 ? current.substring(0, markerIdx) : current;
            String cleaned = OutputSanitizer.clean(output);
            if (!cleaned.isEmpty()) {
                newOutputQueue.offer(cleaned);
            }
        }

        boolean awaitNewOutput(long timeoutMs) {
            if (exited.get() && newOutputQueue.isEmpty()) return false;
            long deadline = System.currentTimeMillis() + timeoutMs;
            lock.lock();
            try {
                while (newOutputQueue.isEmpty() && !exited.get()) {
                    long remain = deadline - System.currentTimeMillis();
                    if (remain <= 0) return false;
                    try { newOutputSignal.awaitNanos(remain * 1_000_000L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
                }
                return true;
            } finally { lock.unlock(); }
        }

        ProcessSnapshot snapshot() {
            StringBuilder sb = new StringBuilder();
            String chunk;
            while ((chunk = newOutputQueue.poll()) != null) sb.append(chunk);
            return new ProcessSnapshot(
                    isAlive() ? "running" : "completed",
                    sb.toString(),
                    exitCode.get(),
                    System.currentTimeMillis() - startTimeMs,
                    null);
        }

        String getFullOutput() {
            byte[] all;
            synchronized (rawBuffer) { all = rawBuffer.toByteArray(); }
            String decoded = EncodingHelper.decodeBest(all);
            String markerId = findMarkerId(decoded);
            if (markerId != null) {
                ShellExecutor.ParseResult r = shellExecutor.parseOutput(decoded, markerId, null, false);
                return OutputSanitizer.clean(r.output());
            }
            return OutputSanitizer.clean(decoded);
        }

        boolean isAlive() { return process.isAlive() && !exited.get(); }
        Integer exitCode() { return exitCode.get(); }

        boolean isLikelyWaitingForInput() {
            if (!isAlive() || stdinClosed) return false;
            return (System.currentTimeMillis() - lastOutputTimeMs) > INPUT_WAIT_IDLE_MS;
        }

        void destroy() {
            closeStdin();
            process.destroyForcibly();
            exited.set(true);
            completedAtMs = System.currentTimeMillis();
            signalNewOutput();
        }

        boolean sendInput(String input) {
            if (!isAlive() || stdinClosed) return false;
            try {
                OutputStream stdin = process.getOutputStream();
                stdin.write(input.getBytes(StandardCharsets.UTF_8));
                stdin.write('\n');
                stdin.flush();
                return true;
            } catch (IOException e) {
                log.warn("[BackgroundProcess] sendInput failed: {}", e.getMessage());
                return false;
            }
        }

        private void closeStdin() {
            if (stdinClosed) return;
            stdinClosed = true;
            try { process.getOutputStream().close(); } catch (IOException ignored) {}
        }

        private void signalNewOutput() {
            lock.lock();
            try { newOutputSignal.signalAll(); } finally { lock.unlock(); }
        }
    }
}
