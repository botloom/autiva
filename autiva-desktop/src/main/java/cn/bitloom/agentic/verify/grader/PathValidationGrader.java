package cn.bitloom.agentic.verify.grader;

import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.verify.Feedback;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

/**
 * 文件路径合法性校验器。
 * <p>
 * 支持文件类工具（Read/Write/Edit/Command 等）的路径参数校验：
 * - 必须是绝对路径
 * - 不能包含 .. 上级目录跳转
 * - 可选：路径白名单（从 Rubric Gene 读取 allowed_dirs）
 */
@Slf4j
@Component
public class PathValidationGrader implements ToolGrader {

    private static final Set<String> PATH_TOOL_NAMES = Set.of(
            "Read", "Write", "Edit", "Command", "Process", "Glob", "Grep"
    );

    private static final Set<String> PATH_PARAM_KEYS = Set.of(
            "file_path", "path", "filePath", "cwd", "directory", "dir"
    );

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public boolean supports(String toolName) {
        return PATH_TOOL_NAMES.contains(toolName);
    }

    @Override
    public Feedback checkArgs(String toolName, String input, List<Gene> rubrics) {
        if (input == null || input.isBlank()) {
            return Feedback.pass();
        }

        try {
            JsonNode root = mapper.readTree(input);
            for (String key : PATH_PARAM_KEYS) {
                JsonNode node = root.get(key);
                if (node != null && node.isTextual()) {
                    String pathStr = node.asText();
                    Feedback fb = validatePath(pathStr, rubrics);
                    if (!fb.passed()) {
                        return fb;
                    }
                }
            }
        } catch (Exception e) {
            // 解析失败不阻断，让工具自己处理
            log.debug("[PathValidationGrader] 输入解析失败，跳过: {}", e.getMessage());
        }
        return Feedback.pass();
    }

    @Override
    public Feedback checkResult(String toolName, String result, List<Gene> rubrics) {
        return Feedback.pass();
    }

    private Feedback validatePath(String pathStr, List<Gene> rubrics) {
        if (pathStr == null || pathStr.isBlank()) {
            return Feedback.fail("路径为空");
        }

        Path path;
        try {
            path = Paths.get(pathStr);
        } catch (Exception e) {
            return Feedback.fail("路径格式无效: " + pathStr);
        }

        if (!path.isAbsolute()) {
            return Feedback.fail("路径必须是绝对路径: " + pathStr);
        }

        String normalized = path.normalize().toString();
        if (normalized.contains("..")) {
            return Feedback.fail("路径不能包含 .. 上级目录跳转: " + pathStr, Feedback.Severity.WARN);
        }

        // 白名单校验（从 Rubric Gene 读取 allowed_dirs 字段）
        for (Gene rubric : rubrics) {
            String content = rubric.content();
            if (content != null && content.contains("allowed_dirs:")) {
                Feedback fb = checkAllowedDirs(normalized, content);
                if (!fb.passed()) {
                    return fb;
                }
            }
        }

        return Feedback.pass();
    }

    private Feedback checkAllowedDirs(String path, String rubricContent) {
        int idx = rubricContent.indexOf("allowed_dirs:");
        if (idx < 0) return Feedback.pass();

        String tail = rubricContent.substring(idx + "allowed_dirs:".length());
        int newline = tail.indexOf('\n');
        String dirsLine = newline > 0 ? tail.substring(0, newline) : tail;
        String[] allowed = dirsLine.split("[,\\s]+");

        for (String allowedDir : allowed) {
            if (allowedDir.isBlank()) continue;
            String normalized = allowedDir.trim().replace("\\", "/");
            if (path.replace("\\", "/").startsWith(normalized)) {
                return Feedback.pass();
            }
        }
        return Feedback.fail("路径不在允许的目录范围内: " + path, Feedback.Severity.WARN);
    }
}
