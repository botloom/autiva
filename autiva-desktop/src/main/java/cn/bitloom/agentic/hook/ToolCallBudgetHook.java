package cn.bitloom.agentic.hook;

import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.agent.advisor.SessionMemoryAdvisor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具调用预算保护 Hook（对标 learn-claude-code 三层退出控制）。
 * <p>
 * Agent 循环委托给 Spring AI ToolCallingAdvisor 驱动，退出完全依赖 LLM 返回 STOP，
 * 无上限保护。本 Hook 通过机制外挂方式补全保护，不触碰主循环：
 * <ul>
 *   <li>计数：每次实际工具执行（beforeToolCall）递增，按 sessionId+branch 隔离</li>
 *   <li>软提醒：达到预算 80% 时，在下一次模型调用前注入 synthetic 提醒，促使收敛</li>
 *   <li>硬停止：达到预算 100% 时，block 所有工具调用，LLM 收到错误说明后自然收尾</li>
 *   <li>出口：afterConversationRound 清理状态</li>
 * </ul>
 */
@Slf4j
public class ToolCallBudgetHook implements IAgentHook {

    /** 默认工具调用预算 */
    public static final int DEFAULT_MAX_TOOL_CALLS = 50;

    /** 软提醒阈值比例 */
    private static final double WARN_RATIO = 0.8;

    private static final String METADATA_SYNTHETIC = "synthetic";

    private final int maxToolCalls;

    /** 预算状态，按 sessionId+branch 隔离（Agent 实例可能跨 session/branch 复用） */
    private final Map<String, BudgetState> states = new ConcurrentHashMap<>();

    public ToolCallBudgetHook() {
        this(DEFAULT_MAX_TOOL_CALLS);
    }

    public ToolCallBudgetHook(int maxToolCalls) {
        this.maxToolCalls = Math.max(1, maxToolCalls);
    }

    @Override
    public String name() {
        return "ToolCallBudgetHook";
    }

    @Override
    public int order() {
        return 5; // 在 PermissionHook(10) 之前，预算耗尽时无需再走审批
    }

    @Override
    public ToolCallDecision beforeToolCall(String toolName, String input, ToolContext context) {
        String key = stateKey(context);
        if (key == null) {
            return ToolCallDecision.proceed(input);
        }

        BudgetState state = states.computeIfAbsent(key, k -> new BudgetState());
        int used = state.increment();

        if (used > maxToolCalls) {
            log.warn("[ToolCallBudgetHook] 工具调用预算已耗尽（{}/{}），阻止工具: tool={}, key={}",
                    used - 1, maxToolCalls, toolName, key);
            return ToolCallDecision.block(
                    "工具调用预算已耗尽（" + (used - 1) + "/" + maxToolCalls
                            + "）。请立即基于已有信息给出最终回答，不要再尝试调用工具。");
        }
        return ToolCallDecision.proceed(input);
    }

    @Override
    public ChatClientRequest beforeModelCall(ChatClientRequest request) {
        String key = stateKey(request);
        if (key == null) {
            return request;
        }
        BudgetState state = states.get(key);
        if (state == null || state.warned) {
            return request;
        }

        int warnThreshold = (int) Math.ceil(maxToolCalls * WARN_RATIO);
        if (state.count() < warnThreshold) {
            return request;
        }

        state.warned = true;
        String reminder = "[系统提醒] 已使用 " + state.count() + "/" + maxToolCalls
                + " 次工具调用预算，请尽快收敛任务并给出最终回答。";

        try {
            Prompt prompt = request.prompt();
            List<Message> messages = new ArrayList<>(prompt.getInstructions());
            messages.add(UserMessage.builder()
                    .text(reminder)
                    .metadata(Map.of(METADATA_SYNTHETIC, Boolean.TRUE))
                    .build());
            log.info("[ToolCallBudgetHook] 注入预算提醒（synthetic 用户消息）: key={}, {}/{}",
                    key, state.count(), maxToolCalls);
            return request.mutate()
                    .prompt(prompt.mutate().messages(messages).build())
                    .build();
        } catch (Exception e) {
            log.warn("[ToolCallBudgetHook] 注入预算提醒失败", e);
            return request;
        }
    }

    @Override
    public void beforeConversationRound(RuntimeContext ctx) {
        if (ctx == null) {
            return;
        }
        // 轮次开始即重置：上一轮若经中断/异常结束（未触发 afterConversationRound），
        // 残留计数会带入本轮导致误报预算耗尽
        String key = stateKey(ctx.getSessionId(), ctx.getBranch());
        if (key != null) {
            states.remove(key);
        }
    }

    @Override
    public void afterConversationRound(RuntimeContext ctx) {
        if (ctx == null) {
            return;
        }
        String key = stateKey(ctx.getSessionId(), ctx.getBranch());
        if (key != null) {
            states.remove(key);
        }
    }

    private String stateKey(ToolContext context) {
        if (context == null || context.getContext() == null) {
            return null;
        }
        Object sessionId = context.getContext().get("sessionId");
        Object branch = context.getContext().get("branch");
        if (sessionId instanceof String sid) {
            return stateKey(sid, branch instanceof String b ? b : null);
        }
        return null;
    }

    private String stateKey(ChatClientRequest request) {
        try {
            String sessionId = null;
            String branch = null;
            Object sid = request.context().get(ChatMemory.CONVERSATION_ID);
            if (sid instanceof String s) {
                sessionId = s;
            } else {
                Object ctx = request.context().get("runtimeContext");
                if (ctx instanceof RuntimeContext rc) {
                    sessionId = rc.getSessionId();
                    branch = rc.getBranch();
                }
            }
            Object branchParam = request.context().get(SessionMemoryAdvisor.BRANCH_CONTEXT_KEY);
            if (branch == null && branchParam instanceof String bp) {
                branch = bp;
            }
            if (sessionId != null) {
                return stateKey(sessionId, branch);
            }
        } catch (Exception ignored) {
            // 上下文访问异常时跳过提醒注入
        }
        return null;
    }

    private String stateKey(String sessionId, String branch) {
        if (sessionId == null) {
            return null;
        }
        return branch != null ? sessionId + ":" + branch : sessionId + ":root";
    }

    /** 预算状态（按对话轮生命周期） */
    private static final class BudgetState {
        private int count = 0;
        private boolean warned = false;

        synchronized int increment() {
            return ++count;
        }

        synchronized int count() {
            return count;
        }
    }
}
