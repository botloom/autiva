package cn.bitloom.agentic.tool.command;

import cn.bitloom.agentic.tool.command.shell.ShellExecutor;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 持久化 Shell 会话（v13）。
 *
 * <p>维护一个长期存活的 shell 进程（Windows: {@code cmd.exe /v:on /k}，Unix: {@code bash --noprofile --norc}），
 * 所有命令通过 stdin 写入同一进程，{@code cd}/{@code export}/{@code source}/shell 函数/后台任务天然持久。
 * 对标 Claude Code 的 BashTool 持久 PTY 会话，但采用纯 Java 管道（无原生库依赖）以避免 v11 Pty4J 不稳定问题。
 *
 * <p>stdout 与 stderr 分离（{@code redirectErrorStream=false}），命令完成通过 marker 行检测
 * （{@code __CMD_MARK_<nano>||<exitCode>||<cwd>}，复用 v12 已验证协议）。
 *
 * <p>线程安全：所有公开方法在 {@code executeLock} 上串行，匹配"所有发给智能体的事件串行处理"约束。
 * 超时/卡死时杀整个 shell + 重启，从 {@link ShellSession} 恢复 cwd（export 的 env 丢失，已知限制）。
 */
@Slf4j
public class PersistentShellSession implements Closeable {

    public static final long DEFAULT_TIMEOUT_MS = 120_000L;
    public static final long MAX_TIMEOUT_MS = 600_000L;
    public static final int MAX_OUTPUT_LINES = 30_000;
    public static final int MAX_COMMAND_LENGTH = 8000;

    private final ShellExecutor shellExecutor;
    private final ShellSession shellSession;
    private final String initialCwd;

    private final Object executeLock = new Object();

    private Process shell;
    private OutputStream stdin;
    private InputStream stdout;
    private InputStream stderr;
    private volatile boolean alive;

    public PersistentShellSession(ShellExecutor shellExecutor, ShellSession shellSession) {
        this(shellExecutor, shellSession, null);
    }

    public PersistentShellSession(ShellExecutor shellExecutor, ShellSession shellSession, String initialCwd) {
        this.shellExecutor = shellExecutor;
        this.shellSession = shellSession;
        this.initialCwd = initialCwd;
        log.info("[PersistentShell] constructed: platform={}, cwd={}, initialCwd={}",
                shellExecutor.platformName(), shellSession.getCwd(), initialCwd);
    }

    /** 启动持久 shell 进程 */
    public void start() {
        Map<String, String> env = shellExecutor.filterEnv(shellSession.mergedEnv(null));
        ProcessBuilder pb = shellExecutor.createPersistentShellBuilder(env);
        log.info("[PersistentShell] starting process: command={}, directory={}, envSize={}",
                pb.command(), pb.directory(), env != null ? env.size() : 0);
        try {
            shell = pb.start();
            stdin = shell.getOutputStream();
            stdout = shell.getInputStream();
            stderr = shell.getErrorStream();
            alive = true;
            log.info("[PersistentShell] started: pid={}, platform={}", shell.pid(), shellExecutor.platformName());
            // 启动后切到 initialCwd（code 模式下为项目路径）
            if (initialCwd != null && !initialCwd.isBlank()) {
                restoreCwd(initialCwd);
            }
        } catch (IOException e) {
            log.error("[PersistentShell] start failed", e);
            alive = false;
            throw new IllegalStateException("持久 Shell 启动失败: " + e.getMessage(), e);
        }
    }

    /** 进程死了则惰性重启 + 恢复 cwd */
    private void ensureAlive() {
        if (alive && shell != null && shell.isAlive()) {
            return;
        }
        log.info("[PersistentShell] ensureAlive: process dead (alive={}, shell={}), restarting...",
                alive, shell != null ? "pid=" + shell.pid() + ",isAlive=" + shell.isAlive() : "null");
        closeQuietly();
        start();
        restoreCwd();
    }

    /** 恢复 cwd（重启后调用）：优先 initialCwd，否则用 shellSession 持久化 cwd */
    private void restoreCwd() {
        String cwd = initialCwd != null ? initialCwd : shellSession.getCwd();
        restoreCwd(cwd);
    }

    /** 切换到指定 cwd（用于启动时切到项目路径） */
    private void restoreCwd(String cwd) {
        if (cwd == null || cwd.isEmpty()) {
            log.debug("[PersistentShell] restoreCwd: cwd is empty, skip");
            return;
        }
        String cdCmd = shellExecutor.isWindows()
                ? "cd /d \"" + cwd + "\""
                : "cd '" + cwd.replace("'", "'\\''") + "'";
        String markerId = shellExecutor.generateMarkerId();
        // cd 后跟 marker（换行分隔，与 wrapPersistentCommand 一致）
        String wrapped = shellExecutor.isWindows()
                ? "@echo off\r\n" + cdCmd + "\r\necho " + markerId
                : cdCmd + " ; echo '" + markerId + "'";
        log.debug("[PersistentShell] restoreCwd: writing='{}'", wrapped);
        try {
            stdin.write((wrapped + "\n").getBytes(StandardCharsets.UTF_8));
            stdin.flush();
            String drain = readUntilMarker(markerId, 5_000L);
            log.debug("[PersistentShell] restoreCwd: drained {} bytes, cwd restored to {}", drain.length(), cwd);
        } catch (IOException e) {
            log.warn("[PersistentShell] restoreCwd failed: {}", e.getMessage());
            alive = false;
        }
    }

    /**
     * 执行命令（前台，持久会话）。
     *
     * @param command     用户命令
     * @param timeoutMs   超时毫秒
     * @param reusePrompt true=跳过清理残留输出（对标 Claude Code reuse_prompt）
     * @return 执行结果（stdout/stderr 分离）
     */
    public CommandResult execute(String command, long timeoutMs, boolean reusePrompt) {
        synchronized (executeLock) {
            log.info("[PersistentShell] execute START: command='{}', timeoutMs={}, reusePrompt={}",
                    command, timeoutMs, reusePrompt);

            if (command != null && command.length() > MAX_COMMAND_LENGTH) {
                log.warn("[PersistentShell] command too long: {} chars", command.length());
                return CommandResult.error("命令过长 (" + command.length() + " 字符)，最大允许 "
                        + MAX_COMMAND_LENGTH + " 字符。请将命令拆分为多次执行。");
            }

            ensureAlive();

            long effectiveTimeout = Math.min(Math.max(timeoutMs, 1000L), MAX_TIMEOUT_MS);

            // 清理上条命令残留输出（脏状态），对标 Claude Code reuse_prompt=false
            if (!reusePrompt) {
                int drainedOut = drainStream(stdout);
                int drainedErr = drainStream(stderr);
                if (drainedOut > 0 || drainedErr > 0) {
                    log.debug("[PersistentShell] drained residual: stdout={}B, stderr={}B", drainedOut, drainedErr);
                }
            }

            String markerId = shellExecutor.generateMarkerId();
            String wrapped = shellExecutor.wrapPersistentCommand(command, markerId);

            try {
                stdin.write((wrapped + "\n").getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            } catch (IOException e) {
                log.error("[PersistentShell] stdin write failed", e);
                alive = false;
                return CommandResult.error("写入 shell stdin 失败: " + e.getMessage());
            }

            // 读 stdout 直到 marker（带超时），stderr 并行 drain
            ByteArrayOutputStream stdoutBuf = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrBuf = new ByteArrayOutputStream();
            CountDownLatch stdoutDone = new CountDownLatch(1);

            Thread stdoutReader = new Thread(() -> {
                try {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = stdout.read(buf)) != -1) {
                        synchronized (stdoutBuf) {
                            stdoutBuf.write(buf, 0, n);
                        }
                        String current = stdoutBuf.toString(StandardCharsets.UTF_8);
                        if (current.contains(markerId)) {
                            break;
                        }
                    }
                } catch (IOException e) {
                    log.warn("[PersistentShell] stdout reader IOException: {}", e.getMessage());
                } finally {
                    stdoutDone.countDown();
                }
            }, "pshell-stdout");
            stdoutReader.setDaemon(true);
            stdoutReader.start();

            Thread stderrReader = new Thread(() -> {
                try {
                    byte[] buf = new byte[8192];
                    while (stdoutDone.getCount() > 0) {
                        int avail = stderr.available();
                        if (avail > 0) {
                            int n = stderr.read(buf, 0, Math.min(avail, buf.length));
                            if (n > 0) {
                                synchronized (stderrBuf) {
                                    stderrBuf.write(buf, 0, n);
                                }
                            }
                        } else {
                            Thread.sleep(10);
                        }
                    }
                    // stdout 已完成，drain stderr 剩余
                    while (stderr.available() > 0) {
                        int n = stderr.read(buf);
                        if (n <= 0) {
                            break;
                        }
                        synchronized (stderrBuf) {
                            stderrBuf.write(buf, 0, n);
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    log.debug("[PersistentShell] stderr reader ended: {}", e.getMessage());
                }
            }, "pshell-stderr");
            stderrReader.setDaemon(true);
            stderrReader.start();

            boolean completed;
            try {
                completed = stdoutDone.await(effectiveTimeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[PersistentShell] execute interrupted");
                return CommandResult.error("命令执行被中断");
            }

            if (!completed) {
                log.warn("[PersistentShell] TIMED OUT after {}ms, marker not found. Killing shell.", effectiveTimeout);
                // 通知 stdout reader 退出（杀进程会关闭流，read 抛 IOException）
                killAndRestart();
            }

            // 等待 stderr reader 收尾
            try {
                stderrReader.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            String rawStdout = EncodingHelper.decodeBest(stdoutBuf.toByteArray());
            String rawStderr = EncodingHelper.decodeBest(stderrBuf.toByteArray());

            ShellExecutor.ParseResult parseResult = shellExecutor.parseOutput(
                    rawStdout, markerId, shellSession.getCwd(), !completed);

            if (parseResult.cwd() != null && !parseResult.cwd().isEmpty()) {
                shellSession.updateCwd(parseResult.cwd());
            }

            String cleanedStdout = OutputSanitizer.clean(parseResult.output());
            cleanedStdout = OutputSanitizer.truncateHeadTail(cleanedStdout, MAX_OUTPUT_LINES);
            String cleanedStderr = OutputSanitizer.clean(rawStderr);
            cleanedStderr = OutputSanitizer.truncateHeadTail(cleanedStderr, MAX_OUTPUT_LINES);

            log.info("[PersistentShell] execute END: command='{}', exitCode={}, stdoutLen={}, stderrLen={}",
                    command, parseResult.exitCode(), cleanedStdout.length(), cleanedStderr.length());

            return CommandResult.success(cleanedStdout, cleanedStderr, parseResult.exitCode(), !completed);
        }
    }

    /**
     * 阻塞读取 stdout 直到出现 marker，用于 cd 恢复等内部命令。
     * 返回 marker 之前的输出（丢弃）。超时后放弃。
     */
    private String readUntilMarker(String markerId, long timeoutMs) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        CountDownLatch done = new CountDownLatch(1);
        Thread reader = new Thread(() -> {
            try {
                byte[] b = new byte[8192];
                int n;
                while ((n = stdout.read(b)) != -1) {
                    synchronized (buf) {
                        buf.write(b, 0, n);
                    }
                    if (buf.toString(StandardCharsets.UTF_8).contains(markerId)) {
                        break;
                    }
                }
            } catch (IOException e) {
                log.debug("[PersistentShell] readUntilMarker ended: {}", e.getMessage());
            } finally {
                done.countDown();
            }
        }, "pshell-init");
        reader.setDaemon(true);
        reader.start();
        try {
            done.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    /** 非阻塞排空流中残留数据（丢弃），返回排空的字节数 */
    private int drainStream(InputStream is) {
        int total = 0;
        try {
            while (is.available() > 0) {
                int n = is.read(new byte[4096]);
                if (n <= 0) {
                    break;
                }
                total += n;
            }
        } catch (IOException e) {
            log.debug("[PersistentShell] drain error: {}", e.getMessage());
        }
        return total;
    }

    /** 杀进程并重启（超时/卡死时调用） */
    private void killAndRestart() {
        closeQuietly();
        start();
        restoreCwd();
    }

    /** 静默关闭（不抛异常） */
    private void closeQuietly() {
        alive = false;
        if (shell != null) {
            shell.destroyForcibly();
        }
        try {
            if (stdin != null) {
                stdin.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (stdout != null) {
                stdout.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (stderr != null) {
                stderr.close();
            }
        } catch (IOException ignored) {
        }
        shell = null;
        stdin = null;
        stdout = null;
        stderr = null;
    }

    @Override
    public void close() {
        synchronized (executeLock) {
            closeQuietly();
        }
    }

    public boolean isAlive() {
        return alive && shell != null && shell.isAlive();
    }
}
