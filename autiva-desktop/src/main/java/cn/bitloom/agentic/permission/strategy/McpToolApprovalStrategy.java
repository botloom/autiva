package cn.bitloom.agentic.permission.strategy;

import cn.bitloom.agentic.permission.ApprovalService;
import cn.bitloom.agentic.permission.McpHostPolicy;
import cn.bitloom.agentic.permission.McpHostPolicy.Decision;
import cn.bitloom.agentic.permission.model.ApprovalDecision;
import cn.bitloom.agentic.tool.mcp.McpConnectionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MCP 工具的审批策略 — 宿主策略 {@link McpHostPolicy} 的执行入口。
 *
 * <p>匹配所有 MCP 工具（运行时连接的注册表 + mcp__ 命名空间的启动注入工具）。
 * 决策流：DENY 硬拒绝 → ALLOW 放行 → CONFIRM 走 {@link ApprovalService} 弹窗
 * （永久批准按工具名粒度记入项目 ApprovalStore）。
 *
 * <p>纪律：不读取 server 自述的 readOnlyHint，授权只来自宿主策略表。
 */
@Slf4j
@Component
public class McpToolApprovalStrategy implements ToolApprovalStrategy {

    private static final String MCP_PREFIX = "mcp__";

    private final McpConnectionManager connectionManager;
    private final McpHostPolicy hostPolicy;
    private final ApprovalService approvalService;

    public McpToolApprovalStrategy(McpConnectionManager connectionManager,
                                   McpHostPolicy hostPolicy,
                                   ApprovalService approvalService) {
        this.connectionManager = connectionManager;
        this.hostPolicy = hostPolicy;
        this.approvalService = approvalService;
    }

    @Override
    public String toolName() {
        // 信息性名称；实际分发依赖 matches() 的前缀/注册表匹配
        return "MCP";
    }

    @Override
    public boolean matches(String toolName) {
        if (toolName == null) {
            return false;
        }
        return toolName.startsWith(MCP_PREFIX) || connectionManager.isMcpTool(toolName);
    }

    @Override
    public String approve(String toolName, String input, String projectDir, String sessionId) {
        String server = resolveServer(toolName);
        String tool = resolveTool(toolName);
        Decision decision = hostPolicy.decide(projectDir, server, tool);
        log.info("[McpHostPolicy] 工具={}, server={}, decision={}", toolName, server, decision);

        return switch (decision) {
            case DENY -> "MCP 工具被宿主策略拒绝: " + toolName
                    + "（可在 .autiva/mcp-policy.json 中调整策略）";
            case ALLOW -> null;
            case CONFIRM -> confirmViaApproval(toolName, server, projectDir, sessionId);
        };
    }

    /**
     * CONFIRM 决策：经 ApprovalService 弹窗（永久批准按工具名记入项目 store）。
     */
    private String confirmViaApproval(String toolName, String server, String projectDir, String sessionId) {
        try {
            ApprovalDecision decision = approvalService.checkAndApproveFile(
                    toolName, null, "调用 MCP 工具", projectDir, sessionId);
            if (!decision.allowed()) {
                return "MCP 工具调用被拒绝: " + toolName + " - " + decision.message();
            }
            return null;
        } catch (Exception e) {
            log.error("[McpHostPolicy] MCP 审批异常，阻止执行: tool={}, error={}", toolName, e.getMessage(), e);
            return "MCP 工具审批失败，已阻止执行: " + e.getMessage();
        }
    }

    /**
     * 从工具名解析 server：优先注册表反查，其次按 mcp__{server}__{tool} 分段。
     */
    private String resolveServer(String toolName) {
        String owner = connectionManager.ownerOf(toolName);
        if (owner != null) {
            return owner;
        }
        String[] parts = toolName.split("__");
        return parts.length >= 3 ? parts[1] : toolName;
    }

    private String resolveTool(String toolName) {
        String[] parts = toolName.split("__");
        return parts.length >= 3 ? parts[2] : toolName;
    }
}
