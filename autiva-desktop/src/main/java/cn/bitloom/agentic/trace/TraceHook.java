package cn.bitloom.agentic.trace;

import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.hook.IAgentHook;
import cn.bitloom.agentic.hook.ToolCallDecision;
import cn.bitloom.agentic.verify.Feedback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Trace 累积 Hook。
 *
 * <p>实现 {@link IAgentHook}，在 L1 Agent Loop 的 6 个回调点累积 Trace 数据，
 * 在对话轮次结束时通过 {@link TraceRecorder} 落盘。</p>
 *
 * <p>与 {@link cn.bitloom.agentic.verify.VerificationHook} 独立协作：
 * TraceHook 负责记录，VerificationHook 负责校验。两者通过 RuntimeContext.params
 * 中的共享 Trace 对象协同（VerificationHook 校验产生 Feedback 时回写到 Trace）。</p>
 *
 * <p>order=50，在 VerificationHook（order=100）之前执行，
 * 确保 beforeConversationRound 先创建 Trace，VerificationHook 才能写入 feedback。</p>
 */
@Slf4j
@Component
public class TraceHook implements IAgentHook {

    private static final String TRACE_KEY = "trace.current";

    private final TraceRecorder recorder;

    /** ThreadLocal 保存当前 Trace，供 afterConversationRound 使用 */
    private final ThreadLocal<Trace> currentTrace = new ThreadLocal<>();
    /** ThreadLocal 保存当前轮次的最后一次产出，供 afterConversationRound 记录 */
    private final ThreadLocal<AssistantMessage> lastOutput = new ThreadLocal<>();
    /** ThreadLocal 保存当前工具调用的起始时间 */
    private final ThreadLocal<Long> toolCallStart = new ThreadLocal<>();
    /** ThreadLocal 保存当前工具调用的输入参数 */
    private final ThreadLocal<String> toolCallInput = new ThreadLocal<>();

    public TraceHook(TraceRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public String name() {
        return "TraceHook";
    }

    @Override
    public int order() {
        return 50;
    }

    // ========== ③ 对话轮次级 ==========

    @Override
    public void beforeConversationRound(RuntimeContext ctx) {
        String sessionId = ctx.getConversationId();
        String agentId = ctx.getSession() != null ? ctx.getSession().getAgentId() : "unknown";
        String userMessage = extractLastUserMessage(ctx);
        Trace trace = Trace.start(generateTraceId(), sessionId, agentId, userMessage);
        currentTrace.set(trace);
        ctx.param(TRACE_KEY, trace);
        lastOutput.remove();
        toolCallStart.remove();
        toolCallInput.remove();
        log.debug("[TraceHook] 对话轮次开始 traceId={} agentId={}", trace.traceId(), agentId);
    }

    // ========== ② 模型调用级 ==========

    @Override
    public void afterModelCall(ChatClientRequest request, ChatClientResponse response) {
        try {
            Trace trace = currentTrace.get();
            if (trace == null) {
                return;
            }
            currentTrace.set(trace.withModelCall());

            if (response != null && response.chatResponse() != null) {
                AssistantMessage output = response.chatResponse().getResult().getOutput();
                if (output != null && output.getText() != null && !output.getText().isEmpty()) {
                    lastOutput.set(output);
                }
            }
        } catch (Exception e) {
            log.warn("[TraceHook] afterModelCall 记录失败: {}", e.getMessage());
        }
    }

    // ========== ① 工具调用级 ==========

    @Override
    public ToolCallDecision beforeToolCall(String toolName, String input, ToolContext context) {
        toolCallStart.set(System.currentTimeMillis());
        toolCallInput.set(input);
        return ToolCallDecision.proceed(input);
    }

    @Override
    public String afterToolCall(String toolName, String result, ToolContext context) {
        try {
            Trace trace = currentTrace.get();
            Long start = toolCallStart.get();
            long duration = start != null ? System.currentTimeMillis() - start : 0L;
            String args = toolCallInput.get();

            if (trace != null) {
                ToolCallRecord record = ToolCallRecord.success(toolName, args, result, duration);
                currentTrace.set(trace.withToolCall(record));
            }
        } catch (Exception e) {
            log.warn("[TraceHook] afterToolCall 记录失败: {}", e.getMessage());
        } finally {
            toolCallStart.remove();
            toolCallInput.remove();
        }
        return result;
    }

    // ========== ③ 对话轮次级：最终落盘 ==========

    @Override
    public void afterConversationRound() {
        Trace trace = currentTrace.get();
        AssistantMessage output = lastOutput.get();
        try {
            if (trace == null) {
                return;
            }

            // 记录最终产出
            if (output != null && output.getText() != null) {
                trace = trace.withFinalOutput(output.getText());
            }

            // 计算 duration 并落盘
            long duration = System.currentTimeMillis() - trace.timestamp();
            trace = trace.withDuration(duration);

            // 如果 verifyMethod 还是 "skipped"，说明 VerificationHook 未启用或未执行
            // 此时根据 feedbacks 判断 verified 状态
            if ("skipped".equals(trace.verifyMethod())) {
                boolean hasError = trace.feedbacks() != null && trace.feedbacks().stream()
                        .anyMatch(fb -> !fb.passed() && fb.shouldBlock());
                trace = trace.withVerified(!hasError, "skipped");
            }

            recorder.record(trace);
            log.debug("[TraceHook] Trace 已落盘 traceId={} duration={}ms toolCalls={} verified={}",
                    trace.traceId(), trace.durationMs(),
                    trace.toolCalls() != null ? trace.toolCalls().size() : 0,
                    trace.verified());
        } catch (Exception e) {
            log.warn("[TraceHook] 落盘失败: {}", e.getMessage());
        } finally {
            currentTrace.remove();
            lastOutput.remove();
            toolCallStart.remove();
            toolCallInput.remove();
        }
    }

    // ========== 公共方法：供 VerificationHook 写入 feedback ==========

    /**
     * 向当前 Trace 追加 Feedback。
     *
     * <p>供 {@link cn.bitloom.agentic.verify.VerificationHook} 在校验时调用，
     * 将校验反馈累积到 Trace 中。</p>
     */
    public void appendFeedback(RuntimeContext ctx, Feedback fb) {
        if (ctx == null || fb == null) {
            return;
        }
        Trace trace = (Trace) ctx.getParam(TRACE_KEY);
        if (trace == null) {
            // 如果 ctx 中没有，尝试从 ThreadLocal 取
            trace = currentTrace.get();
        }
        if (trace == null) {
            return;
        }
        Trace updated = trace.withFeedback(fb);
        currentTrace.set(updated);
        ctx.param(TRACE_KEY, updated);
    }

    /**
     * 标记当前 Trace 的校验结果。
     */
    public void markVerified(RuntimeContext ctx, boolean verified, String method) {
        if (ctx == null) {
            return;
        }
        Trace trace = (Trace) ctx.getParam(TRACE_KEY);
        if (trace == null) {
            trace = currentTrace.get();
        }
        if (trace == null) {
            return;
        }
        Trace updated = trace.withVerified(verified, method);
        currentTrace.set(updated);
        ctx.param(TRACE_KEY, updated);
    }

    // ========== 辅助方法 ==========

    private String extractLastUserMessage(RuntimeContext ctx) {
        if (ctx.getSession() == null || ctx.getSession().getMessages() == null) {
            return null;
        }
        var messages = ctx.getSession().getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            var msg = messages.get(i);
            if (msg instanceof UserMessage um) {
                return um.getText();
            }
        }
        return null;
    }

    private String generateTraceId() {
        return "trc_" + UUID.randomUUID().toString().substring(0, 12);
    }
}
