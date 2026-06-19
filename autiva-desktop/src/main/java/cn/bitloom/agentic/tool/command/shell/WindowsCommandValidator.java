package cn.bitloom.agentic.tool.command.shell;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Windows 命令验证器 — 检测 Windows/PowerShell 危险命令。
 *
 * <p>灵感来源：AgentScope 的 WindowsCommandValidator。
 */
public class WindowsCommandValidator implements CommandValidator {

    private static final List<Pattern> DESTRUCTIVE_PATTERNS = List.of(
            // Windows cmd 危险命令
            Pattern.compile("\\bformat\\s+[A-Z]:"),
            Pattern.compile("\\bshutdown\\b"),
            Pattern.compile("\\breboot\\b"),
            // PowerShell 危险命令
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

    @Override
    public ValidationResult validate(String command) {
        if (command == null || command.isBlank()) {
            return ValidationResult.safe();
        }
        for (Pattern p : DESTRUCTIVE_PATTERNS) {
            if (p.matcher(command).find()) {
                return ValidationResult.destructive("destructive-command", p.pattern());
            }
        }
        for (Pattern p : REDIRECT_OVERWRITE) {
            if (p.matcher(command).find()) {
                return ValidationResult.warning("redirect-overwrite", p.pattern());
            }
        }
        return ValidationResult.safe();
    }
}
