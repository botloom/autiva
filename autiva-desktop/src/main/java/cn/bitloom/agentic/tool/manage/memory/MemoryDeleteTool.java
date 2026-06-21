package cn.bitloom.agentic.tool.manage.memory;

import cn.bitloom.agentic.memory.MemoryManager;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 清空 memory.md 指定区块内容（破坏性操作，需谨慎）。
 */
@Slf4j
public class MemoryDeleteTool extends AbstractTool<MemoryDeleteTool.Input> {

    private static final String DESCRIPTION = """
            清空 memory.md 指定区块内容（破坏性操作，需谨慎）。
            target 为区块名称（如：用户画像、关键偏好、近期事件、例行提醒）。""";

    private final MemoryManager memoryManager;

    public MemoryDeleteTool(MemoryManager memoryManager) {
        super("memory_delete", DESCRIPTION, Input.class);
        this.memoryManager = memoryManager;
    }

    public record Input(
            @ToolParam(description = "区块名称（如：用户画像、关键偏好、近期事件、例行提醒）") String target
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        String sessionId = (String) context.getContext().get("sessionId");
        String agentId = memoryManager.resolveAgentId(sessionId);
        log.info("[ToolCall] memory_delete - agentId={}, target={}", agentId, input.target());
        try {
            memoryManager.delete(agentId, input.target());
            return ToolResult.success("记忆区块已清空: " + input.target());
        } catch (Exception e) {
            return ToolResult.error("清空记忆失败: " + e.getMessage());
        }
    }
}
