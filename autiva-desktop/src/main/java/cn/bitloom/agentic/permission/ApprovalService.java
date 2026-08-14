package cn.bitloom.agentic.permission;

import cn.bitloom.agentic.permission.command.CommandClassifier;
import cn.bitloom.agentic.permission.command.WorkdirEscapeGuard;
import cn.bitloom.agentic.permission.model.ApprovalDecision;
import cn.bitloom.agentic.permission.model.ApprovalRequest;
import cn.bitloom.agentic.permission.model.ApprovalResult;
import cn.bitloom.agentic.permission.model.CommandClass;
import cn.bitloom.bridge.desktop.ToolUIBridge;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 批准编排服务 — 命令与文件写操作审批的唯一入口。
 *
 * <p>命令流程：
 * <ol>
 *   <li>{@link CommandClassifier#classify} 把命令分为 READ/WRITE/DESTRUCTIVE</li>
 *   <li>READ → 直接放行</li>
 *   <li>DESTRUCTIVE → 硬拒绝（不可审批、不可持久化）</li>
 *   <li>WRITE → {@link WorkdirEscapeGuard} 越界检查，越界硬拒</li>
 *   <li>未越界 → 查 {@link ApprovalStore} 是否已永久批准/拒绝</li>
 *   <li>未持久化 → 弹批准框（{@link ToolUIBridge#showApproval}），等用户选择</li>
 *   <li>APPROVE_PERMANENT → 写入 store 持久化</li>
 *   <li>DENY → 返回拒绝</li>
 * </ol>
 *
 * <p>文件流程：越界 → 查 store → 弹框，与命令共用尾部决策逻辑。
 */
@Slf4j
@Component
public class ApprovalService {

    /** 批准框等待超时（默认 10 分钟，与 GuiQuestionHandler 一致） */
    private static final long APPROVAL_TIMEOUT_MINUTES = 10;

    private final ToolUIBridge toolUIBridge;
    private final Map<String, ApprovalStore> storeCache = new ConcurrentHashMap<>();

    public ApprovalService(ToolUIBridge toolUIBridge) {
        this.toolUIBridge = toolUIBridge;
    }

    /**
     * 检查并获取命令批准。
     *
     * @param command    原始命令
     * @param projectDir 项目目录（用于定位 .autiva/）；null 时跳过批准（不拦截）
     * @param sessionId  当前会话 ID（用于路由到对应 TaskCard）
     * @return 批准决策
     */
    public ApprovalDecision checkAndApprove(String command, String projectDir, String sessionId) {
        // 无项目目录时不启用批准
        if (projectDir == null || projectDir.isBlank()) {
            return ApprovalDecision.allow();
        }

        // 1. 分类
        CommandClassifier.Classification classification = CommandClassifier.classify(command);
        if (classification.commandClass() == CommandClass.READ) {
            log.debug("[Approval] READ 命令放行: {}", command);
            return ApprovalDecision.allow();
        }

        // [Gate 1] 破坏性命令硬拒绝：不可绕过、不可审批、不持久化。防止用户误点"永久批准"放行 rm -rf 等危险操作。
        if (classification.commandClass() == CommandClass.DESTRUCTIVE) {
            log.warn("[Approval] 破坏性命令硬拒绝(不可审批): command={}, reason={}", command, classification.reason());
            return ApprovalDecision.deny("命令命中破坏性规则，已禁止执行且无法审批: " + classification.reason());
        }

        // [Gate 2] workdir 越界守卫：命令写操作目标越出项目工作区则硬拒
        String escaped = WorkdirEscapeGuard.checkEscape(command, projectDir);
        if (escaped != null) {
            log.warn("[Approval] 命令目标越出项目工作区(硬拒绝): command={}, target={}, workdir={}", command, escaped, projectDir);
            return ApprovalDecision.deny("命令的操作目标越出项目工作区，已拒绝执行: " + escaped);
        }

        ApprovalStore store = getOrCreateStore(projectDir);
        String prefix = ApprovalStore.extractPrefix(command);
        ApprovalRequest request = ApprovalRequest.forCommand(
                command, classification.commandClass(), classification.reason(), projectDir);
        return resolveViaStore(store, prefix, request, sessionId,
                "命令 '" + prefix + "' 已被永久拒绝",
                "用户拒绝执行命令");
    }

    /**
     * 检查并获取文件写操作批准。
     *
     * <p>批准 key 直接用工具名（如 "Write" / "Edit"），永久批准后该项目内同工具调用都自动放行。
     * work 模式（projectDir 为 null）跳过批准。
     *
     * @param toolName   工具名（"Write" / "Edit"）
     * @param filePath   目标文件绝对路径
     * @param action     文件动作（"创建" / "覆盖" / "编辑"）
     * @param projectDir 项目目录；null 时跳过批准
     * @param sessionId  当前会话 ID
     * @return 批准决策
     */
    public ApprovalDecision checkAndApproveFile(String toolName, String filePath, String action,
                                                String projectDir, String sessionId) {
        // 无项目目录时不启用批准（work 模式）
        if (projectDir == null || projectDir.isBlank()) {
            return ApprovalDecision.allow();
        }

        // [Gate 2] 文件目标越出项目工作区则硬拒：防止"永久批准某工具"后越界写项目外文件
        if (isFileOutsideWorkspace(filePath, projectDir)) {
            log.warn("[Approval] 文件目标越出项目工作区(硬拒绝): tool={}, file={}, workdir={}", toolName, filePath, projectDir);
            return ApprovalDecision.deny("文件操作目标越出项目工作区，已拒绝执行: " + filePath);
        }

        // 文件写操作直接按工具名作为 prefix（不经过 CommandClassifier）
        String prefix = toolName == null ? "" : toolName.trim().toLowerCase(java.util.Locale.ROOT);
        ApprovalStore store = getOrCreateStore(projectDir);
        String reason = action + " 文件";
        ApprovalRequest request = ApprovalRequest.forFile(toolName, filePath, action, reason, projectDir);
        return resolveViaStore(store, prefix, request, sessionId,
                "工具 '" + toolName + "' 已被永久拒绝",
                "用户拒绝执行文件操作");
    }

    /**
     * 公共决策尾部：查 store → 弹批准框 → 依据用户选择决定批准/拒绝。
     *
     * @param permanentDenyMessage 已被永久拒绝时的提示文案
     * @param userDenyMessage      用户本次拒绝时的提示文案
     */
    private ApprovalDecision resolveViaStore(ApprovalStore store, String prefix, ApprovalRequest request,
                                             String sessionId, String permanentDenyMessage, String userDenyMessage) {
        if (store.isDenied(prefix)) {
            log.info("[Approval] 操作已被永久拒绝: prefix={}", prefix);
            return ApprovalDecision.deny(permanentDenyMessage);
        }
        if (store.isAllowed(prefix)) {
            log.debug("[Approval] 操作已被永久批准: prefix={}", prefix);
            return ApprovalDecision.allow();
        }

        // 弹批准框
        ApprovalResult result = askUser(request, sessionId);
        log.info("[Approval] 用户选择: result={}, prefix={}", result, prefix);

        return switch (result) {
            case APPROVE_ONCE -> ApprovalDecision.approveOnce();
            case APPROVE_PERMANENT -> {
                store.allow(prefix);
                yield ApprovalDecision.approvePermanent();
            }
            case DENY -> ApprovalDecision.deny(userDenyMessage);
        };
    }

    /**
     * 弹批准框，阻塞等待用户选择。
     */
    private ApprovalResult askUser(ApprovalRequest request, String sessionId) {
        CompletableFuture<String> future = new CompletableFuture<>();
        try {
            String approvalJson = JsonUtils.toJson(request);
            toolUIBridge.showApproval(approvalJson, future, sessionId);

            String resultJson = future.get(APPROVAL_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            return parseResult(resultJson);
        } catch (TimeoutException e) {
            log.warn("[Approval] 用户超时未响应，默认拒绝: command={}", request.command());
            // 主动完成 future，触发 ToolUIBridge 的 whenComplete 清理残留的审批卡片
            future.complete(null);
            return ApprovalResult.DENY;
        } catch (Exception e) {
            log.error("[Approval] 弹批准框失败，默认拒绝: command={}, error={}",
                    request.command(), e.getMessage(), e);
            return ApprovalResult.DENY;
        }
    }

    private ApprovalResult parseResult(String resultJson) {
        try {
            JsonNode node = JsonUtils.parse(resultJson);
            String result = node.path("result").asText();
            return ApprovalResult.valueOf(result);
        } catch (Exception e) {
            log.warn("[Approval] 解析用户选择失败，默认拒绝: json={}", resultJson);
            return ApprovalResult.DENY;
        }
    }

    /**
     * 文件目标是否越出项目工作区。
     * filePath 为空或无法解析时按"不越界"处理（交给正常审批流程兜底）。
     */
    private boolean isFileOutsideWorkspace(String filePath, String projectDir) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }
        try {
            java.nio.file.Path target = java.nio.file.Path.of(filePath).toAbsolutePath().normalize();
            java.nio.file.Path root = java.nio.file.Path.of(projectDir).toAbsolutePath().normalize();
            return !target.startsWith(root);
        } catch (java.nio.file.InvalidPathException e) {
            log.debug("[Approval] 无法解析文件路径，按不越界处理: file={}, error={}", filePath, e.getMessage());
            return false;
        }
    }

    /**
     * 获取或创建项目的 ApprovalStore（按 projectDir 缓存）。
     */
    private ApprovalStore getOrCreateStore(String projectDir) {
        return storeCache.computeIfAbsent(projectDir, dir -> {
            try {
                return new ApprovalStore(Path.of(dir));
            } catch (Exception e) {
                log.error("[Approval] 创建 ApprovalStore 失败: dir={}, error={}", dir, e.getMessage(), e);
                throw new IllegalStateException("无法创建批准存储: " + dir, e);
            }
        });
    }
}
