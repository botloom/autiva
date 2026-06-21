package cn.bitloom.agentic.tool.manage.memory;

import cn.bitloom.agentic.memory.MemoryManager;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 追加记忆到 memory.md。
 */
@Slf4j
public class MemorySaveTool extends AbstractTool<MemorySaveTool.Input> {

    private static final String DESCRIPTION = """
            追加记忆到 memory.md。
            适用于记录重要事件、决策、用户偏好等。""";

    private final MemoryManager memoryManager;

    public MemorySaveTool(MemoryManager memoryManager) {
        super("memory_save", DESCRIPTION, Input.class);
        this.memoryManager = memoryManager;
    }

    public record Input(
            @ToolParam(description = "记忆内容") String content
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        String sessionId = (String) context.getContext().get("sessionId");
        String agentId = memoryManager.resolveAgentId(sessionId);
        log.info("[ToolCall] memory_save - agentId={}", agentId);
        try {
            memoryManager.save(agentId, input.content());
            return ToolResult.success("记忆已保存");
        } catch (Exception e) {
            return ToolResult.error("保存记忆失败: " + e.getMessage());
        }
    }
}
