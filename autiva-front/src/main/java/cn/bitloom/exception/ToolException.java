package cn.bitloom.exception;

public class ToolException extends AutivaException {

    public ToolException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ToolException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public ToolException(String errorCode, String message, boolean recoverable) {
        super(errorCode, message, recoverable);
    }

    public ToolException(String errorCode, String message, Throwable cause, boolean recoverable) {
        super(errorCode, message, cause, recoverable);
    }

    public static ToolException validationError(String detail) {
        return new ToolException("TOOL_VALIDATION_ERROR", detail);
    }

    public static ToolException executionError(String toolName, Throwable cause) {
        return new ToolException("TOOL_EXECUTION_ERROR", "工具执行失败: " + toolName, cause);
    }

    public static ToolException executionError(String toolName, String detail) {
        return new ToolException("TOOL_EXECUTION_ERROR", "工具执行失败: " + toolName + " - " + detail);
    }

    public static ToolException notFound(String toolName) {
        return new ToolException("TOOL_NOT_FOUND", "工具不存在: " + toolName);
    }

    public static ToolException bypass(String toolName, String reason) {
        return new ToolException("TOOL_BYPASS", "工具被绕过: " + toolName + " - " + reason);
    }
}
