package cn.bitloom.agentic.hook;

/**
 * 工具调用前 Hook 的决策结果。
 * <p>
 * proceed=true 时使用 input（可能已修改）继续执行工具；
 * proceed=false 时阻止工具执行，将 blockReason 作为错误结果返回给 LLM。
 *
 * @param proceed     是否继续执行工具
 * @param input       修改后的输入参数（proceed=true 时有效，null 表示不修改原 input）
 * @param blockReason 阻止原因（proceed=false 时有效）
 */
public record ToolCallDecision(boolean proceed, String input, String blockReason) {

    /** 继续执行，使用原始或修改后的输入 */
    public static ToolCallDecision proceed(String input) {
        return new ToolCallDecision(true, input, null);
    }

    /** 阻止工具执行，返回错误结果给 LLM */
    public static ToolCallDecision block(String reason) {
        return new ToolCallDecision(false, null, reason);
    }
}
