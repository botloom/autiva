package cn.bitloom.agentic.verify.grader;

import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.verify.Feedback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 正则规则校验器（工具级）。
 * <p>
 * 从 RUBRIC Gene 的 content 中解析 regex 规则，按规则校验工具输入和结果。
 * 规则格式（在 Gene.content 中）：
 * <pre>
 * args_regex:
 *   - pattern: "^/tmp/.*$"
 *     message: "路径必须在 /tmp 下"
 * result_regex:
 *   - pattern: ".*success.*"
 *     message: "结果必须包含 success 标识"
 * </pre>
 */
@Slf4j
@Component
public class RegexToolGrader implements ToolGrader {

    private static final String ARGS_REGEX_PREFIX = "args_regex:";
    private static final String RESULT_REGEX_PREFIX = "result_regex:";

    @Override
    public Feedback checkArgs(String toolName, String input, List<Gene> rubrics) {
        if (input == null || input.isEmpty()) {
            return Feedback.pass();
        }
        for (Gene rubric : rubrics) {
            Feedback fb = checkRegex(input, rubric.content(), ARGS_REGEX_PREFIX);
            if (!fb.passed()) {
                return fb;
            }
        }
        return Feedback.pass();
    }

    @Override
    public Feedback checkResult(String toolName, String result, List<Gene> rubrics) {
        if (result == null || result.isEmpty()) {
            return Feedback.pass();
        }
        for (Gene rubric : rubrics) {
            Feedback fb = checkRegex(result, rubric.content(), RESULT_REGEX_PREFIX);
            if (!fb.passed()) {
                return fb;
            }
        }
        return Feedback.pass();
    }

    private Feedback checkRegex(String text, String rubricContent, String sectionPrefix) {
        if (rubricContent == null || !rubricContent.contains(sectionPrefix)) {
            return Feedback.pass();
        }

        int idx = rubricContent.indexOf(sectionPrefix);
        String section = rubricContent.substring(idx + sectionPrefix.length());
        // 截取到下一个顶层字段
        int nextSection = findNextSection(section);
        if (nextSection > 0) {
            section = section.substring(0, nextSection);
        }

        // 简单解析 - pattern: 和 message:
        String[] lines = section.split("\n");
        String currentPattern = null;
        String currentMessage = null;

        for (String line : lines) {
            String trimmed = line.trim().replace("- ", "");
            if (trimmed.startsWith("pattern:")) {
                if (currentPattern != null) {
                    Feedback fb = applyPattern(text, currentPattern, currentMessage);
                    if (!fb.passed()) return fb;
                }
                currentPattern = extractValue(trimmed, "pattern:");
                currentMessage = null;
            } else if (trimmed.startsWith("message:")) {
                currentMessage = extractValue(trimmed, "message:");
            }
        }
        if (currentPattern != null) {
            return applyPattern(text, currentPattern, currentMessage);
        }
        return Feedback.pass();
    }

    private Feedback applyPattern(String text, String patternStr, String message) {
        try {
            Pattern p = Pattern.compile(patternStr);
            Matcher m = p.matcher(text);
            if (!m.find()) {
                return Feedback.fail(message != null ? message : "正则校验未通过: " + patternStr);
            }
        } catch (PatternSyntaxException e) {
            log.warn("[RegexToolGrader] 正则表达式无效: {}", patternStr);
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

    private int findNextSection(String text) {
        String[] sections = {"args_regex:", "result_regex:", "allowed_dirs:"};
        int earliest = -1;
        for (String s : sections) {
            int i = text.indexOf(s, 1);
            if (i > 0 && (earliest < 0 || i < earliest)) {
                earliest = i;
            }
        }
        return earliest;
    }
}
