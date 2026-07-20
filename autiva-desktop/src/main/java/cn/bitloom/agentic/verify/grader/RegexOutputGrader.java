package cn.bitloom.agentic.verify.grader;

import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.verify.Feedback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 产出级正则规则校验器。
 * <p>
 * 从 RUBRIC Gene 的 content 中解析 output_regex 规则，按规则校验最终产出。
 * 规则格式（在 Gene.content 中）：
 * <pre>
 * output_regex:
 *   - pattern: ".*完成.*"
 *     message: "产出必须包含完成状态说明"
 *     severity: ERROR
 * </pre>
 */
@Slf4j
@Component
public class RegexOutputGrader implements OutputGrader {

    private static final String OUTPUT_REGEX_PREFIX = "output_regex:";

    @Override
    public Feedback verify(AssistantMessage output, RuntimeContext ctx, List<Gene> rubrics) {
        if (output == null || output.getText() == null || output.getText().isEmpty()) {
            return Feedback.pass();
        }
        String text = output.getText();
        for (Gene rubric : rubrics) {
            Feedback fb = checkRegex(text, rubric.content());
            if (!fb.passed()) {
                return fb;
            }
        }
        return Feedback.pass();
    }

    private Feedback checkRegex(String text, String rubricContent) {
        if (rubricContent == null || !rubricContent.contains(OUTPUT_REGEX_PREFIX)) {
            return Feedback.pass();
        }

        int idx = rubricContent.indexOf(OUTPUT_REGEX_PREFIX);
        String section = rubricContent.substring(idx + OUTPUT_REGEX_PREFIX.length());

        String[] lines = section.split("\n");
        String currentPattern = null;
        String currentMessage = null;
        Feedback.Severity currentSeverity = Feedback.Severity.ERROR;

        for (String line : lines) {
            String trimmed = line.trim().replace("- ", "");
            if (trimmed.startsWith("pattern:")) {
                if (currentPattern != null) {
                    Feedback fb = applyPattern(text, currentPattern, currentMessage, currentSeverity);
                    if (!fb.passed()) return fb;
                }
                currentPattern = extractValue(trimmed, "pattern:");
                currentMessage = null;
                currentSeverity = Feedback.Severity.ERROR;
            } else if (trimmed.startsWith("message:")) {
                currentMessage = extractValue(trimmed, "message:");
            } else if (trimmed.startsWith("severity:")) {
                String sev = extractValue(trimmed, "severity:");
                if (sev != null) {
                    try {
                        currentSeverity = Feedback.Severity.valueOf(sev.toUpperCase());
                    } catch (IllegalArgumentException ignored) {}
                }
            } else if (trimmed.isEmpty() || (!trimmed.startsWith("pattern:")
                    && !trimmed.startsWith("message:")
                    && !trimmed.startsWith("severity:"))) {
                // 离开当前规则块
                if (currentPattern != null) {
                    Feedback fb = applyPattern(text, currentPattern, currentMessage, currentSeverity);
                    if (!fb.passed()) return fb;
                    currentPattern = null;
                }
            }
        }
        if (currentPattern != null) {
            return applyPattern(text, currentPattern, currentMessage, currentSeverity);
        }
        return Feedback.pass();
    }

    private Feedback applyPattern(String text, String patternStr, String message, Feedback.Severity severity) {
        try {
            Pattern p = Pattern.compile(patternStr);
            Matcher m = p.matcher(text);
            if (!m.find()) {
                return Feedback.fail(message != null ? message : "产出未匹配规则: " + patternStr, severity);
            }
        } catch (PatternSyntaxException e) {
            log.warn("[RegexOutputGrader] 正则表达式无效: {}", patternStr);
        }
        return Feedback.pass();
    }

    private String extractValue(String line, String prefix) {
        int i = line.indexOf(prefix);
        if (i < 0) return null;
        String val = line.substring(i + prefix.length()).trim();
        if ((val.startsWith("\"") && val.endsWith("\"")) ||
                (val.startsWith("'") && val.endsWith("'"))) {
            val = val.substring(1, val.length() - 1);
        }
        return val;
    }
}
