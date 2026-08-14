package cn.bitloom.agentic.permission.strategy;

import cn.bitloom.agentic.permission.ApprovalService;
import org.springframework.stereotype.Component;

/**
 * Write 工具的审批策略 — 文件写入。
 */
@Component
public class WriteApprovalStrategy extends FileApprovalStrategy {

    public WriteApprovalStrategy(ApprovalService approvalService) {
        super(approvalService);
    }

    @Override
    public String toolName() {
        return "Write";
    }

    @Override
    protected String action() {
        return "写入";
    }
}
