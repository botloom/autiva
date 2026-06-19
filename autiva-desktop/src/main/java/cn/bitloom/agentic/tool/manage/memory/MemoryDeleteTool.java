package cn.bitloom.agentic.tool.manage.memory;

import cn.bitloom.agentic.memory.MemoryManager;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 删除记忆条目（破坏性操作，需谨慎）。
 */
@Slf4j
public class MemoryDeleteTool extends AbstractTool<MemoryDeleteTool.Input> {

    private static final String DESCRIPTION = """
            删除记忆条目（破坏性操作，需谨慎）。
            type="section" 清空 memory.md 指定区块内容，target 为区块名称。
            type="journal" 删除日流水账文件，target 为日期(YYYY-MM-DD)。""";

    private final MemoryManager memoryManager;

    public MemoryDeleteTool(MemoryManager memoryManager) {
        super("memory_delete", DESCRIPTION, Input.class);
        this.memoryManager = memoryManager;
    }

    public record Input(
            @ToolParam(description = "删除类型：section 或 journal") String type,
            @ToolParam(description = "区块名称或日期(YYYY-MM-DD)") String target
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        String sessionId = (String) context.getContext().get("sessionId");
        String agentId = memoryManager.resolveAgentId(sessionId);
        log.info("[ToolCall] memory_delete - agentId={}, type={}, target={}", agentId, input.type(), input.target());
        try {
            memoryManager.delete(agentId, input.type(), input.target());
            return ToolResult.success("记忆已删除: " + input.type() + "/" + input.target());
        } catch (Exception e) {
            return ToolResult.error("删除记忆失败: " + e.getMessage());
        }
    }
}
