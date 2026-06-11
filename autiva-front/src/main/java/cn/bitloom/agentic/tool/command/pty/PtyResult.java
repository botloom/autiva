package cn.bitloom.agentic.tool.command.pty;

/**
 * Result of a command executed through PTY.
 *
 * @param output  cleaned command output (without marker lines)
 * @param exitCode exit code of the command, or -1 if unknown
 * @param cwd     current working directory after command execution
 */
public record PtyResult(String output, int exitCode, String cwd) {

    public static PtyResult error(String message) {
        return new PtyResult(message, -1, "");
    }
}
