package cn.bitloom.agentic.tool.command;

import cn.bitloom.agentic.tool.command.shell.ShellExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 持久化 Shell 会话注册表（v13）。
 *
 * <p>按 sessionId 隔离 {@link PersistentShellSession}，惰性创建。持久 shell 进程不能跨会话共享
 * （命令会交错），必须每个会话独占一个 shell 进程。
 *
 * <p>cwd 持久化沿用 {@link ShellSession} 全局共享设计（既有行为，本次不改动作用域）。
 *
 * <p>会话关闭时应调用 {@link #close(String)} 清理对应 shell 进程，避免泄漏。
 */
@Slf4j
@Component
public class PersistentShellRegistry {

    private final ConcurrentMap<String, PersistentShellSession> sessions = new ConcurrentHashMap<>();
    private final ShellExecutor shellExecutor;
    private final ShellSession sharedShellSession;

    public PersistentShellRegistry() {
        this.shellExecutor = ShellExecutor.create();
        this.sharedShellSession = new ShellSession();
    }

    /**
     * 获取全局共享的 {@link ShellSession}（cwd 持久化）。
     *
     * <p>供 {@link CommandExecutor} 等需要读取当前 cwd 的组件复用，确保后台命令与持久会话使用同一份 cwd 状态。
     */
    public ShellSession getSharedShellSession() {
        return sharedShellSession;
    }

    /**
     * 获取或创建指定会话的持久 shell。
     *
     * <p>首次创建时若 projectPath 非空，shell 启动后会自动 cd 到 projectPath，
     * 让 LLM 在 code 模式下执行的命令直接落在项目根目录。已存在的 session 不受 projectPath 影响
     * （sessionId 已编码 projectName，同 session 的 projectPath 稳定）。
     *
     * @param sessionId   会话 ID（从 ToolContext 提取）
     * @param projectPath 项目路径（code 模式下作为 shell 的初始 cwd；null 时回退到全局共享 cwd）
     * @return 该会话独占的 PersistentShellSession
     */
    public PersistentShellSession getOrCreate(String sessionId, String projectPath) {
        if (sessionId == null || sessionId.isEmpty()) {
            // 无 sessionId 时退化为单例（如测试场景），避免 NPE
            sessionId = "__default__";
        }
        final String effectiveProjectPath = projectPath;
        return sessions.computeIfAbsent(sessionId, id -> {
            PersistentShellSession s = new PersistentShellSession(shellExecutor, sharedShellSession, effectiveProjectPath);
            try {
                s.start();
                log.info("[PersistentShellRegistry] created shell for session {}, initialCwd={}", id, effectiveProjectPath);
            } catch (Exception e) {
                log.error("[PersistentShellRegistry] failed to start shell for session {}", id, e);
                throw e;
            }
            return s;
        });
    }

    /** 关闭并移除指定会话的 shell（会话结束时调用） */
    public void close(String sessionId) {
        if (sessionId == null) {
            return;
        }
        PersistentShellSession s = sessions.remove(sessionId);
        if (s != null) {
            s.close();
            log.info("[PersistentShellRegistry] closed shell for session {}", sessionId);
        }
    }

    /** 关闭所有会话的 shell（应用关闭时调用） */
    public void closeAll() {
        sessions.forEach((id, s) -> {
            try {
                s.close();
            } catch (Exception e) {
                log.warn("[PersistentShellRegistry] error closing shell for session {}", id, e);
            }
        });
        sessions.clear();
    }
}
