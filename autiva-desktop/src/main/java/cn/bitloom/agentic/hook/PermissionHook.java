package cn.bitloom.agentic.hook;

import cn.bitloom.agentic.tool.command.approval.ApprovalDecision;
import cn.bitloom.agentic.tool.command.approval.CommandApprovalService;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

/**
 * 权限控制 Hook — 在工具执行前统一拦截需要审批的操作。
 *
 * <p>统一的权限入口，替代原本散落在 CommandTool/WriteTool/EditTool 内部的审批逻辑。
 * 拦截以下操作：
 * <ul>
 *   <li>Command/Process 工具：通过 CommandApprovalService 弹批准框</li>
 *   <li>Write/Edit 工具：通过 CommandApprovalService 弹批准框</li>
 * </ul>
 *
 * <p>审批状态由 CommandApprovalService 内部的 ApprovalStore 持久化，
 * 同一项目内已批准的操作会自动放行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionHook implements IAgentHook {

    private final CommandApprovalService approvalService;

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

        // Command 工具：解析命令并审批
        if ("Command".equalsIgnoreCase(toolName)) {
            String command = extractStringFromJson(input, "command");
            if (command == null || command.isBlank()) {
                return ToolCallDecision.proceed(input);
            }
            ApprovalDecision decision = approvalService.checkAndApprove(command, projectDir, sessionId);
            if (!decision.allowed()) {
                log.info("[PermissionHook] 命令被拒绝: command={}, reason={}", command, decision.message());
                return ToolCallDecision.block("命令被拒绝: " + decision.message());
            }
            return ToolCallDecision.proceed(input);
        }

        // Write 工具：解析文件路径并审批
        if ("Write".equalsIgnoreCase(toolName)) {
            String filePath = extractStringFromJson(input, "filePath", "file_path");
            if (filePath == null || filePath.isBlank()) {
                return ToolCallDecision.proceed(input);
            }
            ApprovalDecision decision = approvalService.checkAndApproveFile(
                    "Write", filePath, "写入", projectDir, sessionId);
            if (!decision.allowed()) {
                log.info("[PermissionHook] 写入被拒绝: file={}, reason={}", filePath, decision.message());
                return ToolCallDecision.block("写入被拒绝: " + decision.message());
            }
            return ToolCallDecision.proceed(input);
        }

        // Edit 工具：解析文件路径并审批
        if ("Edit".equalsIgnoreCase(toolName)) {
            String filePath = extractStringFromJson(input, "filePath", "file_path");
            if (filePath == null || filePath.isBlank()) {
                return ToolCallDecision.proceed(input);
            }
            ApprovalDecision decision = approvalService.checkAndApproveFile(
                    "Edit", filePath, "编辑", projectDir, sessionId);
            if (!decision.allowed()) {
                log.info("[PermissionHook] 编辑被拒绝: file={}, reason={}", filePath, decision.message());
                return ToolCallDecision.block("编辑被拒绝: " + decision.message());
            }
            return ToolCallDecision.proceed(input);
        }

        return ToolCallDecision.proceed(input);
    }

    /**
     * 从 ToolContext 中提取字符串值。
     */
    private String extractString(ToolContext context, String key) {
        if (context == null) {
            return null;
        }
        Object value = context.getContext().get(key);
        return value instanceof String s ? s : null;
    }

    /**
     * 从 JSON 字符串中提取字段值，支持多个候选字段名（兼容 camelCase 和 snake_case）。
     */
    private String extractStringFromJson(String json, String... fieldNames) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = JsonUtils.parse(json);
            for (String field : fieldNames) {
                JsonNode value = node.get(field);
                if (value != null && !value.isNull()) {
                    return value.asText();
                }
            }
        } catch (Exception e) {
            log.debug("[PermissionHook] 解析工具输入 JSON 失败: {}", e.getMessage());
        }
        return null;
    }
}
