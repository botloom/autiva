package cn.bitloom.agentic.tool.command.approval;

/**
 * 用户对命令批准请求的选择。
 */
public enum ApprovalResult {
    /** 批准一次（不持久化，下次同样命令仍会弹框） */
    APPROVE_ONCE,
    /** 永久批准（写入 .autiva/command-approvals.json 的 allow 列表） */
    APPROVE_PERMANENT,
    /** 拒绝执行（返回错误给 LLM） */
    DENY
}
