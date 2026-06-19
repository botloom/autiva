package cn.bitloom.agentic.tool.command.shell;

import java.io.File;
import java.util.Map;

/**
 * Shell 执行器接口 — 封装平台相关的命令构建和进程创建逻辑。
 *
 * <p>灵感来源：
 * <ul>
 *   <li>AgentScope 的 ShellExecutorFactory 工厂模式</li>
 *   <li>Trae Agent 的分平台 _BashSession</li>
 *   <li>Spring AI JManus 的 ShellCommandExecutor 接口</li>
 * </ul>
 */
public interface ShellExecutor {

    /** Marker 前缀，用于捕获退出码和 cwd */
    String MARKER_PREFIX = "__CMD_MARK_";

    /**
     * 创建 ProcessBuilder 并配置好 shell、workdir、env。
     *
     * @param wrappedCommand 已通过 {@link #wrapCommand} 包装的命令
     * @param workdir 工作目录
     * @param env 环境变量（已过滤）
     * @return 配置好的 ProcessBuilder
     */
    ProcessBuilder createProcessBuilder(String wrappedCommand, String workdir, Map<String, String> env);

    /**
     * 包装用户命令：追加 cwd 设置、编码切换、退出码/cwd 捕获 marker。
     *
     * @param command 用户原始命令
     * @param workdir 工作目录
     * @return 包装后的完整命令字符串
     */
    String wrapCommand(String command, String workdir);

    /**
     * 从命令输出中解析 marker 行，提取 exitCode 和 cwd。
     *
     * @param rawOutput 原始输出（含 marker 行）
     * @param markerId 本次执行的 marker ID
     * @param fallbackCwd 解析失败时的回退 cwd
     * @param timedOut 是否超时
     * @return 解析结果
     */
    ParseResult parseOutput(String rawOutput, String markerId, String fallbackCwd, boolean timedOut);

    /**
     * 过滤环境变量（如 Windows 移除 WindowsApps）。
     * 默认不做任何过滤。
     */
    default Map<String, String> filterEnv(Map<String, String> env) {
        return env;
    }

    /** 平台名称，用于日志和工具描述 */
    String platformName();

    /** 是否 Windows 平台 */
    boolean isWindows();

    /**
     * 生成唯一的 marker ID。
     */
    default String generateMarkerId() {
        return MARKER_PREFIX + System.nanoTime();
    }

    /**
     * 工厂方法：根据当前平台自动选择 ShellExecutor 实现。
     */
    static ShellExecutor create() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win") ? new WindowsShellExecutor() : new UnixShellExecutor();
    }

    /**
     * 命令输出解析结果。
     */
    record ParseResult(String output, int exitCode, String cwd) {
        public static ParseResult error(String message) {
            return new ParseResult(message, -1, "");
        }
    }
}
