package cn.bitloom.agentic.verify;

import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneStore;
import cn.bitloom.agentic.evolve.gene.GeneType;
import cn.bitloom.agentic.hook.IAgentHook;
import cn.bitloom.agentic.hook.ToolCallDecision;
import cn.bitloom.agentic.trace.TraceHook;
import cn.bitloom.agentic.verify.grader.LlmGrader;
import cn.bitloom.agentic.verify.grader.OutputGrader;
import cn.bitloom.agentic.verify.grader.ToolGrader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ToolContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * L2 校验循环核心：实现 IAgentHook，在三个粒度上介入 L1 Agent Loop。
 * <p>
 * 设计参考 Loop Engineering 最佳实践：
 * 1. **工具级即时反馈**：beforeToolCall/afterToolCall 利用 ToolCallingAdvisor 递归特性，LLM 自动修正重试
 * 2. **模型级记录**：afterModelCall 记录 Trace（Phase 3 完善）
 * 3. **对话级校验**：afterConversationRound 调用 OutputGrader + LlmGrader，失败通过 EventBus 触发重试
 * <p>
 * 通过 agent.md 的 verification: true 启用。
 */
@Slf4j
public class VerificationHook implements IAgentHook {

    private static final String STATE_KEY = "verificationState";
    private static final String RETRY_COUNT_KEY = "verificationRetryCount";

    private final GeneStore geneStore;
    private final List<ToolGrader> toolGraders;
    private final List<OutputGrader> outputGraders;
    private final LlmGrader llmGrader;
    private final EvolveConfig config;
    private final TraceHook traceHook;

    /** ThreadLocal 保存当前轮次的 RuntimeContext，供 afterConversationRound 使用 */
    private final ThreadLocal<RuntimeContext> currentCtx = new ThreadLocal<>();
    /** ThreadLocal 保存当前轮次的最后一次产出，供 afterConversationRound 校验 */
    private final ThreadLocal<AssistantMessage> lastOutput = new ThreadLocal<>();

    public VerificationHook(GeneStore geneStore,
                            List<ToolGrader> toolGraders,
                            List<OutputGrader> outputGraders,
                            LlmGrader llmGrader,
                            EvolveConfig config,
                            TraceHook traceHook) {
        this.geneStore = geneStore;
        this.toolGraders = toolGraders != null ? toolGraders : List.of();
        this.outputGraders = outputGraders != null ? outputGraders : List.of();
        this.llmGrader = llmGrader;
        this.config = config;
        this.traceHook = traceHook;
    }

    @Override
    public String name() {
        return "VerificationHook";
    }

    @Override
    public int order() {
        return 100;
    }

    // ========== ③ 对话轮次级 ==========

    @Override
    public void beforeConversationRound(RuntimeContext ctx) {
        currentCtx.set(ctx);
        lastOutput.remove();
        if (ctx.getParam(STATE_KEY) == null) {
            ctx.param(STATE_KEY, new VerificationState());
        }
        if (ctx.getParam(RETRY_COUNT_KEY) == null) {
            ctx.param(RETRY_COUNT_KEY, new AtomicInteger(0));
        }
        log.debug("[VerificationHook] 对话轮次开始 sessionId={}", ctx.getConversationId());
    }

    // ========== ② 模型调用级 ==========

    @Override
    public void afterModelCall(ChatClientRequest request, ChatClientResponse response) {
        try {
            if (response == null || response.chatResponse() == null) {
                return;
            }
            AssistantMessage output = response.chatResponse().getResult().getOutput();
            if (output != null) {
                lastOutput.set(output);
                VerificationState state = getState(request);
                if (state != null) {
                    state.modelCallCount++;
                }
            }
        } catch (Exception e) {
            log.warn("[VerificationHook] afterModelCall 记录失败: {}", e.getMessage());
        }
    }

    // ========== ① 工具调用级 ==========

    @Override
    public ToolCallDecision beforeToolCall(String toolName, String input, ToolContext context) {
        for (ToolGrader grader : toolGraders) {
            if (!grader.supports(toolName)) {
                continue;
            }
            try {
                List<Gene> rubrics = geneStore.findByTypeAndTarget(GeneType.RUBRIC, toolName);
                Feedback fb = grader.checkArgs(toolName, input, rubrics);
                if (!fb.passed() && fb.shouldBlock()) {
                    log.info("[VerificationHook] 工具参数校验未通过 tool={} fb={}", toolName, fb.message());
                    VerificationState state = getState(context);
                    if (state != null) {
                        state.blockedToolCalls++;
                        state.addFeedback(fb);
                    }
                    recordFeedback(fb);
                    return ToolCallDecision.block("参数校验未通过: " + fb.message());
                }
            } catch (Exception e) {
                log.warn("[VerificationHook] 工具参数校验异常 tool={}: {}", toolName, e.getMessage());
            }
        }
        return ToolCallDecision.proceed(input);
    }

    @Override
    public String afterToolCall(String toolName, String result, ToolContext context) {
        for (ToolGrader grader : toolGraders) {
            if (!grader.supports(toolName)) {
                continue;
            }
            try {
                List<Gene> rubrics = geneStore.findByTypeAndTarget(GeneType.RUBRIC, toolName);
                Feedback fb = grader.checkResult(toolName, result, rubrics);
                if (!fb.passed() && fb.shouldBlock()) {
                    log.info("[VerificationHook] 工具结果校验未通过 tool={} fb={}", toolName, fb.message());
                    VerificationState state = getState(context);
                    if (state != null) {
                        state.failedToolResults++;
                        state.addFeedback(fb);
                    }
                    recordFeedback(fb);
                    // 修改 result 为错误信息，LLM 在下一轮递归中自动修正
                    return "{\"status\":\"ERROR\",\"message\":\"结果校验未通过: "
                            + escapeJson(fb.message()) + "\",\"data\":{},\"rawOutput\":\""
                            + escapeJson(fb.message()) + "\"}";
                }
            } catch (Exception e) {
                log.warn("[VerificationHook] 工具结果校验异常 tool={}: {}", toolName, e.getMessage());
            }
        }
        return result;
    }

    // ========== ③ 对话轮次级：最终产出校验 ==========

    @Override
    public void afterConversationRound() {
        RuntimeContext ctx = currentCtx.get();
        AssistantMessage output = lastOutput.get();
        try {
            if (ctx == null || output == null) {
                return;
            }

            VerificationState state = (VerificationState) ctx.getParam(STATE_KEY);
            AtomicInteger retryCount = (AtomicInteger) ctx.getParam(RETRY_COUNT_KEY);
            if (state == null || retryCount == null) {
                return;
            }

            String agentId = ctx.getSession() != null ? ctx.getSession().getAgentId() : "unknown";
            List<Gene> rubrics = geneStore.findByTypeAndTarget(GeneType.RUBRIC, agentId);

            // 1. 确定性产出校验
            for (OutputGrader grader : outputGraders) {
                if (!grader.enabled()) continue;
                try {
                    Feedback fb = grader.verify(output, ctx, rubrics);
                    state.addFeedback(fb);
                    recordFeedback(fb);
                    if (!fb.passed() && fb.shouldBlock()) {
                        handleFailure(ctx, state, retryCount, fb);
                        markVerified(ctx, false, "deterministic");
                        return;
                    }
                } catch (Exception e) {
                    log.warn("[VerificationHook] 产出校验异常 {}: {}", grader.getClass().getSimpleName(), e.getMessage());
                }
            }

            // 2. LLM-as-judge 校验（慢、贵，仅确定性通过后执行）
            if (llmGrader != null) {
                try {
                    List<Feedback> llmFeedbacks = llmGrader.grade(output, ctx);
                    for (Feedback fb : llmFeedbacks) {
                        state.addFeedback(fb);
                        recordFeedback(fb);
                        if (!fb.passed() && fb.shouldBlock()) {
                            handleFailure(ctx, state, retryCount, fb);
                            markVerified(ctx, false, "llm");
                            return;
                        }
                    }
                } catch (Exception e) {
                    log.warn("[VerificationHook] LLM 评判异常: {}", e.getMessage());
                }
            }

            // 3. 全部通过 → 记录成功
            state.verified = true;
            markVerified(ctx, true, llmGrader != null ? "deterministic+llm" : "deterministic");
            log.info("[VerificationHook] 校验通过 sessionId={} attempts={} feedbacks={}",
                    ctx.getConversationId(), state.modelCallCount, state.feedbacks.size());

        } finally {
            // 清理 ThreadLocal，避免内存泄漏
            currentCtx.remove();
            lastOutput.remove();
        }
    }

    private void handleFailure(RuntimeContext ctx, VerificationState state,
                               AtomicInteger retryCount, Feedback fb) {
        state.verified = false;
        log.info("[VerificationHook] 校验未通过 sessionId={} retry={}/{} fb={}",
                ctx.getConversationId(), retryCount.get(), config.getMaxConversationRetries(), fb.message());

        if (retryCount.incrementAndGet() <= config.getMaxConversationRetries()) {
            // 注入反馈，通过 EventBus 触发重试（等价于用户说"刚才的不对，xxx有问题"）
            String retryPrompt = buildRetryPrompt(fb);
            String sessionId = ctx.getConversationId();
            if (sessionId != null) {
                EventBus.publishIn(MessageEvent.userMessage(sessionId, retryPrompt));
                log.info("[VerificationHook] 已发布重试消息 sessionId={} retry={}",
                        sessionId, retryCount.get());
            }
        } else {
            log.warn("[VerificationHook] 重试次数耗尽 sessionId={}，放行当前产出",
                    ctx.getConversationId());
        }
    }

    private String buildRetryPrompt(Feedback fb) {
        return "[L2 校验反馈] 上一次的产出未通过质量校验，请根据以下反馈重新回答：\n\n"
                + (fb.message() != null ? fb.message() : "（未给出具体原因）")
                + "\n\n请修正上述问题后重新给出完整答案。";
    }

    // ========== 辅助方法 ==========

    private void recordFeedback(Feedback fb) {
        if (traceHook == null || fb == null) return;
        try {
            traceHook.appendFeedback(currentCtx.get(), fb);
        } catch (Exception e) {
            log.debug("[VerificationHook] 写入 Trace feedback 失败: {}", e.getMessage());
        }
    }

    private void markVerified(RuntimeContext ctx, boolean verified, String method) {
        if (traceHook == null || ctx == null) return;
        try {
            traceHook.markVerified(ctx, verified, method);
        } catch (Exception e) {
            log.debug("[VerificationHook] 标记 Trace verified 失败: {}", e.getMessage());
        }
    }

    private VerificationState getState(ChatClientRequest request) {
        if (request == null || request.context() == null) return null;
        Object ctxObj = request.context().get("runtimeContext");
        if (!(ctxObj instanceof RuntimeContext ctx)) return null;
        return (VerificationState) ctx.getParam(STATE_KEY);
    }

    private VerificationState getState(ToolContext context) {
        // ToolContext 的上下文来自 Agent.runStream/runBlock 的 toolContext(ctx.getParams())，
        // 因此 RuntimeContext.param(k, v) 设置的值会出现在 ToolContext 中。
        if (context == null) return null;
        Object stateObj = context.getContext().get(STATE_KEY);
        return stateObj instanceof VerificationState vs ? vs : null;
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ========== 内部状态类 ==========

    /**
     * 单轮对话的校验状态，保存在 RuntimeContext.params 中。
     * 每个 VerificationHook 实例共享同一个 RuntimeContext，但每个对话轮次创建新的 VerificationState。
     */
    public static class VerificationState {
        private int modelCallCount = 0;
        private int blockedToolCalls = 0;
        private int failedToolResults = 0;
        private boolean verified = false;
        private final List<Feedback> feedbacks = new ArrayList<>();

        void addFeedback(Feedback fb) {
            if (fb != null) {
                feedbacks.add(fb);
            }
        }

        public int getModelCallCount() { return modelCallCount; }
        public int getBlockedToolCalls() { return blockedToolCalls; }
        public int getFailedToolResults() { return failedToolResults; }
        public boolean isVerified() { return verified; }
        public List<Feedback> getFeedbacks() { return List.copyOf(feedbacks); }
    }
}
