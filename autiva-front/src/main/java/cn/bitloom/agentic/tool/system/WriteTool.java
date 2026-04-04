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
public class WriteTool implements cn.bitloom.agentic.tool.ITool {

    private final ReadTool readTool;

    @Tool(name = "write", description = "写入内容到文件，自动创建父目录。如果文件已存在则覆盖。")
    public ToolResult write(@ToolParam(description = "文件路径（绝对路径或相对于工作目录的路径）") String filePath,
                       @ToolParam(description = "要写入的内容") String content) {
        log.info("[ToolCall] write - 写入文件: filePath={}", filePath);
        if (StringUtils.isBlank(filePath)) {
            return ToolResult.failure("错误：文件路径不能为空");
        }

        if (content == null) {
            return ToolResult.failure("错误：内容不能为空");
        }

        if (!readTool.isSafe(filePath)) {
            return ToolResult.failure("错误：路径不安全，只允许写入 ~/.autiva 或当前工作目录下的文件");
        }

        try {
            Path path = resolvePath(filePath);

            Path parentDir = path.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                log.info("[ToolCall] write - 创建父目录: {}", parentDir);
            }

            Files.writeString(path, content);
            log.info("[ToolCall] write - 写入成功: filePath={}, bytes={}", path, content.length());

            String result = "成功写入文件: " + path + " (" + content.length() + " 字节)";
            return ToolResult.success("写入文件成功", result);

        } catch (IOException e) {
            log.error("[ToolCall] write - 写入失败: filePath={}", filePath, e);
            return ToolResult.failure("写入文件失败: " + e.getMessage());
        }
    }

    private Path resolvePath(String filePath) {
        Path path = Paths.get(filePath);
        if (path.isAbsolute()) {
            return path;
        }
        return Paths.get(System.getProperty("user.dir"), filePath);
    }
}