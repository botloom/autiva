package cn.bitloom.agentic.permission.strategy;

/**
 * 工具审批策略 — 每种需要权限拦截的工具实现一个策略，注册进权限 Hook 分发器。
 *
 * <p>新增需审批的工具时，只需新增一个策略实现并由 Spring 管理，无需改动分发逻辑。
 * 动态命名的工具（如 MCP 的 mcp__server__tool）可覆写 {@link #matches} 实现前缀/注册表匹配。
 */
public interface ToolApprovalStrategy {

    /**
     * 处理的工具名（不区分大小写，分发匹配时会统一转小写）。
     */
    String toolName();

    /**
     * 判断本策略是否处理该工具调用。默认按 {@link #toolName()} 精确匹配（不区分大小写）。
     */
    default boolean matches(String toolName) {
        return toolName() != null && toolName().equalsIgnoreCase(toolName);
    }

    /**
     * 审批工具调用。
     *
     * @param toolName   实际调用的工具名（动态命名工具如 MCP 工具与注册名一致）
     * @param input      工具原始输入（JSON 字符串）
     * @param projectDir 当前项目目录（null 表示 work 模式，批准跳过）
     * @param sessionId  当前会话 ID
     * @return null 表示放行；非 null 表示阻止原因
     */
    String approve(String toolName, String input, String projectDir, String sessionId);
}
