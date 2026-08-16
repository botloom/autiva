package cn.bitloom.agentic.goal;

import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.event.AbstractEvent;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.hook.IAgentHook;
import cn.bitloom.agentic.session.EventFilter;
import cn.bitloom.agentic.session.ISessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 目标闭环 Hook（对标 learn-claude-code s17 Goal Loop）。
 *
 * <p>「没有新 tool_use 只说明本轮结束，不能证明目标达成」。本 Hook 挂
 * {@code afterConversationRound}（HookAdvisor 仅在 finishReason=STOP 时回调，
 * 即本轮无工具调用、LLM 认为已完成时），触发独立判断器（{@link GoalJudge}，
 * 另一次轻量模型调用、零工具）复核目标：
 *
 * <ul>
 *   <li>ok=true → 目标达成（achieved），通知用户</li>
 *   <li>impossible=true → 停止（impossible），向用户报告原因</li>
 *   <li>ok=false → 把 reason 作为 synthetic user 消息写入 session 并自动续轮
 *       （GoalManager.continueRound → ViewModel 发起下一次 runStream）</li>
 *   <li>defer：后台任务运行中不判断（等 task_notification 注入后 resumeIfDeferred 恢复）</li>
 *   <li>出口保护：连续阻止上限 {@value #MAX_BLOCKED} 次；判断器调用失败时停止自动续轮、
 *       保留目标、把错误交给用户——绝不宣称成功</li>
 * </ul>
 *
 * <p>仅主智能体（branch == null）生效；Goal 由 GoalSetTool 激活。
 */
@Slf4j
public class GoalJudgeHook implements IAgentHook {

    /** 连续阻止上限（出口保护） */
    public static final int MAX_BLOCKED = 3;

    /** 判定依据的最大事件数（最近窗口） */
    static final int MAX_EVENTS = 80;

    /** 提交给判断器的对话文本上限（字符） */
    static final int MAX_ROUND_CHARS = 24000;

    static final String GOAL_FEEDBACK_TEMPLATE = """
            <goal_feedback>
            目标尚未达成。独立判断器的判定原因：%s
            请继续推进目标。注意：运行验证命令后，必须把命令与结果明确写进对话，供独立判断器检查。
            </goal_feedback>""";

    private final GoalManager goalManager;
    private final ISessionManager sessionManager;
    private final GoalJudge judge;
    private final GoalListener listener;

    /** 目标状态监听器（UI 反馈：状态卡片 / 通知） */
    public interface GoalListener {
        GoalListener NOOP = (sessionId, state) -> {
        };

        /**
         * @param sessionId 会话
         * @param state     最新目标状态（含 status / judgeCount / lastReason）
         */
        void onGoalUpdated(String sessionId, GoalState state);
    }

    private GoalJudgeHook(GoalManager goalManager, ISessionManager sessionManager, GoalJudge judge,
            GoalListener listener) {
        this.goalManager = goalManager;
        this.sessionManager = sessionManager;
        this.judge = judge;
        this.listener = listener != null ? listener : GoalListener.NOOP;
    }

    @Override
    public String name() {
        return "GoalJudgeHook";
    }

    @Override
    public int order() {
        return 30; // PermissionHook(10)/TodoReminderHook(20) 之后，MemoryExtractionHook(40) 之前
    }

    @Override
    public void afterConversationRound(RuntimeContext ctx) {
        if (ctx == null || ctx.getSessionId() == null || ctx.getBranch() != null) {
            return; // 仅主智能体
        }
        String sessionId = ctx.getSessionId();
        GoalState state = goalManager.getGoal(sessionId).orElse(null);
        if (state == null || !state.isActive()) {
            return;
        }

        // defer：后台任务运行中不判断，等 task_notification 注入后 resumeIfDeferred 恢复
        if (goalManager.hasBackgroundWork()) {
            state.setDeferred(true);
            log.info("[Goal] 后台任务运行中，本轮跳过判定（defer）: session={}", sessionId);
            return;
        }

        // 异步判定，不阻塞回合结束回调
        CompletableFuture.runAsync(() -> {
            try {
                evaluate(sessionId, state);
            } catch (Exception e) {
                // 判断器调用失败：停止自动续轮、保留目标、错误交给用户——绝不宣称成功
                state.setLastReason("判断器调用失败: " + e.getMessage());
                state.setStatus(GoalState.STATUS_BLOCKED);
                listener.onGoalUpdated(sessionId, state);
                log.warn("[Goal] 判断器调用失败，停止自动续轮: session={}: {}", sessionId, e.getMessage());
            }
        });
    }

    private void evaluate(String sessionId, GoalState state) {
        String roundText = formatRound(readRecentEvents(sessionId));
        if (roundText.isBlank()) {
            return;
        }

        state.incrementJudgeCount();
        GoalJudge.Verdict verdict = judge.judge(state.getGoal(), roundText);

        if (verdict.ok()) {
            state.setStatus(GoalState.STATUS_ACHIEVED);
            state.setLastReason(verdict.reason());
            listener.onGoalUpdated(sessionId, state);
            log.info("[Goal] 目标达成: session={}, judgeCount={}", sessionId, state.getJudgeCount());
            return;
        }

        if (verdict.impossible()) {
            state.setStatus(GoalState.STATUS_IMPOSSIBLE);
            state.setLastReason(verdict.reason());
            listener.onGoalUpdated(sessionId, state);
            log.info("[Goal] 目标无法达成: session={}, reason={}", sessionId, verdict.reason());
            return;
        }

        // 出口保护：连续阻止达到上限时停止自动续轮，保留目标等待用户
        state.incrementBlockedCount();
        state.setLastReason(verdict.reason());
        if (state.getBlockedCount() >= MAX_BLOCKED) {
            state.setStatus(GoalState.STATUS_BLOCKED);
            listener.onGoalUpdated(sessionId, state);
            log.warn("[Goal] 连续阻止 {} 次，停止自动续轮: session={}", state.getBlockedCount(), sessionId);
            return;
        }

        listener.onGoalUpdated(sessionId, state);
        log.info("[Goal] 目标未达成（{}/{}），自动续轮: session={}, reason={}",
                state.getBlockedCount(), MAX_BLOCKED, sessionId, verdict.reason());

        // 自动续轮：reason 作为续行消息（不落盘，作为下一轮输入由 SessionMemoryAdvisor 持久化）
        String message = GOAL_FEEDBACK_TEMPLATE.formatted(verdict.reason() == null || verdict.reason().isBlank()
                ? "（未给出具体原因）" : verdict.reason());
        if (!goalManager.continueRound(sessionId, message)) {
            log.warn("[Goal] 无可用续轮回调（ViewModel 未注册），本轮不续行: session={}", sessionId);
        }
    }

    /** 读取最近事件窗口（root 可见，与主智能体视角一致） */
    private List<AbstractEvent> readRecentEvents(String sessionId) {
        List<AbstractEvent> all = sessionManager.getEvents(sessionId, EventFilter.active());
        int from = Math.max(0, all.size() - MAX_EVENTS);
        return all.subList(from, all.size());
    }

    /** 事件窗口 → 对话文本（用户 / 助手 / 工具结果） */
    private String formatRound(List<AbstractEvent> events) {
        StringBuilder sb = new StringBuilder();
        for (AbstractEvent event : events) {
            if (!(event instanceof MessageEvent me)) {
                continue;
            }
            String role = switch (me.getMessageType()) {
                case USER -> "用户";
                case ASSISTANT -> "助手";
                case TOOL -> "工具结果";
                default -> null;
            };
            if (role == null) {
                continue;
            }
            String text = me.getText();
            if (me.isToolResponse() && me.getResponses() != null) {
                text = me.getResponses().stream()
                        .map(r -> r.name() + ": " + (r.responseData() != null ? r.responseData() : ""))
                        .reduce("", (a, b) -> a + b);
            }
            if (text == null || text.isBlank()) {
                continue;
            }
            sb.append(role).append(": ").append(text).append('\n');
            if (sb.length() > MAX_ROUND_CHARS) {
                break;
            }
        }
        String result = sb.toString();
        return result.length() <= MAX_ROUND_CHARS ? result : result.substring(0, MAX_ROUND_CHARS) + "\n...(已截断)";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private GoalManager goalManager;
        private ISessionManager sessionManager;
        private ChatClient chatClient;
        private GoalListener listener;

        private Builder() {
        }

        public Builder goalManager(GoalManager goalManager) {
            this.goalManager = goalManager;
            return this;
        }

        public Builder sessionManager(ISessionManager sessionManager) {
            this.sessionManager = sessionManager;
            return this;
        }

        /** 判断器使用的独立 ChatClient（轻量模型调用，零工具） */
        public Builder chatClient(ChatClient chatClient) {
            this.chatClient = chatClient;
            return this;
        }

        public Builder listener(GoalListener listener) {
            this.listener = listener;
            return this;
        }

        public GoalJudgeHook build() {
            if (goalManager == null || sessionManager == null || chatClient == null) {
                throw new IllegalStateException("goalManager/sessionManager/chatClient 均不可为空");
            }
            return new GoalJudgeHook(goalManager, sessionManager, new GoalJudge(chatClient), listener);
        }
    }
}
