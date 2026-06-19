package cn.bitloom.agentic.tool.command.shell;

/**
 * 命令验证器接口 — 按平台检测命令安全性。
 *
 * <p>灵感来源：AgentScope 的 CommandValidator + UnixCommandValidator / WindowsCommandValidator。
 */
public interface CommandValidator {

    /**
     * 验证命令安全性。
     *
     * @param command 要验证的命令
     * @return 验证结果
     */
    ValidationResult validate(String command);

    /**
     * 验证结果。
     */
    record ValidationResult(SafetyLevel level, String rule, String pattern) {
        public boolean isSafe() {
            return level == SafetyLevel.SAFE;
        }

        public boolean isDestructive() {
            return level == SafetyLevel.DESTRUCTIVE;
        }

        public boolean isWarning() {
            return level == SafetyLevel.WARNING;
        }

        public static ValidationResult safe() {
            return new ValidationResult(SafetyLevel.SAFE, null, null);
        }

        public static ValidationResult destructive(String rule, String pattern) {
            return new ValidationResult(SafetyLevel.DESTRUCTIVE, rule, pattern);
        }

        public static ValidationResult warning(String rule, String pattern) {
            return new ValidationResult(SafetyLevel.WARNING, rule, pattern);
        }
    }

    /**
     * 安全等级。
     */
    enum SafetyLevel {
        SAFE, WARNING, DESTRUCTIVE
    }

    /**
     * 工厂方法：根据当前平台自动选择 CommandValidator 实现。
     */
    static CommandValidator create() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win") ? new WindowsCommandValidator() : new UnixCommandValidator();
    }
}
