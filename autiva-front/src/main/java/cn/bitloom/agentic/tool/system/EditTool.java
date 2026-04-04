package cn.bitloom.agentic.tool.system;

import cn.bitloom.agentic.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
@RequiredArgsConstructor
public class EditTool implements cn.bitloom.agentic.tool.ITool {

    private final ReadTool readTool;

    @Tool(name = "edit", description = "精确编辑文件，通过查找精确文本匹配进行替换。适用于手术式修改文件。")
    public ToolResult edit(@ToolParam(description = "文件路径") String filePath,
                       @ToolParam(description = "要查找替换的旧文本（必须精确匹配）") String oldText,
                       @ToolParam(description = "要替换成的新文本") String newText) {
        log.info("[ToolCall] edit - 编辑文件: filePath={}", filePath);
        if (StringUtils.isBlank(filePath)) {
            return ToolResult.failure("错误：文件路径不能为空");
        }

        if (StringUtils.isBlank(oldText)) {
            return ToolResult.failure("错误：旧文本不能为空");
        }

        if (!readTool.isSafe(filePath)) {
            return ToolResult.failure("错误：路径不安全，只允许编辑 ~/.autiva 或当前工作目录下的文件");
        }

        try {
            Path path = resolvePath(filePath);

            if (!Files.exists(path)) {
                return ToolResult.failure("错误：文件不存在: " + path);
            }

            String content = Files.readString(path);

            if (!content.contains(oldText)) {
                return ToolResult.failure("错误：未找到匹配的文本。\n\n" +
                       "提示：请确保 oldText 参数与文件中的文本完全一致（包括空格、换行等）");
            }

            int matchCount = countOccurrences(content, oldText);
            if (matchCount > 1) {
                return ToolResult.failure("错误：找到 " + matchCount + " 处匹配。\n\n" +
                       "edit 工具只支持精确替换单处文本。\n" +
                       "请提供更精确的上下文（包含更多周围文本）来唯一标识要替换的位置。");
            }

            String newContent = content.replaceFirst(escapeRegex(oldText), escapeReplacement(newText));

            Files.writeString(path, newContent);
            log.info("[ToolCall] edit - 编辑成功: filePath={}, 替换 1 处文本", path);

            String result = "成功编辑文件: " + path + "\n" +
                   "- 替换了 1 处文本";
            return ToolResult.success("编辑文件成功", result);

        } catch (IOException e) {
            log.error("[ToolCall] edit - 编辑失败: filePath={}", filePath, e);
            return ToolResult.failure("编辑文件失败: " + e.getMessage());
        }
    }

    private Path resolvePath(String filePath) {
        if (Paths.get(filePath).isAbsolute()) {
            return Paths.get(filePath);
        }
        return Paths.get(System.getProperty("user.dir"), filePath);
    }

    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private String escapeRegex(String text) {
        return text.replace("\\", "\\\\")
                   .replace("[", "\\[")
                   .replace("]", "\\]")
                   .replace("(", "\\(")
                   .replace(")", "\\)")
                   .replace(".", "\\.")
                   .replace("*", "\\*")
                   .replace("+", "\\+")
                   .replace("?", "\\?")
                   .replace("^", "\\^")
                   .replace("$", "\\$")
                   .replace("{", "\\{")
                   .replace("}", "\\}")
                   .replace("|", "\\|")
                   .replace("?", "\\?");
    }

    private String escapeReplacement(String text) {
        return text.replace("\\", "\\\\")
                   .replace("$", "\\$");
    }
}