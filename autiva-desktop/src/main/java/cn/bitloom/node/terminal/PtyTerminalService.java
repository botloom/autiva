package cn.bitloom.node.terminal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * PTY 终端服务
 * 管理 PTY 会话的创建、查询和销毁
 */
@Slf4j
@Component
public class PtyTerminalService {

    private final ConcurrentMap<String, PtySession> sessions = new ConcurrentHashMap<>();

    /**
     * 创建新的 PTY 会话
     *
     * @param workingDir 工作目录（可为 null，使用用户主目录）
     * @return PTY 会话
     * @throws IOException 创建失败
     */
    public PtySession createSession(Path workingDir) throws IOException {
        PtySession session = new PtySession(workingDir);
        sessions.put(session.getSessionId(), session);
        log.info("创建 PTY 会话: {}", session.getSessionId());
        return session;
    }

    /**
     * 获取会话
     */
    public PtySession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 发送输入
     */
    public void writeInput(String sessionId, String input) throws IOException {
        PtySession session = sessions.get(sessionId);
        if (session != null) {
            session.writeInput(input);
        }
    }

    /**
     * 关闭会话
     */
    public void closeSession(String sessionId) {
        PtySession session = sessions.remove(sessionId);
        if (session != null) {
            session.close();
            log.info("关闭 PTY 会话: {}", sessionId);
        }
    }

    /**
     * 关闭所有会话（应用退出时调用）
     */
    public void closeAllSessions() {
        sessions.forEach((id, session) -> {
            try {
                session.close();
            } catch (Exception e) {
                log.warn("关闭 PTY 会话异常: {}", id, e);
            }
        });
        sessions.clear();
    }

    /**
     * 获取活跃会话数量
     */
    public int getActiveSessionCount() {
        return (int) sessions.values().stream().filter(PtySession::isAlive).count();
    }
}
