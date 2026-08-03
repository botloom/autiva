package cn.bitloom.agentic.tool.command;

/**
 * 命令执行结果。
 *
 * <p>stdout 与 stderr 分离（v13 持久化 Shell 重构），便于 LLM 区分正常输出与错误输出。
 */
public record CommandResult(String output, String stderr, Integer exitCode,
                            boolean timedOut, boolean error, String errorMessage) {

    public static CommandResult error(String message) {
        return new CommandResult("", "", null, false, true, message);
    }

    public static CommandResult success(String output, String stderr, int exitCode, boolean timedOut) {
        return new CommandResult(output, stderr, exitCode, timedOut, false, null);
    }
}
