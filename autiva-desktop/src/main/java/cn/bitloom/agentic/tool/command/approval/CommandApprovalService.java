package cn.bitloom.agentic.tool.command.approval;

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
 * 命令批准编排服务 — CommandTool 的唯一入口。
 *
 * <p>流程：
 * <ol>
 *   <li>{@link CommandClassifier#classify} 把命令分为 READ/WRITE/DESTRUCTIVE</li>
 *   <li>READ → 直接放行</li>
 *   <li>WRITE/DESTRUCTIVE → 查 {@link ApprovalStore} 是否已永久批准/拒绝</li>
 *   <li>未持久化 → 弹批准框（{@link ToolUIBridge#showApproval}），等用户选择</li>
 *   <li>APPROVE_PERMANENT → 写入 store 持久化</li>
 *   <li>DENY → 返回拒绝</li>
 * </ol>
 */
@Slf4j
@Component
public class CommandApprovalService {

    /** 批准框等待超时（默认 10 分钟，与 GuiQuestionHandler 一致） */
    private static final long APPROVAL_TIMEOUT_MINUTES = 10;

    private final ToolUIBridge toolUIBridge;
    private final Map<String, ApprovalStore> storeCache = new ConcurrentHashMap<>();

    public CommandApprovalService(ToolUIBridge toolUIBridge) {
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

        // 2. 查 store
        ApprovalStore store = getOrCreateStore(projectDir);
        String prefix = ApprovalStore.extractPrefix(command);
        if (store.isDenied(prefix)) {
            log.info("[Approval] 命令已被永久拒绝: prefix={}, command={}", prefix, command);
            return ApprovalDecision.deny("命令 '" + prefix + "' 已被永久拒绝");
        }
        if (store.isAllowed(prefix)) {
            log.debug("[Approval] 命令已被永久批准: prefix={}, command={}", prefix, command);
            return ApprovalDecision.allow();
        }

        // 3. 弹批准框
        ApprovalRequest request = ApprovalRequest.forCommand(
                command, classification.commandClass(), classification.reason(), projectDir);
        ApprovalResult result = askUser(request, sessionId);

        log.info("[Approval] 用户选择: result={}, prefix={}, command={}", result, prefix, command);

        return switch (result) {
            case APPROVE_ONCE -> ApprovalDecision.approveOnce();
            case APPROVE_PERMANENT -> {
                store.allow(prefix);
                yield ApprovalDecision.approvePermanent();
            }
            case DENY -> ApprovalDecision.deny("用户拒绝执行命令");
        };
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

        // 文件写操作直接按工具名作为 prefix（不经过 CommandClassifier）
        String prefix = toolName == null ? "" : toolName.trim().toLowerCase(java.util.Locale.ROOT);

        ApprovalStore store = getOrCreateStore(projectDir);
        if (store.isDenied(prefix)) {
            log.info("[Approval] 文件操作已被永久拒绝: tool={}, file={}", toolName, filePath);
            return ApprovalDecision.deny("工具 '" + toolName + "' 已被永久拒绝");
        }
        if (store.isAllowed(prefix)) {
            log.debug("[Approval] 文件操作已被永久批准: tool={}, file={}", toolName, filePath);
            return ApprovalDecision.allow();
        }

        // 弹批准框
        String reason = action + " 文件";
        ApprovalRequest request = ApprovalRequest.forFile(toolName, filePath, action, reason, projectDir);
        ApprovalResult result = askUser(request, sessionId);

        log.info("[Approval] 用户选择: result={}, tool={}, file={}", result, toolName, filePath);

        return switch (result) {
            case APPROVE_ONCE -> ApprovalDecision.approveOnce();
            case APPROVE_PERMANENT -> {
                store.allow(prefix);
                yield ApprovalDecision.approvePermanent();
            }
            case DENY -> ApprovalDecision.deny("用户拒绝执行文件操作");
        };
    }

    /**
     * 弹批准框，阻塞等待用户选择。
     */
    private ApprovalResult askUser(ApprovalRequest request, String sessionId) {
        try {
            String approvalJson = JsonUtils.toJson(request);
            CompletableFuture<String> future = new CompletableFuture<>();
            toolUIBridge.showApproval(approvalJson, future, sessionId);

            String resultJson = future.get(APPROVAL_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            return parseResult(resultJson);
        } catch (TimeoutException e) {
            log.warn("[Approval] 用户超时未响应，默认拒绝: command={}", request.command());
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
