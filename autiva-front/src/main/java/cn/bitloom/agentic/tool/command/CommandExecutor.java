package cn.bitloom.agentic.tool.command;

import cn.bitloom.agentic.tool.command.shell.Shell;
import cn.bitloom.agentic.tool.command.shell.ShellDetector;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CommandExecutor {

    public static final long DEFAULT_TIMEOUT_MS = 120_000L;
    public static final long MAX_TIMEOUT_MS = 600_000L;
    public static final long DEFAULT_YIELD_MS = 10_000L;
    public static final int MAX_OUTPUT_LINES = 30_000;
    public static final String PWD_MARK_PREFIX = "__PWD__";
    /** Maximum command length (Windows cmd line limit ~8191, Linux ~128KB) */
    public static final int MAX_COMMAND_LENGTH = 8000;

    private final Shell shell;
    private final ShellSession session;

    public CommandExecutor(Shell shell, ShellSession session) {
        this.shell = shell;
        this.session = session;
    }

    public CommandExecutor(ShellSession session) {
        this(ShellDetector.detect(), session);
    }

    public Shell getShell() {
        return shell;
    }

    public CommandResult execute(String command, long timeoutMs, String workdir, Map<String, String> env) {
        long effectiveTimeout = Math.min(Math.max(timeoutMs, 1000L), MAX_TIMEOUT_MS);
        String cwd = session.resolveWorkdir(workdir);
        Map<String, String> mergedEnv = session.mergedEnv(env);

        try {
            ProcessBuilder pb = shell.createProcessBuilder(command, cwd, mergedEnv);
            Process process = pb.start();
            process.getOutputStream().close();

            OutputReader outputReader = new OutputReader(process);
            outputReader.start();

            boolean completed = process.waitFor(effectiveTimeout, TimeUnit.MILLISECONDS);

            if (!completed) {
                log.warn("[CommandExecutor] command timed out after {}ms, killing process", effectiveTimeout);
                process.destroyForcibly();
                try {
                    process.waitFor(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            outputReader.await(5, TimeUnit.SECONDS);

            int exitCode;
            try {
                exitCode = process.exitValue();
            } catch (IllegalThreadStateException e) {
                exitCode = -1;
            }

            String rawOutput = outputReader.getOutput();
            log.info("[CommandExecutor] rawOutput length={}, first 200 chars: [{}]",
                    rawOutput.length(), rawOutput.length() > 200 ? rawOutput.substring(0, 200) : rawOutput);

            String cleaned = OutputSanitizer.clean(rawOutput);
            log.info("[CommandExecutor] after clean length={}, first 200 chars: [{}]",
                    cleaned.length(), cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned);
            cleaned = OutputSanitizer.truncate(cleaned, MAX_OUTPUT_LINES);
            session.updateFromOutput(cleaned);
            cleaned = stripPwdMarker(cleaned);
            log.info("[CommandExecutor] final output length={}", cleaned.length());

            return CommandResult.success(cleaned, exitCode, !completed);

        } catch (IOException e) {
            log.error("[CommandExecutor] failed to execute command", e);
            return CommandResult.error("Failed to execute command: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CommandResult.error("Command execution interrupted");
        }
    }

    public YieldResult executeWithYield(String command, long yieldMs, String workdir, Map<String, String> env) {
        long effectiveYield = yieldMs > 0 ? yieldMs : DEFAULT_YIELD_MS;
        String cwd = session.resolveWorkdir(workdir);
        Map<String, String> mergedEnv = session.mergedEnv(env);

        try {
            ProcessBuilder pb = shell.createProcessBuilder(command, cwd, mergedEnv);
            Process process = pb.start();

            OutputReader outputReader = new OutputReader(process);
            outputReader.start();

            boolean completed = process.waitFor(effectiveYield, TimeUnit.MILLISECONDS);

            if (completed) {
                try { process.getOutputStream().close(); } catch (IOException ignored) {}
                outputReader.await(5, TimeUnit.SECONDS);
                int exitCode;
                try {
                    exitCode = process.exitValue();
                } catch (IllegalThreadStateException e) {
                    exitCode = -1;
                }
                String rawOutput = outputReader.getOutput();
                String cleaned = OutputSanitizer.clean(rawOutput);
                cleaned = OutputSanitizer.truncate(cleaned, MAX_OUTPUT_LINES);
                session.updateFromOutput(cleaned);
                cleaned = stripPwdMarker(cleaned);
                return new YieldResult(true, cleaned, exitCode, null);
            } else {
                String partialOutput = outputReader.getOutputSoFar();
                String cleaned = OutputSanitizer.clean(partialOutput);
                session.updateFromOutput(cleaned);
                cleaned = stripPwdMarker(cleaned);
                return new YieldResult(false, cleaned, null, process);
            }

        } catch (IOException e) {
            log.error("[CommandExecutor] failed to execute command with yield", e);
            return new YieldResult(true, "Failed to execute command: " + e.getMessage(), -1, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new YieldResult(true, "Command execution interrupted", -1, null);
        }
    }

    Process startBackgroundProcess(String command, String workdir, Map<String, String> env) throws IOException {
        String cwd = session.resolveWorkdir(workdir);
        Map<String, String> mergedEnv = session.mergedEnv(env);
        ProcessBuilder pb = shell.createProcessBuilder(command, cwd, mergedEnv);
        return pb.start();
    }

    private String stripPwdMarker(String output) {
        if (output == null || output.isEmpty()) return output;
        // Linear scan instead of Stream API for better performance on large output
        StringBuilder sb = new StringBuilder(output.length());
        int i = 0;
        while (i < output.length()) {
            int nlIdx = output.indexOf('\n', i);
            if (nlIdx < 0) {
                // Last line
                if (!output.contains(PWD_MARK_PREFIX)) {
                    // Quick check: if the remaining part doesn't contain PWD, append all
                    sb.append(output, i, output.length());
                } else if (!output.substring(i).contains(PWD_MARK_PREFIX)) {
                    sb.append(output, i, output.length());
                }
                break;
            }
            String line = output.substring(i, nlIdx);
            if (!line.contains(PWD_MARK_PREFIX)) {
                sb.append(line).append('\n');
            }
            i = nlIdx + 1;
        }
        // Remove trailing newline if the original didn't end with one
        if (!output.endsWith("\n") && sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    record YieldResult(boolean completed, String output, Integer exitCode, Process backgroundProcess) {
        boolean needsBackground() {
            return !completed && backgroundProcess != null;
        }
    }

    static class OutputReader {
        private final Process process;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final CountDownLatch latch = new CountDownLatch(1);
        private Thread readerThread;

        OutputReader(Process process) {
            this.process = process;
        }

        void start() {
            readerThread = new Thread(this::readLoop, "cmd-output-reader");
            readerThread.setDaemon(true);
            readerThread.start();
        }

        private void readLoop() {
            try (var is = process.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    buffer.write(buf, 0, n);
                }
            } catch (IOException e) {
                log.debug("[OutputReader] readLoop IOException: {}", e.getMessage());
            } finally {
                latch.countDown();
            }
        }

        void await(long timeout, TimeUnit unit) throws InterruptedException {
            latch.await(timeout, unit);
            if (readerThread != null) {
                readerThread.join(unit.toMillis(timeout));
            }
        }

        String getOutput() {
            return EncodingHelper.decodeBest(buffer.toByteArray());
        }

        String getOutputSoFar() {
            return EncodingHelper.decodeBest(buffer.toByteArray());
        }
    }
}
