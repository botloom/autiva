package cn.bitloom.agentic.tool.command;

import java.util.List;
import java.util.regex.Pattern;

public final class CommandSafety {

    private static final List<Pattern> DESTRUCTIVE_PATTERNS = List.of(
            // Unix/Bash patterns
            Pattern.compile("\\brm\\s+(-[a-zA-Z]*f[a-zA-Z]*\\s+|.*--no-preserve-root)"),
            Pattern.compile("\\bdd\\s+.*/dev/(sd|nvme|vd|hd)"),
            Pattern.compile("\\bmkfs\\."),
            Pattern.compile("\\bformat\\s+[A-Z]:"),
            Pattern.compile("\\bshutdown\\b"),
            Pattern.compile("\\breboot\\b"),
            Pattern.compile("\\binit\\s+[06]"),
            Pattern.compile(">\\s*/dev/sd"),
            Pattern.compile("\\bsudo\\s+rm\\s+"),
            Pattern.compile("\\brmdir\\s+/\\s*$"),
            Pattern.compile("\\bchmod\\s+(-R\\s+)?000\\s+/"),
            Pattern.compile("\\bchown\\s+.*\\s+/\\s*$"),
            Pattern.compile("\\bcurl\\s+.*\\|\\s*(ba)?sh"),
            Pattern.compile("\\bwget\\s+.*\\|\\s*(ba)?sh"),
            // PowerShell patterns
            Pattern.compile("(?i)\\bRemove-Item\\s+.*-Recurse.*-Force"),
            Pattern.compile("(?i)\\bStop-Computer\\b"),
            Pattern.compile("(?i)\\bRestart-Computer\\b"),
            Pattern.compile("(?i)\\bSet-ExecutionPolicy\\s+Unrestricted"),
            Pattern.compile("(?i)\\bInvoke-WebRequest.*\\|\\s*Invoke-Expression"),
            Pattern.compile("(?i)\\biwr.*\\|\\s*iex"),
            Pattern.compile("(?i)\\bFormat-Volume\\b"),
            Pattern.compile("(?i)\\bClear-Disk\\b"),
            Pattern.compile("(?i)\\bRemove-Service\\b"),
            Pattern.compile("(?i)\\bSet-ItemProperty.*-Name\\s+Path")
    );

    private static final List<Pattern> REDIRECT_OVERWRITE = List.of(
            Pattern.compile(">{1,2}\\s*(?!/dev/null)\\S+\\s*$")
    );

    private CommandSafety() {
    }

    public static SafetyCheck check(String command) {
        if (command == null || command.isBlank()) {
            return SafetyCheck.safe();
        }
        for (Pattern p : DESTRUCTIVE_PATTERNS) {
            if (p.matcher(command).find()) {
                return SafetyCheck.destructive(p.pattern());
            }
        }
        for (Pattern p : REDIRECT_OVERWRITE) {
            if (p.matcher(command).find()) {
                return SafetyCheck.warning("redirect-overwrite", p.pattern());
            }
        }
        return SafetyCheck.safe();
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
