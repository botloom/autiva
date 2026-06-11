package cn.bitloom.agentic.tool.manage.memory;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.constant.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 写入或更新指定智能体的记忆文件内容。
 */
@Slf4j
public class MemoryManageWriteTool extends AbstractTool<MemoryManageWriteTool.Input> {

    private static final String DESCRIPTION = "写入或更新指定智能体的记忆文件内容";

    private MemoryManageWriteTool() {
        super("memory_manage_write", DESCRIPTION, Input.class);
    }

    public record Input(
            @ToolParam(description = "智能体名称（MAIN或DOCTOR）") String agentName,
            @ToolParam(description = "文件名（如MEMORY.md、USER.md）") String fileName,
            @ToolParam(description = "文件内容") String content
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        log.info("[ToolCall] memory_manage_write - 写入记忆文件: agent={}, file={}", input.agentName(), input.fileName());
        Path agentDir = "default".equals(input.agentName())
                ? AppConstants.Base.APP_DIR
                : AppConstants.Base.WORKSPACE_DIR.resolve(input.agentName());
        Path filePath = agentDir.resolve(input.fileName());
        try {
            Files.writeString(filePath, input.content(), StandardCharsets.UTF_8);
            return ToolResult.success("文件已更新: " + input.fileName());
        } catch (IOException e) {
            return ToolResult.error("写入文件失败: " + e.getMessage());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Builder() {}

        public MemoryManageWriteTool build() {
            return new MemoryManageWriteTool();
        }
    }
}
