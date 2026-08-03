package cn.bitloom.agentic.tool.command.approval;

/**
 * 批准检查的最终决策 — CommandTool 据此决定是否继续执行。
 *
 * @param allowed  是否放行执行
 * @param message  决策说明（拒绝时为原因，放行时为 null）
 * @param result   用户选择结果（READ 直接放行时为 null）
 */
public record ApprovalDecision(boolean allowed, String message, ApprovalResult result) {

    /** 放行（READ 或已永久批准） */
    public static ApprovalDecision allow() {
        return new ApprovalDecision(true, null, null);
    }

    /** 用户批准一次 */
    public static ApprovalDecision approveOnce() {
        return new ApprovalDecision(true, null, ApprovalResult.APPROVE_ONCE);
    }

    /** 用户永久批准 */
    public static ApprovalDecision approvePermanent() {
        return new ApprovalDecision(true, null, ApprovalResult.APPROVE_PERMANENT);
    }

    /** 拒绝执行 */
    public static ApprovalDecision deny(String reason) {
        return new ApprovalDecision(false, reason, ApprovalResult.DENY);
    }
}
