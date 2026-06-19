package cn.bitloom.agentic.tool.manage.memory;

import cn.bitloom.agentic.memory.MemoryManager;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 搜索记忆文件（memory.md 和 memory/YYYY-MM-DD.md）。
 */
@Slf4j
public class MemorySearchTool extends AbstractTool<MemorySearchTool.Input> {

    private static final String DESCRIPTION = """
            搜索记忆文件，包括 memory.md 和 memory/YYYY-MM-DD.md 日流水账。
            回答关于用户偏好、历史事件的问题前，应先调用此工具搜索相关记忆。""";

    private final MemoryManager memoryManager;

    public MemorySearchTool(MemoryManager memoryManager) {
        super("memory_search", DESCRIPTION, Input.class);
        this.memoryManager = memoryManager;
    }

    public record Input(
            @ToolParam(description = "搜索关键词") String query,
            @ToolParam(description = "最大结果数（默认 5）", required = false) Integer limit
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        String sessionId = (String) context.getContext().get("sessionId");
        String agentId = memoryManager.resolveAgentId(sessionId);
        int limit = input.limit() != null ? input.limit() : 5;
        log.info("[ToolCall] memory_search - agentId={}, query={}, limit={}", agentId, input.query(), limit);
        try {
            String result = memoryManager.search(agentId, input.query(), limit);
            return ToolResult.builder()
                    .status(ToolResult.Status.SUCCESS)
                    .message("搜索记忆: " + input.query())
                    .rawOutput(result)
                    .build();
        } catch (Exception e) {
            return ToolResult.error("搜索记忆失败: " + e.getMessage());
        }
    }
}
