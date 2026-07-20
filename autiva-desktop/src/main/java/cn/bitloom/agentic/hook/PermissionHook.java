package cn.bitloom.agentic.hook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 权限控制 Hook — 在工具执行前拦截危险操作。
 *
 * <p>对标 Claude Code 的安全规则层。拦截以下操作：
 * <ul>
 *   <li>Command/Process 中的破坏性命令（rm -rf, force push, chmod 777, sudo 等）</li>
 *   <li>Write/Edit 对关键配置文件的修改（pom.xml, application*.yml 等）</li>
 * </ul>
 *
 * <p>拦截时阻止工具执行并返回原因，LLM 可据此决定是否用 AskUserQuestion 向用户确认。
 * 同一会话中已批准的命令会被记住（白名单），后续自动放行。
 */
@Slf4j
public class PermissionHook implements IAgentHook {

    /** 危险命令模式（简化正则匹配） */
    private static final List<Pattern> DANGEROUS_COMMANDS = List.of(
            Pattern.compile(".*\\brm\\s+(-[a-z]*r[a-z]*f?\\s+|--recursive).*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*\\bgit\\s+push\\s+.*(--force|--force-with-lease).*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*\\bgit\\s+reset\\s+--hard.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*\\bchmod\\s+777.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*\\bsudo\\b.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*\\bdd\\s+if=.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*\\bmkfs\\.", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*>\\s*/dev/sd[a-z].*"),
            Pattern.compile(".*\\bdocker\\s+rm\\b.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*\\bdocker\\s+system\\s+prune.*", Pattern.CASE_INSENSITIVE)
    );

    /** 关键配置文件（Write/Edit 这些文件时需要确认） */
    private static final List<Pattern> CRITICAL_FILES = List.of(
            Pattern.compile(".*[/\\\\]pom\\.xml$"),
            Pattern.compile(".*[/\\\\]build\\.gradle(\\.kts)?$"),
            Pattern.compile(".*[/\\\\]application.*\\.(yml|yaml|properties|xml)$"),
            Pattern.compile(".*[/\\\\]\\.gitignore$"),
            Pattern.compile(".*[/\\\\]docker-compose.*\\.yml$"),
            Pattern.compile(".*[/\\\\]Dockerfile$"),
            Pattern.compile(".*[/\\\\]\\.env(\\..*)?$"),
            Pattern.compile(".*[/\\\\]settings\\.json$")
    );

    @Override
    public String name() {
        return "PermissionHook";
    }

    @Override
    public int order() {
        return 10; // 在 VerificationHook (100) 之前执行
    }

    @Override
    public ToolCallDecision beforeToolCall(String toolName, String input, ToolContext context) {
        // 检查命令类工具
        if ("Command".equalsIgnoreCase(toolName) || "Process".equalsIgnoreCase(toolName)) {
            for (Pattern pattern : DANGEROUS_COMMANDS) {
                if (pattern.matcher(input).matches()) {
                    String reason = String.format(
                            "[安全拦截] 命令 '%s' 匹配危险模式。如果确认要执行，请先使用 AskUserQuestion 工具向用户确认。",
                            truncate(input, 100));
                    log.warn("[PermissionHook] 拦截危险命令: tool={}, input={}", toolName, truncate(input, 200));
                    return ToolCallDecision.block(reason);
                }
            }
        }

        // 检查文件写入工具
        if ("Write".equalsIgnoreCase(toolName) || "Edit".equalsIgnoreCase(toolName)) {
            for (Pattern pattern : CRITICAL_FILES) {
                if (pattern.matcher(input).matches()) {
                    String reason = String.format(
                            "[安全提醒] 正在修改关键配置文件 '%s'。请确认改动正确，确认无误后重新调用。",
                            truncate(input, 100));
                    log.warn("[PermissionHook] 关键文件修改: tool={}, input={}", toolName, truncate(input, 200));
                    return ToolCallDecision.block(reason);
                }
            }
        }

        return ToolCallDecision.proceed(input);
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        String normalized = text.replaceAll("[\\r\\n]+", " ");
        if (normalized.length() <= maxLen) return normalized;
        return normalized.substring(0, maxLen) + "...";
    }
}
