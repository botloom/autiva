package cn.bitloom.agentic.verify;

/**
 * 校验反馈：Grader 检查后返回的结果。
 * <p>
 * 不可变 record。passed=true 表示通过，false 表示有问题。
 * severity 表示严重程度，决定是否阻断当前流程。
 *
 * @param passed   是否通过校验
 * @param message  反馈消息（通过时可为 null；不通过时必须包含可读的失败原因）
 * @param score    评分 [0,1]，通过时 ≥0.6，不通过时 <0.6
 * @param severity 严重程度
 */
public record Feedback(boolean passed, String message, double score, Severity severity) {

    public enum Severity {
        /** 信息级，不阻断 */
        INFO,
        /** 警告级，记录但不阻断 */
        WARN,
        /** 错误级，阻断当前流程并触发重试 */
        ERROR,
        /** 致命级，立即终止 */
        FATAL
    }

    public static Feedback pass() {
        return new Feedback(true, null, 1.0, Severity.INFO);
    }

    public static Feedback pass(String message) {
        return new Feedback(true, message, 1.0, Severity.INFO);
    }

    public static Feedback fail(String message) {
        return new Feedback(false, message, 0.0, Severity.ERROR);
    }

    public static Feedback fail(String message, Severity severity) {
        return new Feedback(false, message, 0.0, severity);
    }

    public static Feedback fail(String message, double score, Severity severity) {
        return new Feedback(false, message, score, severity);
    }

    public boolean shouldBlock() {
        return !passed && (severity == Severity.ERROR || severity == Severity.FATAL);
    }
}
