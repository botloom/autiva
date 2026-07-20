package cn.bitloom.agentic.trace;

import cn.bitloom.agentic.verify.Feedback;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * L1+L2 执行轨迹的完整结构化记录，供 L4 爬山循环分析。
 *
 * <p>每条 Trace 对应一次对话轮次（用户消息 → Agent 处理 → 最终产出），
 * 包含 L1 工具调用序列、L2 校验反馈、最终产出及元数据。</p>
 *
 * @param traceId      唯一ID
 * @param sessionId    会话ID
 * @param agentId      Agent 名
 * @param timestamp    起始时间戳
 * @param userMessage  用户原始请求
 * @param toolCalls    工具调用序列
 * @param finalOutput  最终产出
 * @param attemptCount 模型调用次数（含递归重试）
 * @param feedbacks    L2 校验反馈
 * @param verified     是否通过 L2 校验
 * @param verifyMethod 校验方式："deterministic" / "llm" / "exhausted" / "skipped"
 * @param durationMs   总耗时（毫秒）
 * @param totalTokens  总 token 消耗（暂记 0，后续接入 UsageAdvisor）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Trace(
        String traceId,
        String sessionId,
        String agentId,
        long timestamp,
        String userMessage,
        List<ToolCallRecord> toolCalls,
        String finalOutput,
        int attemptCount,
        List<Feedback> feedbacks,
        boolean verified,
        String verifyMethod,
        long durationMs,
        long totalTokens
) {
    public static Trace start(String traceId, String sessionId, String agentId, String userMessage) {
        return new Trace(
                traceId, sessionId, agentId, System.currentTimeMillis(),
                userMessage,
                new ArrayList<>(),
                null,
                0,
                new ArrayList<>(),
                false,
                "skipped",
                0L,
                0L
        );
    }

    public Trace withToolCall(ToolCallRecord call) {
        List<ToolCallRecord> calls = new ArrayList<>(this.toolCalls);
        calls.add(call);
        return new Trace(traceId, sessionId, agentId, timestamp, userMessage,
                calls, finalOutput, attemptCount, feedbacks, verified, verifyMethod, durationMs, totalTokens);
    }

    public Trace withModelCall() {
        return new Trace(traceId, sessionId, agentId, timestamp, userMessage,
                toolCalls, finalOutput, attemptCount + 1, feedbacks, verified, verifyMethod, durationMs, totalTokens);
    }

    public Trace withFinalOutput(String output) {
        return new Trace(traceId, sessionId, agentId, timestamp, userMessage,
                toolCalls, output, attemptCount, feedbacks, verified, verifyMethod, durationMs, totalTokens);
    }

    public Trace withFeedback(Feedback fb) {
        List<Feedback> list = new ArrayList<>(this.feedbacks);
        list.add(fb);
        return new Trace(traceId, sessionId, agentId, timestamp, userMessage,
                toolCalls, finalOutput, attemptCount, list, verified, verifyMethod, durationMs, totalTokens);
    }

    public Trace withVerified(boolean verified, String method) {
        long duration = System.currentTimeMillis() - timestamp;
        return new Trace(traceId, sessionId, agentId, timestamp, userMessage,
                toolCalls, finalOutput, attemptCount, feedbacks, verified, method, duration, totalTokens);
    }

    public Trace withDuration(long durationMs) {
        return new Trace(traceId, sessionId, agentId, timestamp, userMessage,
                toolCalls, finalOutput, attemptCount, feedbacks, verified, verifyMethod, durationMs, totalTokens);
    }
}
