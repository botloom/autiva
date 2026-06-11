package cn.bitloom.agentic.tool.manage.memory;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.constant.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 删除指定智能体的记忆文件（破坏性操作，需确认）。
 */
@Slf4j
public class MemoryManageDeleteTool extends AbstractTool<MemoryManageDeleteTool.Input> {

    private static final String DESCRIPTION = "删除指定智能体的记忆文件（破坏性操作，需确认）";

    private MemoryManageDeleteTool() {
        super("memory_manage_delete", DESCRIPTION, Input.class);
    }

    public record Input(
            @ToolParam(description = "智能体名称（MAIN或DOCTOR）") String agentName,
            @ToolParam(description = "要删除的文件名") String fileName
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        log.info("[ToolCall] memory_manage_delete - 删除记忆文件: agent={}, file={}", input.agentName(), input.fileName());
        Path agentDir = "default".equals(input.agentName())
                ? AppConstants.Base.APP_DIR
                : AppConstants.Base.WORKSPACE_DIR.resolve(input.agentName());
        Path filePath = agentDir.resolve(input.fileName());
        try {
            if (Files.deleteIfExists(filePath)) {
                return ToolResult.success("文件已删除: " + input.fileName());
            } else {
                return ToolResult.error("文件不存在: " + input.fileName());
            }
        } catch (IOException e) {
            return ToolResult.error("删除文件失败: " + e.getMessage());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Builder() {}

        public MemoryManageDeleteTool build() {
            return new MemoryManageDeleteTool();
        }
    }
}
