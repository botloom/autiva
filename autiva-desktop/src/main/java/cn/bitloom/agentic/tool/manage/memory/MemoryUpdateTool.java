package cn.bitloom.agentic.tool.manage.memory;

import cn.bitloom.agentic.memory.MemoryManager;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 更新 memory.md 的指定区块。
 */
@Slf4j
public class MemoryUpdateTool extends AbstractTool<MemoryUpdateTool.Input> {

    private static final String DESCRIPTION = """
            更新 memory.md 的指定区块内容。
            可用区块：用户画像、关键偏好、近期事件、例行提醒。
            会替换该区块的全部内容。""";

    private final MemoryManager memoryManager;

    public MemoryUpdateTool(MemoryManager memoryManager) {
        super("memory_update", DESCRIPTION, Input.class);
        this.memoryManager = memoryManager;
    }

    public record Input(
            @ToolParam(description = "区块名称：用户画像/关键偏好/近期事件/例行提醒") String section,
            @ToolParam(description = "新的区块内容") String content
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        String sessionId = (String) context.getContext().get("sessionId");
        String agentId = memoryManager.resolveAgentId(sessionId);
        log.info("[ToolCall] memory_update - agentId={}, section={}", agentId, input.section());
        try {
            memoryManager.update(agentId, input.section(), input.content());
            return ToolResult.success("记忆区块已更新: " + input.section());
        } catch (Exception e) {
            return ToolResult.error("更新记忆失败: " + e.getMessage());
        }
    }
}
