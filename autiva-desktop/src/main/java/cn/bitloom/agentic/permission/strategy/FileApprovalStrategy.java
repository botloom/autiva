package cn.bitloom.agentic.permission.strategy;

import cn.bitloom.agentic.permission.ApprovalService;
import cn.bitloom.agentic.permission.model.ApprovalDecision;
import cn.bitloom.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件写工具的审批策略基类 — 委托 {@link ApprovalService#checkAndApproveFile}。
 *
 * <p>Write / Edit 共用审批骨架，子类仅提供工具名与动作描述，并通过构造器传入共享的审批服务。
 */
@Slf4j
public abstract class FileApprovalStrategy implements ToolApprovalStrategy {

    private final ApprovalService approvalService;

    protected FileApprovalStrategy(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /** 文件动作描述（"写入" / "编辑"），用于 UI 展示 */
    protected abstract String action();

    @Override
    public String approve(String toolName, String input, String projectDir, String sessionId) {
        String filePath = JsonUtils.extractString(input, "filePath", "file_path");
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        try {
            ApprovalDecision decision = approvalService.checkAndApproveFile(
                    toolName(), filePath, action(), projectDir, sessionId);
            if (!decision.allowed()) {
                log.info("[PermissionHook] 文件操作被拒绝: file={}, reason={}", filePath, decision.message());
                return "文件操作被拒绝: " + decision.message();
            }
        } catch (Exception e) {
            log.error("[PermissionHook] 文件审批异常，阻止执行: file={}, error={}", filePath, e.getMessage(), e);
            return "文件审批失败，已阻止执行: " + e.getMessage();
        }
        return null;
    }
}
