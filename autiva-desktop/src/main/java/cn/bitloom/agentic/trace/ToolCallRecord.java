package cn.bitloom.agentic.trace;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 单次工具调用记录。
 *
 * @param toolName    工具名称
 * @param arguments   输入参数（JSON 字符串）
 * @param result      返回结果（JSON 字符串）
 * @param durationMs  耗时（毫秒）
 * @param success     是否成功（参数校验通过且结果未被 L2 改写为错误）
 * @param blocked     是否被 beforeToolCall 阻断
 * @param feedbackMsg 校验反馈消息（无反馈时为 null）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolCallRecord(
        String toolName,
        String arguments,
        String result,
        long durationMs,
        boolean success,
        boolean blocked,
        String feedbackMsg
) {
    public static ToolCallRecord success(String toolName, String arguments, String result, long durationMs) {
        return new ToolCallRecord(toolName, arguments, result, durationMs, true, false, null);
    }

    public static ToolCallRecord blocked(String toolName, String arguments, String reason, long durationMs) {
        return new ToolCallRecord(toolName, arguments, null, durationMs, false, true, reason);
    }

    public static ToolCallRecord failed(String toolName, String arguments, String result, long durationMs, String feedback) {
        return new ToolCallRecord(toolName, arguments, result, durationMs, false, false, feedback);
    }
}
