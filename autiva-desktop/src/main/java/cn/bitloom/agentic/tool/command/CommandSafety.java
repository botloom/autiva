package cn.bitloom.agentic.tool.command;

import cn.bitloom.agentic.tool.command.shell.CommandValidator;

/**
 * 命令安全检测 — 委托给平台对应的 {@link CommandValidator}。
 *
 * <p>保留原有 {@link SafetyCheck} API 不变（向后兼容），
 * 内部委托给 {@link CommandValidator#create()} 自动选择的平台验证器。
 */
public final class CommandSafety {

    private static final CommandValidator VALIDATOR = CommandValidator.create();

    private CommandSafety() {
    }

    public static SafetyCheck check(String command) {
        if (command == null || command.isBlank()) {
            return SafetyCheck.safe();
        }
        CommandValidator.ValidationResult result = VALIDATOR.validate(command);
        return switch (result.level()) {
            case SAFE -> SafetyCheck.safe();
            case WARNING -> SafetyCheck.warning(result.rule(), result.pattern());
            case DESTRUCTIVE -> SafetyCheck.destructive(result.pattern());
        };
    }

    /**
     * 直接访问底层 CommandValidator。
     */
    public static CommandValidator getValidator() {
        return VALIDATOR;
    }

    public record SafetyCheck(SafetyLevel level, String rule, String pattern) {
        public boolean isSafe() {
            return level == SafetyLevel.SAFE;
        }

        public boolean isDestructive() {
            return level == SafetyLevel.DESTRUCTIVE;
        }

        public boolean isWarning() {
            return level == SafetyLevel.WARNING;
        }

        public static SafetyCheck safe() {
            return new SafetyCheck(SafetyLevel.SAFE, null, null);
        }

        public static SafetyCheck destructive(String pattern) {
            return new SafetyCheck(SafetyLevel.DESTRUCTIVE, "destructive-command", pattern);
        }

        public static SafetyCheck warning(String rule, String pattern) {
            return new SafetyCheck(SafetyLevel.WARNING, rule, pattern);
        }
    }

    public enum SafetyLevel {
        SAFE, WARNING, DESTRUCTIVE
    }
}
