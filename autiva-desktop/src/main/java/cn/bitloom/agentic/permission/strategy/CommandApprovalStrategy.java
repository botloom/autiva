package cn.bitloom.agentic.permission.strategy;

import cn.bitloom.agentic.permission.ApprovalService;
import cn.bitloom.agentic.permission.model.ApprovalDecision;
import cn.bitloom.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Command 工具的审批策略 — 委托 {@link ApprovalService#checkAndApprove}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandApprovalStrategy implements ToolApprovalStrategy {

    private final ApprovalService approvalService;

    @Override
    public String toolName() {
        return "Command";
    }

    @Override
    public String approve(String toolName, String input, String projectDir, String sessionId) {
        String command = JsonUtils.extractString(input, "command");
        if (command == null || command.isBlank()) {
            return null;
        }
        try {
            ApprovalDecision decision = approvalService.checkAndApprove(command, projectDir, sessionId);
            if (!decision.allowed()) {
                log.info("[PermissionHook] 命令被拒绝: command={}, reason={}", command, decision.message());
                return "命令被拒绝: " + decision.message();
            }
        } catch (Exception e) {
            log.error("[PermissionHook] 命令审批异常，阻止执行: command={}, error={}", command, e.getMessage(), e);
            return "命令审批失败，已阻止执行: " + e.getMessage();
        }
        return null;
    }
}
