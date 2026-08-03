package cn.bitloom.agentic.tool.command;

import cn.bitloom.agentic.tool.command.shell.ShellExecutor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * 后台命令启动器（v13）。
 *
 * <p>v12 的前台执行逻辑（execute / executeWithYield）已迁移到 {@link PersistentShellSession}（持久化 Shell 会话）。
 * 本类仅保留 {@link #startBackground}，用于 {@link CommandTool} 的 {@code run_in_background=true} 分支：
 * 启动一个独立 detached 一次性进程（{@code cmd.exe /v:on /c} 或 {@code bash -c}），注册到 {@link ProcessManager} 管理。
 *
 * <p>后台进程不经过持久会话（否则会阻塞会话），cwd/env 通过 {@link ShellSession} 解析 + 命令前缀注入。
 */
@Slf4j
public class CommandExecutor {

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

    /**
     * 启动后台进程，返回 Process 句柄供 {@link ProcessManager} 管理。
     *
     * @param command 用户命令
     * @param workdir 工作目录覆盖（null 时用持久化 cwd）
     * @param env     环境变量覆盖（null 时用持久化 env）
     * @return 已启动的 Process
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
}
