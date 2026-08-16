package cn.bitloom.agentic.tool.plan;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.util.concurrent.CompletableFuture;

/**
 * 退出计划模式工具（Plan Mode 闭环出口）。
 *
 * <p>计划模式下智能体完成调研后调用本工具提交实施计划，阻塞等待用户决策：
 * <ul>
 *   <li>{@link #DECISION_APPROVED}：计划已批准（VM 侧已退出计划模式并重建智能体，
 *       当前轮结束后自动发起执行轮）</li>
 *   <li>{@link #FEEDBACK_PREFIX}文本：用户反馈调整意见，智能体据此修改计划后重新提交</li>
 *   <li>{@link #DECISION_ABANDONED}：用户放弃计划并退出计划模式</li>
 * </ul>
 */
@Slf4j
public class ExitPlanModeTool extends AbstractTool<ExitPlanModeTool.Input> {

    public static final String DECISION_APPROVED = "APPROVED";
    public static final String DECISION_ABANDONED = "ABANDONED";
    public static final String FEEDBACK_PREFIX = "FEEDBACK::";

    /** 计划提交通知：智能体提交计划（工具线程），future 完成值即用户决策 */
    public interface PlanApprovalListener {
        void onPlanSubmitted(String sessionId, String plan, CompletableFuture<String> future);
    }

    private static final String DESCRIPTION = """
            提交最终实施计划并等待用户决策（仅计划模式可用）。\
            计划必须具体到文件级：列出将创建/修改的文件与改动要点、实施步骤顺序、风险与回滚方式。\
            调用后等待用户决策：批准则计划模式结束并自动开始执行；有反馈则按反馈调整后重新提交。""";

    private final PlanApprovalListener listener;

    private ExitPlanModeTool(PlanApprovalListener listener) {
        super("ExitPlanMode", DESCRIPTION, Input.class);
        this.listener = listener;
    }

    public record Input(@ToolParam(description = "完整实施计划（markdown，具体到文件级）") String plan) {
    }

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        String sessionId = extractString(toolContext, "sessionId");
        if (input.plan() == null || input.plan().isBlank()) {
            return ToolResult.error("plan 不能为空");
        }
        CompletableFuture<String> future = new CompletableFuture<>();
        listener.onPlanSubmitted(sessionId, input.plan().trim(), future);
        String decision;
        try {
            decision = future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error("等待计划决策被中断");
        } catch (Exception e) {
            log.warn("[Plan] 计划决策等待失败: {}", e.getMessage());
            return ToolResult.error("计划决策失败: " + e.getMessage());
        }
        if (DECISION_APPROVED.equals(decision)) {
            return ToolResult.success("计划已获用户批准。计划模式已结束，无需再次确认，简短收尾结束本轮即可（执行将由系统在下一轮自动开始）。");
        }
        if (DECISION_ABANDONED.equals(decision)) {
            return ToolResult.success("用户放弃了本次计划并退出计划模式。");
        }
        if (decision != null && decision.startsWith(FEEDBACK_PREFIX)) {
            return ToolResult.success("用户未批准并给出反馈：" + decision.substring(FEEDBACK_PREFIX.length())
                    + "\n请根据反馈调整计划，并再次调用 ExitPlanMode 重新提交。");
        }
        return ToolResult.error("未知决策结果: " + decision);
    }

    static String extractString(ToolContext context, String key) {
        if (context != null && context.getContext() != null) {
            Object value = context.getContext().get(key);
            if (value instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private PlanApprovalListener listener;

        private Builder() {
        }

        public Builder listener(PlanApprovalListener listener) {
            this.listener = listener;
            return this;
        }

        public ExitPlanModeTool build() {
            Assert.notNull(this.listener, "必须提供listener");
            return new ExitPlanModeTool(this.listener);
        }
    }
}
