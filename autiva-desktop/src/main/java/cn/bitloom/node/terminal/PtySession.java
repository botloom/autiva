package cn.bitloom.node.terminal;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PTY 会话封装
 * 管理 Pty4J 的 PtyProcess 生命周期
 */
@Slf4j
public class PtySession {

    private final String sessionId;
    private final PtyProcess ptyProcess;
    private final OutputStream stdin;
    private final InputStream stdout;
    private final InputStream stderr;
    private volatile boolean closed = false;

    public PtySession(Path workingDir) throws IOException {
        this.sessionId = UUID.randomUUID().toString();

        Map<String, String> env = new HashMap<>(System.getenv());
        String[] command = buildShellCommand();

        PtyProcessBuilder builder = new PtyProcessBuilder()
                .setCommand(command)
                .setEnvironment(env)
                .setRedirectErrorStream(true);

        if (workingDir != null) {
            builder.setDirectory(workingDir.toString());
        }

        this.ptyProcess = builder.start();
        this.stdin = ptyProcess.getOutputStream();
        this.stdout = ptyProcess.getInputStream();
        this.stderr = ptyProcess.getErrorStream();

        log.info("PTY 会话已创建: {}, 工作目录: {}", sessionId, workingDir);
    }

    /**
     * 根据操作系统构建 Shell 命令
     */
    private String[] buildShellCommand() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            // Windows 使用 PowerShell
            return new String[]{"powershell.exe", "-NoLogo"};
        } else if (osName.contains("mac")) {
            // macOS 使用 zsh
            return new String[]{"/bin/zsh", "-l"};
        } else {
            // Linux 使用 bash
            return new String[]{"/bin/bash", "-l"};
        }
    }

    /**
     * 发送输入到 PTY
     */
    public void writeInput(String input) throws IOException {
        if (closed) {
            return;
        }
        stdin.write(input.getBytes());
        stdin.flush();
    }

    /**
     * 读取输出（非阻塞，返回可用字节）
     */
    public byte[] readOutput() throws IOException {
        if (closed) {
            return new byte[0];
        }
        int available = stdout.available();
        if (available <= 0) {
            return new byte[0];
        }
        byte[] buffer = new byte[available];
        int read = stdout.read(buffer);
        if (read > 0) {
            byte[] result = new byte[read];
            System.arraycopy(buffer, 0, result, 0, read);
            return result;
        }
        return new byte[0];
    }

    /**
     * 获取标准输出流（阻塞读取）
     */
    public InputStream getInputStream() {
        return stdout;
    }

    /**
     * 获取会话ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 检查进程是否存活
     */
    public boolean isAlive() {
        return !closed && ptyProcess.isAlive();
    }

    /**
     * 获取退出码
     */
    public int getExitCode() {
        return ptyProcess.exitValue();
    }

    /**
     * 关闭 PTY 会话
     */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (ptyProcess.isAlive()) {
                ptyProcess.destroy();
                // 等待进程退出（3秒超时）
                long deadline = System.currentTimeMillis() + 3000;
                while (ptyProcess.isAlive() && System.currentTimeMillis() < deadline) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                if (ptyProcess.isAlive()) {
                    ptyProcess.destroyForcibly();
                }
            }
        } catch (Exception e) {
            log.warn("关闭 PTY 会话异常: {}", sessionId, e);
        }
        log.info("PTY 会话已关闭: {}", sessionId);
    }

    /**
     * 调整终端窗口大小
     */
    public void setWinSize(int cols, int rows) {
        if (closed) {
            return;
        }
        try {
            ptyProcess.setWinSize(new com.pty4j.WinSize(cols, rows));
        } catch (Exception e) {
            log.warn("设置终端窗口大小失败: {}x{}", cols, rows, e);
        }
    }
}
