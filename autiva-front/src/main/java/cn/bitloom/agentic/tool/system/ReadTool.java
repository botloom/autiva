package cn.bitloom.agentic.tool.system;

import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.workspace.WorkspaceManager;
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
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadTool implements cn.bitloom.agentic.tool.ITool {

    private static final int MAX_FILE_SIZE = 1024 * 1024;
    private static final int DEFAULT_LIMIT = 2000;
    private static final int MAX_LINE_LENGTH = 500;

    private final WorkspaceManager workspaceManager;

    @Tool(name = "read", description = "读取文件内容，支持分页读取、行号显示。支持文本和图片文件。")
    public ToolResult read(
            @ToolParam(description = "要读取的文件路径（绝对路径或相对于工作目录的路径）") String filePath,
            @ToolParam(description = "起始行号（从1开始），默认为1", required = false) Integer offset,
            @ToolParam(description = "读取的行数，默认2000行", required = false) Integer limit) {
        
        log.info("[ToolCall] read - 读取文件: filePath={}, offset={}, limit={}", filePath, offset, limit);
        
        if (StringUtils.isBlank(filePath)) {
            return ToolResult.failure("错误：文件路径不能为空");
        }

        int startLine = offset != null && offset > 0 ? offset : 1;
        int lineLimit = limit != null && limit > 0 ? limit : DEFAULT_LIMIT;

        try {
            Path path = resolvePath(filePath);

            if (!Files.exists(path)) {
                return ToolResult.failure("错误：文件不存在: " + path);
            }

            if (!Files.isRegularFile(path)) {
                return ToolResult.failure("错误：不是一个文件: " + path);
            }

            long fileSize = Files.size(path);
            String fileName = path.getFileName().toString().toLowerCase();

            if (isImageFile(fileName)) {
                return handleImageFile(path, fileSize);
            }

            if (isBinaryFile(fileName)) {
                return ToolResult.failure("错误：无法读取二进制文件: " + fileName + "\n提示：请使用适当的工具分析二进制文件");
            }

            return readTextFile(path, startLine, lineLimit, fileSize);

        } catch (IOException e) {
            log.error("[ToolCall] read - 读取失败: filePath={}", filePath, e);
            return ToolResult.failure("读取文件失败: " + e.getMessage());
        }
    }

    private boolean isImageFile(String fileName) {
        return fileName.endsWith(".png") || fileName.endsWith(".jpg") ||
               fileName.endsWith(".jpeg") || fileName.endsWith(".gif") ||
               fileName.endsWith(".bmp") || fileName.endsWith(".webp");
    }

    private boolean isBinaryFile(String fileName) {
        return fileName.endsWith(".exe") || fileName.endsWith(".dll") ||
               fileName.endsWith(".so") || fileName.endsWith(".dylib") ||
               fileName.endsWith(".class") || fileName.endsWith(".jar") ||
               fileName.endsWith(".zip") || fileName.endsWith(".tar") ||
               fileName.endsWith(".gz") || fileName.endsWith(".rar") ||
               fileName.endsWith(".7z") || fileName.endsWith(".pdf");
    }

    private ToolResult handleImageFile(Path path, long fileSize) {
        log.info("[ToolCall] read - 读取图片文件: filePath={}, size={}", path, fileSize);
        String result = "图片文件: " + path + " (大小: " + formatSize(fileSize) + ")\n" +
                       "提示：图片内容无法以文本形式展示";
        return ToolResult.success("读取图片文件成功", result);
    }

    private ToolResult readTextFile(Path path, int startLine, int lineLimit, long fileSize) throws IOException {
        List<String> allLines = Files.readAllLines(path);
        int totalLines = allLines.size();

        if (startLine > totalLines) {
            String msg = String.format("警告：文件只有 %d 行，起始行号 %d 超出范围\n\n" +
                     "提示：文件总行数为 %d，请使用 offset=1 到 %d 之间的值", 
                     totalLines, startLine, totalLines, totalLines);
            return ToolResult.success(msg, "");
        }

        int fromIndex = startLine - 1;
        int toIndex = Math.min(fromIndex + lineLimit, totalLines);
        List<String> selectedLines = allLines.subList(fromIndex, toIndex);
        int readLines = selectedLines.size();

        StringBuilder result = new StringBuilder();
        result.append(String.format("文件: %s (大小: %s, 总行数: %d)\n", path, formatSize(fileSize), totalLines));
        result.append(String.format("显示: 第 %d-%d 行 (共 %d 行)\n\n", startLine, startLine + readLines - 1, readLines));

        int lineNumber = startLine;
        for (String line : selectedLines) {
            String truncatedLine = truncateLine(line);
            result.append(String.format("%6d→%s\n", lineNumber, truncatedLine));
            lineNumber++;
        }

        if (toIndex < totalLines) {
            int remaining = totalLines - toIndex;
            result.append(String.format("\n... [剩余 %d 行未显示，使用 offset=%d 继续读取] ...", remaining, toIndex + 1));
        }

        log.info("[ToolCall] read - 读取成功: filePath={}, lines={}-{}, total={}", 
                 path, startLine, startLine + readLines - 1, totalLines);
        
        return ToolResult.success("读取文件成功", result.toString());
    }

    private String truncateLine(String line) {
        if (line.length() <= MAX_LINE_LENGTH) {
            return line;
        }
        return line.substring(0, MAX_LINE_LENGTH) + "... [截断]";
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private Path resolvePath(String filePath) {
        Path path = Paths.get(filePath);
        if (path.isAbsolute()) {
            return path;
        }
        return Paths.get(System.getProperty("user.dir"), filePath);
    }

    @Override
    public Boolean isSafe(String path) {
        try {
            Path resolved = resolvePath(path);
            return resolved.toAbsolutePath().startsWith(
                Paths.get(System.getProperty("user.home"), ".autiva")
            ) || resolved.toAbsolutePath().startsWith(
                Paths.get(System.getProperty("user.dir"))
            );
        } catch (Exception e) {
            return false;
        }
    }
}
