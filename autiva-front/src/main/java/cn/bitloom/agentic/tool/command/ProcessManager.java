package cn.bitloom.agentic.tool.command;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

@Slf4j
public class ProcessManager {

    private static final long DEFAULT_BG_TIMEOUT_MS = 600_000L;
    private static final long CLEANUP_INTERVAL_MS = 300_000L;
    private static final long COMPLETED_TTL_MS = 60_000L;

    private final Map<String, BackgroundProcess> processes = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(0);

    public ProcessManager() {
        Thread cleanupThread = new Thread(this::cleanupLoop, "bg-process-cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    public String register(Process process, String command, String description) {
        String id = "proc_" + idSeq.incrementAndGet();
        BackgroundProcess bg = new BackgroundProcess(id, process, command, description);
        processes.put(id, bg);
        bg.start();
        log.info("[ProcessManager] registered {} cmd={}", id, command);
        return id;
    }

    public ProcessSnapshot poll(String id, long waitMs) {
        BackgroundProcess bg = processes.get(id);
        if (bg == null) return ProcessSnapshot.error("Unknown session_id: " + id);
        if (waitMs > 0) bg.awaitNewOutput(waitMs);
        String output = bg.consumeNewOutput();
        boolean alive = bg.isAlive();
        Integer exitCode = bg.exitCode();
        long elapsed = System.currentTimeMillis() - bg.startTimeMs;
        return new ProcessSnapshot(
                alive ? "running" : "completed",
                output,
                exitCode,
                alive ? elapsed : null,
                null
        );
    }

    public ProcessLog log(String id, int offset, int limit) {
        BackgroundProcess bg = processes.get(id);
        if (bg == null) return ProcessLog.error("Unknown session_id: " + id);
        String fullOutput = bg.getFullOutput();
        String[] lines = fullOutput.split("\n", -1);
        int totalLines = lines.length;
        int effectiveOffset = Math.max(0, offset);
        int effectiveLimit = limit > 0 ? limit : 200;
        List<String> resultLines = new ArrayList<>();
        for (int i = effectiveOffset; i < Math.min(effectiveOffset + effectiveLimit, totalLines); i++) {
            resultLines.add(lines[i]);
        }
        boolean waitingForInput = bg.isLikelyWaitingForInput();
        return new ProcessLog(
                String.join("\n", resultLines),
                totalLines,
                effectiveOffset,
                resultLines.size(),
                bg.isAlive() ? "running" : "completed",
                bg.exitCode(),
                waitingForInput,
                null
        );
    }

    public ProcessListResult list() {
        List<ProcessInfo> infos = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, BackgroundProcess> entry : processes.entrySet()) {
            BackgroundProcess bg = entry.getValue();
            String name = deriveName(bg.command);
            infos.add(new ProcessInfo(
                    entry.getKey(),
                    name,
                    bg.isAlive() ? "running" : "completed",
                    bg.exitCode(),
                    now - bg.startTimeMs,
                    bg.isLikelyWaitingForInput()
            ));
        }
        return new ProcessListResult(infos);
    }

    public boolean write(String id, String data) {
        BackgroundProcess bg = processes.get(id);
        if (bg == null) return false;
        return bg.sendInput(data);
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

    private String deriveName(String command) {
        if (command == null || command.isBlank()) return "unknown";
        String trimmed = command.trim();
        int spaceIdx = trimmed.indexOf(' ');
        String verb = spaceIdx > 0 ? trimmed.substring(0, spaceIdx) : trimmed;
        int lastSlash = verb.lastIndexOf('/');
        int lastBackslash = verb.lastIndexOf('\\');
        int cut = Math.max(lastSlash, lastBackslash);
        if (cut >= 0) verb = verb.substring(cut + 1);
        return verb.length() > 20 ? verb.substring(0, 20) : verb;
    }

    private void cleanupLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(CLEANUP_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            long now = System.currentTimeMillis();
            processes.entrySet().removeIf(entry -> {
                BackgroundProcess bg = entry.getValue();
                if (!bg.isAlive() && (now - bg.completedAtMs > COMPLETED_TTL_MS)) {
                    log.debug("[ProcessManager] cleaning up completed process: {}", entry.getKey());
                    return true;
                }
                if (bg.isAlive() && (now - bg.startTimeMs > DEFAULT_BG_TIMEOUT_MS)) {
                    log.warn("[ProcessManager] killing timed-out background process: {}", entry.getKey());
                    bg.destroy();
                    return true;
                }
                return false;
            });
        }
    }

    public record ProcessSnapshot(
            String status, String output, Integer exitCode,
            Long elapsedMs, String error
    ) {
        public boolean isError() { return error != null; }
        public static ProcessSnapshot error(String msg) {
            return new ProcessSnapshot("error", "", null, null, msg);
        }
    }

    public record ProcessLog(
            String output, int totalLines, int offset, int returnedLines,
            String status, Integer exitCode, boolean waitingForInput, String error
    ) {
        public boolean isError() { return error != null; }
        public static ProcessLog error(String msg) {
            return new ProcessLog("", 0, 0, 0, "error", null, false, msg);
        }
    }

    public record ProcessListResult(List<ProcessInfo> processes) {}

    public record ProcessInfo(
            String sessionId, String name, String status,
            Integer exitCode, long elapsedMs, boolean waitingForInput
    ) {}

    static class BackgroundProcess {

        private static final long INPUT_WAIT_IDLE_MS = 15_000L;

        private final String id;
        private final String command;
        private final String description;
        private final Process process;
        private final OutputStream processStdin;
        final long startTimeMs;
        volatile long completedAtMs = 0;

        // Raw byte buffer for output
        private final ByteArrayOutputStream rawBuffer = new ByteArrayOutputStream();
        // Incremental output tracking: each entry is a chunk of cleaned text
        private final ConcurrentLinkedQueue<String> newOutputQueue = new ConcurrentLinkedQueue<>();
        // Track the length of cleaned text already emitted (for incremental diff)
        private int lastCleanedLength = 0;
        // Track how many raw bytes have been decoded (for incremental decode)
        private int lastDecodedOffset = 0;
        // Whether CLIXML has been detected in previous chunks
        private boolean clixmlDetected = false;
        private final AtomicBoolean exited = new AtomicBoolean(false);
        private final AtomicReference<Integer> exitCode = new AtomicReference<>(null);
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition newOutputSignal = lock.newCondition();
        private volatile long lastOutputTimeMs = System.currentTimeMillis();
        private volatile boolean stdinClosed = false;
        private Thread readerThread;

        BackgroundProcess(String id, Process process, String command, String description) {
            this.id = id;
            this.process = process;
            this.processStdin = process.getOutputStream();
            this.command = command;
            this.description = description;
            this.startTimeMs = System.currentTimeMillis();
        }

        void start() {
            readerThread = new Thread(this::readLoop, "bg-proc-" + id);
            readerThread.setDaemon(true);
            readerThread.start();
        }

        private void readLoop() {
            try (var is = process.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    synchronized (rawBuffer) {
                        rawBuffer.write(buf, 0, n);
                    }
                    lastOutputTimeMs = System.currentTimeMillis();
                    decodeAndEnqueueNewOutput();
                    signalNewOutput();
                }
            } catch (IOException e) {
                log.debug("[BackgroundProcess:{}] readLoop IOException: {}", id, e.getMessage());
            } finally {
                // Final decode of any remaining bytes
                decodeAndEnqueueNewOutput();
                try {
                    int ec = process.waitFor();
                    exitCode.set(ec);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                exited.set(true);
                completedAtMs = System.currentTimeMillis();
                signalNewOutput();
                closeStdin();
                log.info("[BackgroundProcess:{}] exited with code {}", id, exitCode.get());
            }
        }

        /**
         * Incrementally decode new bytes and enqueue the cleaned output diff.
         *
         * Optimization: instead of re-decoding and re-cleaning all bytes from the beginning
         * on every chunk (O(n²)), we only decode and clean the new bytes since lastDecodedOffset.
         * For CLIXML detection, we check the first chunk and set a flag; subsequent chunks
         * are treated as continuation text. The full re-clean is deferred to getFullOutput().
         */
        private void decodeAndEnqueueNewOutput() {
            byte[] allBytes;
            synchronized (rawBuffer) {
                allBytes = rawBuffer.toByteArray();
            }
            if (allBytes.length <= lastDecodedOffset) return;

            // Decode only the new bytes
            byte[] newBytes = new byte[allBytes.length - lastDecodedOffset];
            System.arraycopy(allBytes, lastDecodedOffset, newBytes, 0, newBytes.length);
            String newDecoded = EncodingHelper.decodeBest(newBytes);

            // Check for CLIXML in the first chunk
            if (!clixmlDetected && newDecoded.contains("#< CLIXML")) {
                clixmlDetected = true;
            }

            // For incremental output, we do a lightweight clean on the new chunk
            // (full CLIXML extraction requires the complete buffer, so we defer that)
            String newCleaned;
            if (clixmlDetected) {
                // CLIXML output needs full re-clean for proper extraction
                // This is still more efficient than the old approach because
                // we only do full re-clean when CLIXML is detected
                String fullDecoded = EncodingHelper.decodeBest(allBytes);
                String fullCleaned = OutputSanitizer.clean(fullDecoded);
                if (fullCleaned.length() > lastCleanedLength) {
                    newCleaned = fullCleaned.substring(lastCleanedLength);
                    lastCleanedLength = fullCleaned.length();
                } else {
                    newCleaned = null;
                }
            } else {
                // No CLIXML: lightweight incremental clean
                newCleaned = OutputSanitizer.clean(newDecoded);
                lastCleanedLength += newCleaned.length();
            }

            lastDecodedOffset = allBytes.length;

            if (newCleaned != null && !newCleaned.isEmpty()) {
                newOutputQueue.offer(newCleaned);
            }
        }

        boolean awaitNewOutput(long timeoutMs) {
            if (exited.get() && newOutputQueue.isEmpty()) return false;
            long deadline = System.currentTimeMillis() + timeoutMs;
            lock.lock();
            try {
                while (newOutputQueue.isEmpty() && !exited.get()) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) return false;
                    try {
                        newOutputSignal.awaitNanos(remaining * 1_000_000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
                return true;
            } finally {
                lock.unlock();
            }
        }

        String consumeNewOutput() {
            StringBuilder sb = new StringBuilder();
            String chunk;
            while ((chunk = newOutputQueue.poll()) != null) sb.append(chunk);
            return sb.toString();
        }

        String getFullOutput() {
            byte[] allBytes;
            synchronized (rawBuffer) {
                allBytes = rawBuffer.toByteArray();
            }
            String decoded = EncodingHelper.decodeBest(allBytes);
            return OutputSanitizer.clean(decoded);
        }

        boolean isAlive() {
            return process.isAlive() && !exited.get();
        }

        Integer exitCode() {
            return exitCode.get();
        }

        boolean isLikelyWaitingForInput() {
            if (!isAlive()) return false;
            if (stdinClosed) return false;
            return (System.currentTimeMillis() - lastOutputTimeMs) > INPUT_WAIT_IDLE_MS;
        }

        void destroy() {
            closeStdin();
            if (process.isAlive()) process.destroyForcibly();
            exited.set(true);
            completedAtMs = System.currentTimeMillis();
            signalNewOutput();
        }

        boolean sendInput(String input) {
            if (!process.isAlive()) return false;
            if (stdinClosed) return false;
            try {
                processStdin.write(input.getBytes(StandardCharsets.UTF_8));
                processStdin.write('\n');
                processStdin.flush();
                return true;
            } catch (IOException e) {
                log.warn("[BackgroundProcess:{}] sendInput failed: {}", id, e.getMessage());
                return false;
            }
        }

        private void closeStdin() {
            if (stdinClosed) return;
            stdinClosed = true;
            try {
                processStdin.close();
            } catch (IOException ignored) {}
        }

        private void signalNewOutput() {
            lock.lock();
            try { newOutputSignal.signalAll(); } finally { lock.unlock(); }
        }
    }
}
