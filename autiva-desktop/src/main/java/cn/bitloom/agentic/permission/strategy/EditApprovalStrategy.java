package cn.bitloom.agentic.permission.strategy;

import cn.bitloom.agentic.permission.ApprovalService;
import org.springframework.stereotype.Component;

/**
 * Edit 工具的审批策略 — 文件编辑。
 */
@Component
public class EditApprovalStrategy extends FileApprovalStrategy {

    public EditApprovalStrategy(ApprovalService approvalService) {
        super(approvalService);
    }

    @Override
    public String toolName() {
        return "Edit";
    }

    @Override
    protected String action() {
        return "编辑";
    }
}
