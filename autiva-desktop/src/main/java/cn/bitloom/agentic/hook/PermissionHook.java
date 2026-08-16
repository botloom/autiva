package cn.bitloom.agentic.hook;

import cn.bitloom.agentic.permission.strategy.ToolApprovalStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 权限控制 Hook — 在工具执行前统一拦截需要审批的操作。
 *
 * <p>统一的权限入口，替代原本散落在 CommandTool/WriteTool/EditTool 内部的审批逻辑。
 * 拦截以下操作：
 * <ul>
 *   <li>Command/Process 工具：通过 {@link cn.bitloom.agentic.permission.ApprovalService} 弹批准框</li>
 *   <li>Write/Edit 工具：通过 {@link cn.bitloom.agentic.permission.ApprovalService} 弹批准框</li>
 * </ul>
 *
 * <p>审批状态由 {@link cn.bitloom.agentic.permission.ApprovalStore} 持久化，
 * 同一项目内已批准的操作会自动放行。
 *
 * <p>工具 → 审批策略采用 {@link ToolApprovalStrategy} 分发表：新增需审批的
 * 工具时，只需新增策略实现并由 Spring 管理，无需改动本类分发逻辑。
 */
@Slf4j
public class PermissionHook implements IAgentHook {

    private final List<ToolApprovalStrategy> approvalStrategies;
    private final Map<String, ToolApprovalStrategy> strategyByTool;

    public PermissionHook(List<ToolApprovalStrategy> approvalStrategies) {
        this.approvalStrategies = List.copyOf(approvalStrategies);
        this.strategyByTool = approvalStrategies.stream()
                .filter(s -> s.toolName() != null)
                .collect(Collectors.toMap(
                        s -> s.toolName().toLowerCase(Locale.ROOT),
                        Function.identity()));
    }

    @Override
    public String name() {
        return "PermissionHook";
    }

    @Override
    public int order() {
        return 10; // 最先执行，拦截未授权操作
    }

    @Override
    public ToolCallDecision beforeToolCall(String toolName, String input, ToolContext context) {
        String projectDir = extractString(context, "projectPath");
        String sessionId = extractString(context, "sessionId");

        ToolApprovalStrategy strategy = findStrategy(toolName);
        if (strategy == null) {
            return ToolCallDecision.proceed(input);
        }

        String blockReason = strategy.approve(toolName, input, projectDir, sessionId);
        if (blockReason != null) {
            log.info("[PermissionHook] 工具调用被拦截: tool={}, reason={}", toolName, blockReason);
            return ToolCallDecision.block(blockReason);
        }
        return ToolCallDecision.proceed(input);
    }

    /**
     * 查找处理该工具的策略：精确匹配（快路径）→ matches 匹配（动态命名工具，如 MCP）。
     */
    private ToolApprovalStrategy findStrategy(String toolName) {
        if (toolName == null) {
            return null;
        }
        ToolApprovalStrategy exact = strategyByTool.get(toolName.toLowerCase(Locale.ROOT));
        if (exact != null && exact.matches(toolName)) {
            return exact;
        }
        return approvalStrategies.stream()
                .filter(s -> s.matches(toolName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 从 ToolContext 中提取字符串值。
     */
    private String extractString(ToolContext context, String key) {
        if (context == null || context.getContext() == null) {
            return null;
        }
        Object value = context.getContext().get(key);
        return value instanceof String s ? s : null;
    }
}
