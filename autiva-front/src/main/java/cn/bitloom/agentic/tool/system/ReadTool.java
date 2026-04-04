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

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadTool implements cn.bitloom.agentic.tool.ITool {

    private static final int MAX_FILE_SIZE = 1024 * 1024;
    private static final int TRUNCATE_SIZE = 10 * 1024;

    private final WorkspaceManager workspaceManager;

    @Tool(name = "read", description = "读取文件内容，支持文本和图片文件。会自动截断过大的文件（超过1MB截断到10KB）")
    public ToolResult read(@ToolParam(description = "要读取的文件路径（绝对路径或相对于工作目录的路径）") String filePath) {
        log.info("[ToolCall] read - 读取文件: filePath={}", filePath);
        if (StringUtils.isBlank(filePath)) {
            return ToolResult.failure("错误：文件路径不能为空");
        }

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

            if (fileName.endsWith(".png") || fileName.endsWith(".jpg") ||
                fileName.endsWith(".jpeg") || fileName.endsWith(".gif") ||
                fileName.endsWith(".bmp") || fileName.endsWith(".webp")) {
                log.info("[ToolCall] read - 读取图片文件: filePath={}, size={}", path, fileSize);
                String result = "图片文件: " + path + " (大小: " + fileSize + " bytes)\n" +
                               "提示：图片内容无法以文本形式展示，请使用图像查看工具";
                return ToolResult.success("读取图片文件成功", result);
            }

            if (fileSize > MAX_FILE_SIZE) {
                String truncated = readTruncated(path, TRUNCATE_SIZE);
                log.info("[ToolCall] read - 文件过大已截断: filePath={}, size={}", path, fileSize);
                String result = "文件过大，已截断显示前 " + TRUNCATE_SIZE + " 字节:\n\n" + truncated +
                               "\n\n... [文件剩余 " + (fileSize - TRUNCATE_SIZE) + " 字节未显示] ...";
                return ToolResult.success("文件已截断读取", result);
            }

            String content = Files.readString(path);
            log.info("[ToolCall] read - 读取成功: filePath={}, size={}", path, fileSize);
            String result = "文件: " + path + " (大小: " + fileSize + " bytes)\n\n" + content;
            return ToolResult.success("读取文件成功", result);

        } catch (IOException e) {
            log.error("[ToolCall] read - 读取失败: filePath={}", filePath, e);
            return ToolResult.failure("读取文件失败: " + e.getMessage());
        }
    }

    private Path resolvePath(String filePath) {
        if (Paths.get(filePath).isAbsolute()) {
            return Paths.get(filePath);
        }
        return Paths.get(System.getProperty("user.dir"), filePath);
    }

    private String readTruncated(Path path, int maxBytes) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length <= maxBytes) {
            return new String(bytes);
        }
        return new String(bytes, 0, maxBytes);
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