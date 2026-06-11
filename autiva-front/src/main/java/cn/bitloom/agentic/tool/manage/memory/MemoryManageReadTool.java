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
 * 读取指定智能体的记忆文件内容。
 */
@Slf4j
public class MemoryManageReadTool extends AbstractTool<MemoryManageReadTool.Input> {

    private static final String DESCRIPTION = "读取指定智能体的记忆文件内容";

    private MemoryManageReadTool() {
        super("memory_manage_read", DESCRIPTION, Input.class);
    }

    public record Input(
            @ToolParam(description = "智能体名称（MAIN或DOCTOR）") String agentName,
            @ToolParam(description = "文件名（如MEMORY.md、USER.md）") String fileName
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        log.info("[ToolCall] memory_manage_read - 读取记忆文件: agent={}, file={}", input.agentName(), input.fileName());
        Path agentDir = "default".equals(input.agentName())
                ? AppConstants.Base.APP_DIR
                : AppConstants.Base.WORKSPACE_DIR.resolve(input.agentName());
        Path filePath = agentDir.resolve(input.fileName());
        if (!Files.exists(filePath)) {
            return ToolResult.error("文件不存在: " + filePath);
        }
        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            return ToolResult.builder().status(ToolResult.Status.SUCCESS)
                    .message("读取文件: " + input.fileName())
                    .rawOutput(content)
                    .build();
        } catch (IOException e) {
            return ToolResult.error("读取文件失败: " + e.getMessage());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Builder() {}

        public MemoryManageReadTool build() {
            return new MemoryManageReadTool();
        }
    }
}
