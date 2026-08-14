package cn.bitloom.agentic.permission.strategy;

/**
 * 工具审批策略 — 每种需要权限拦截的工具实现一个策略，注册进权限 Hook 分发器。
 *
 * <p>新增需审批的工具时，只需新增一个策略实现并由 Spring 管理，无需改动分发逻辑。
 */
public interface ToolApprovalStrategy {

    /**
     * 处理的工具名（不区分大小写，分发匹配时会统一转小写）。
     */
    String toolName();

    /**
     * 审批工具调用。
     *
     * @param input     工具原始输入（JSON 字符串）
     * @param projectDir 当前项目目录（null 表示 work 模式，批准跳过）
     * @param sessionId  当前会话 ID
     * @return null 表示放行；非 null 表示阻止原因
     */
    String approve(String input, String projectDir, String sessionId);
}
