package cn.bitloom.agentic.tool.command;

public record CommandResult(String output, Integer exitCode, boolean timedOut, boolean error, String errorMessage) {

    public static CommandResult error(String message) {
        return new CommandResult("", null, false, true, message);
    }

    public static CommandResult success(String output, int exitCode, boolean timedOut) {
        return new CommandResult(output, exitCode, timedOut, false, null);
    }
}
