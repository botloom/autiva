package cn.bitloom.agentic.tool.command.shell;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Unix 命令验证器 — 检测 Unix/Bash 危险命令。
 *
 * <p>灵感来源：AgentScope 的 UnixCommandValidator。
 */
public class UnixCommandValidator implements CommandValidator {

    private static final List<Pattern> DESTRUCTIVE_PATTERNS = List.of(
            Pattern.compile("\\brm\\s+(-[a-zA-Z]*f[a-zA-Z]*\\s+|.*--no-preserve-root)"),
            Pattern.compile("\\bdd\\s+.*/dev/(sd|nvme|vd|hd)"),
            Pattern.compile("\\bmkfs\\."),
            Pattern.compile("\\bshutdown\\b"),
            Pattern.compile("\\breboot\\b"),
            Pattern.compile("\\binit\\s+[06]"),
            Pattern.compile(">\\s*/dev/sd"),
            Pattern.compile("\\bsudo\\s+rm\\s+"),
            Pattern.compile("\\brmdir\\s+/\\s*$"),
            Pattern.compile("\\bchmod\\s+(-R\\s+)?000\\s+/"),
            Pattern.compile("\\bchown\\s+.*\\s+/\\s*$"),
            Pattern.compile("\\bcurl\\s+.*\\|\\s*(ba)?sh"),
            Pattern.compile("\\bwget\\s+.*\\|\\s*(ba)?sh")
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
