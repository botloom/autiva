package cn.bitloom.agentic.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EditTool implements cn.bitloom.agentic.tool.ITool {

    private final ReadTool readTool;

    @Tool(name = "edit", description = "精确编辑文件，通过查找精确文本匹配进行替换。支持替换单处或所有匹配。")
    public ToolResult edit(
            @ToolParam(description = "文件路径") String filePath,
            @ToolParam(description = "要查找替换的旧文本（必须精确匹配）") String oldText,
            @ToolParam(description = "要替换成的新文本") String newText,
            @ToolParam(description = "是否替换所有匹配，默认false（只替换第一处）", required = false) Boolean replaceAll) {
        
        log.info("[ToolCall] edit - 编辑文件: filePath={}, replaceAll={}", filePath, replaceAll);
        
        if (StringUtils.isBlank(filePath)) {
            return ToolResult.failure("错误：文件路径不能为空");
        }

        if (oldText == null) {
            return ToolResult.failure("错误：旧文本不能为null");
        }

        if (newText == null) {
            return ToolResult.failure("错误：新文本不能为null");
        }

        if (!readTool.isSafe(filePath)) {
            return ToolResult.failure("错误：路径不安全，只允许编辑 ~/.autiva 或当前工作目录下的文件");
        }

        boolean shouldReplaceAll = Boolean.TRUE.equals(replaceAll);

        try {
            Path path = resolvePath(filePath);

            if (!Files.exists(path)) {
                if (oldText.isEmpty()) {
                    return createNewFile(path, newText);
                }
                return ToolResult.failure("错误：文件不存在: " + path);
            }

            String content = Files.readString(path, StandardCharsets.UTF_8);

            if (oldText.isEmpty() && content.isEmpty()) {
                return writeToFile(path, newText, "创建新文件");
            }

            if (oldText.isEmpty() && !content.isEmpty()) {
                return ToolResult.failure("错误：文件已存在且有内容，无法用空oldText创建新文件");
            }

            if (!content.contains(oldText)) {
                return buildNotFoundResult(oldText, content);
            }

            int matchCount = countOccurrences(content, oldText);
            
            if (matchCount > 1 && !shouldReplaceAll) {
                return ToolResult.failure(
                    String.format("错误：找到 %d 处匹配，但 replaceAll=false。\n\n" +
                        "edit 工具默认只替换第一处匹配。\n" +
                        "选项：\n" +
                        "1. 设置 replaceAll=true 替换所有匹配\n" +
                        "2. 提供更精确的上下文（包含更多周围文本）来唯一标识要替换的位置\n\n" +
                        "匹配位置预览：\n%s",
                        matchCount, buildMatchPreview(content, oldText, 3)));
            }

            String newContent;
            int replacedCount;
            
            if (shouldReplaceAll) {
                newContent = content.replace(oldText, newText);
                replacedCount = matchCount;
            } else {
                newContent = replaceFirst(content, oldText, newText);
                replacedCount = 1;
            }

            Files.writeString(path, newContent, StandardCharsets.UTF_8);
            log.info("[ToolCall] edit - 编辑成功: filePath={}, 替换 {} 处文本", path, replacedCount);

            String result = String.format("成功编辑文件: %s\n- 替换了 %d 处文本", path, replacedCount);
            return ToolResult.success("编辑文件成功", result);

        } catch (IOException e) {
            log.error("[ToolCall] edit - 编辑失败: filePath={}", filePath, e);
            return ToolResult.failure("编辑文件失败: " + e.getMessage());
        }
    }

    private ToolResult createNewFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        log.info("[ToolCall] edit - 创建新文件: filePath={}", path);
        return ToolResult.success("创建文件成功", "成功创建新文件: " + path);
    }

    private ToolResult writeToFile(Path path, String content, String action) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
        log.info("[ToolCall] edit - {}: filePath={}", action, path);
        return ToolResult.success(action + "成功", action + ": " + path);
    }

    private ToolResult buildNotFoundResult(String oldText, String content) {
        String suggestion = findSimilarText(content, oldText);
        StringBuilder msg = new StringBuilder("错误：未找到匹配的文本。\n\n");
        msg.append("提示：请确保 oldText 参数与文件中的文本完全一致（包括空格、换行、缩进等）\n\n");
        
        if (suggestion != null) {
            msg.append("可能的相似文本：\n").append(suggestion).append("\n\n");
        }
        
        msg.append("要查找的文本（前100字符）：\n").append(truncate(oldText, 100));
        return ToolResult.failure(msg.toString());
    }

    private String buildMatchPreview(String content, String searchText, int maxPreviews) {
        List<String> previews = new ArrayList<>();
        int index = 0;
        int count = 0;
        
        while ((index = content.indexOf(searchText, index)) != -1 && count < maxPreviews) {
            int start = Math.max(0, index - 20);
            int end = Math.min(content.length(), index + searchText.length() + 20);
            String preview = content.substring(start, end);
            
            int relativePos = index - start;
            preview = preview.replace("\n", "\\n");
            String pointer = " ".repeat(relativePos) + "^".repeat(Math.min(searchText.length(), end - index));
            
            previews.add(String.format("  ...%s...\n   %s", preview, pointer));
            
            index += searchText.length();
            count++;
        }
        
        if (count < countOccurrences(content, searchText)) {
            previews.add("  ...(更多匹配未显示)");
        }
        
        return String.join("\n", previews);
    }

    private String findSimilarText(String content, String searchText) {
        if (searchText.length() < 3) return null;
        
        String searchLower = searchText.toLowerCase().trim();
        String[] lines = content.split("\n");
        
        for (String line : lines) {
            String lineLower = line.toLowerCase().trim();
            if (lineLower.contains(searchLower.substring(0, Math.min(searchLower.length(), 10)))) {
                return truncate(line, 100);
            }
        }
        
        return null;
    }

    private String replaceFirst(String content, String oldText, String newText) {
        int index = content.indexOf(oldText);
        if (index == -1) return content;
        return content.substring(0, index) + newText + content.substring(index + oldText.length());
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

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    private Path resolvePath(String filePath) {
        if (Paths.get(filePath).isAbsolute()) {
            return Paths.get(filePath);
        }
        return Paths.get(System.getProperty("user.dir"), filePath);
    }
}
