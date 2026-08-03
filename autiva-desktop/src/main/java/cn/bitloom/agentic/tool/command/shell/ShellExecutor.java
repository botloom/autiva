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
     * 创建 ProcessBuilder 并配置好 shell、workdir、env（一次性执行模式，供后台进程使用）。
     *
     * @param wrappedCommand 已通过 {@link #wrapCommand} 包装的命令
     * @param workdir 工作目录
     * @param env 环境变量（已过滤）
     * @return 配置好的 ProcessBuilder
     */
    ProcessBuilder createProcessBuilder(String wrappedCommand, String workdir, Map<String, String> env);

    /**
     * 包装用户命令（一次性执行模式）：追加 cwd 设置、编码切换、退出码/cwd 捕获 marker。
     * 供后台进程 {@link cn.bitloom.agentic.tool.command.CommandExecutor#startBackground} 使用。
     *
     * @param command 用户原始命令
     * @param workdir 工作目录
     * @return 包装后的完整命令字符串
     */
    String wrapCommand(String command, String workdir);

    /**
     * 创建持久 shell 进程的 ProcessBuilder（v13）。
     *
     * <p>启动一个长期存活的 shell（Windows: {@code cmd.exe /v:on /k}，Unix: {@code bash --noprofile --norc}），
     * 不带命令参数。后续命令通过 stdin 写入，由 {@link cn.bitloom.agentic.tool.command.PersistentShellSession} 管理。
     *
     * <p>注意：{@code redirectErrorStream=false}，stdout 与 stderr 分离。
     *
     * @param env 环境变量（已过滤），可为 null
     * @return 配置好的 ProcessBuilder（未启动）
     */
    ProcessBuilder createPersistentShellBuilder(Map<String, String> env);

    /**
     * 包装持久会话命令（v13）。
     *
     * <p>不含 cd 前缀（会话已维护 cwd），仅追加退出码/cwd 捕获 marker。
     * 写入持久 shell 的 stdin 后，读取 stdout 直到 marker 出现即视为命令完成。
     *
     * @param command 用户原始命令
     * @param markerId 本次执行的 marker ID（由 {@link #generateMarkerId()} 生成）
     * @return 包装后的命令字符串（不含换行，调用方负责追加换行）
     */
    String wrapPersistentCommand(String command, String markerId);

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
