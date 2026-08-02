package cn.bitloom.agentic.agent.advisor;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LLM 调用日志 Advisor。
 * <p>
 * 打印内容覆盖：Agent 标识、Session/Branch、请求消息摘要（含历史 ToolResponse 内容）、
 * 响应元数据（Model / FinishReason / Token usage）、错误场景下累积上下文与完整消息序列。
 * <p>
 * 正常路径保持精简，Text 截断 1000；错误路径 Text 不截断，并追加 MsgSeq。
 */
@Slf4j
@Builder
public class LoggingAdvisor implements StreamAdvisor, CallAdvisor {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static final String BORDER_MID = "├──────────────────────────────────────────────";
    private static final String BORDER_BOTTOM = "└──────────────────────────────────────────────";

    /** 工具响应内容截断阈值（请求日志中显示历史 ToolResponse 的内容） */
    private static final int TOOL_RESP_BRIEF_LEN = 200;
    /** 正常路径 Text 截断阈值 */
    private static final int TEXT_BRIEF_LEN = 1000;
    /** 错误场景下 Text 不截断的硬上限（防止日志爆炸） */
    private static final int TEXT_ERROR_LEN = 8000;

    private final AtomicInteger requestSeq = new AtomicInteger(0);

    /** Agent 名称（主/子智能体标识），由 Agent.build() 注入 */
    @Nullable
    private final String agentName;

    @Override
    public @NonNull String getName() {
        return "LoggingAdvisor";
    }

    @Override
    public int getOrder() {
        return 1;
    }

    @Override
    public @NonNull Flux<ChatClientResponse> adviseStream(@NonNull ChatClientRequest chatClientRequest, @NonNull StreamAdvisorChain streamAdvisorChain) {
        int seq = this.requestSeq.incrementAndGet();
        long startNano = System.nanoTime();
        LocalDateTime requestTime = LocalDateTime.now();

        AtomicReference<StringBuilder> fullText = new AtomicReference<>(new StringBuilder());
        AtomicReference<StringBuilder> toolCallBuilder = new AtomicReference<>(new StringBuilder());
        AtomicInteger toolCallCount = new AtomicInteger(0);
        // 流式响应中最后一个非空 finishReason（错误时用于诊断是否被截断）
        AtomicReference<String> lastFinishReason = new AtomicReference<>();
        // 流式响应中累积的 model 名（首个非空值）
        AtomicReference<String> responseModel = new AtomicReference<>();

        Flux<ChatClientResponse> responseFlux = streamAdvisorChain.nextStream(chatClientRequest);

        return responseFlux
                .doOnNext(response -> {
                    ChatResponse chatResponse = response.chatResponse();
                    if (chatResponse != null) {
                        var output = chatResponse.getResult().getOutput();

                        if (output.getText() != null && !output.getText().isEmpty()) {
                            fullText.get().append(output.getText());
                        }

                        if (!output.getToolCalls().isEmpty()) {
                            for (var toolCall : output.getToolCalls()) {
                                int num = toolCallCount.incrementAndGet();
                                toolCallBuilder.get()
                                        .append(String.format("%n│ %-8s│ %s(%s)",
                                                "#" + num,
                                                toolCall.name(),
                                                truncate(toolCall.arguments(), 300)));
                            }
                        }

                        // 捕获响应元数据（流式可能多次推送，取首个非空值）
                        captureResponseMetadata(chatResponse, lastFinishReason, responseModel);
                    }
                })
                .doOnComplete(() -> {
                    long durationMs = (System.nanoTime() - startNano) / 1_000_000;

                    List<String> lines = new ArrayList<>();
                    lines.add(formatHeader(seq, requestTime, durationMs));
                    lines.addAll(buildRequestLines(chatClientRequest));
                    lines.add(BORDER_MID);

                    appendResponseLines(lines, responseModel.get(), lastFinishReason.get(),
                            fullText.get().toString(), toolCallCount.get(), toolCallBuilder.get().toString(),
                            false);

                    lines.add(BORDER_BOTTOM);
                    lines.forEach(log::info);
                })
                .doOnError(error -> {
                    long durationMs = (System.nanoTime() - startNano) / 1_000_000;

                    List<String> lines = new ArrayList<>();
                    lines.add(formatHeader(seq, requestTime, durationMs));
                    lines.addAll(buildRequestLines(chatClientRequest));
                    lines.add(BORDER_MID);

                    lines.add(String.format("│ %-8s│ %s: %s", "Error", error.getClass().getSimpleName(), error.getMessage()));

                    // 错误场景下打印已累积的响应内容，Text 不截断（最多 TEXT_ERROR_LEN）
                    appendResponseLines(lines, responseModel.get(), lastFinishReason.get(),
                            fullText.get().toString(), toolCallCount.get(), toolCallBuilder.get().toString(),
                            true);

                    // 打印完整消息序列，便于排查 tool_calls/tool_response 配对问题
                    lines.addAll(buildMessageSequenceLines(chatClientRequest));
                    lines.add(BORDER_BOTTOM);

                    lines.forEach(log::error);
                    // 打印完整堆栈
                    log.error("[LoggingAdvisor] LLM stream error detail", error);
                });
    }

    @Override
    public @NonNull ChatClientResponse adviseCall(@NonNull ChatClientRequest chatClientRequest, @NonNull CallAdvisorChain callAdvisorChain) {
        int seq = this.requestSeq.incrementAndGet();
        long startNano = System.nanoTime();
        LocalDateTime requestTime = LocalDateTime.now();

        ChatClientResponse response;
        try {
            response = callAdvisorChain.nextCall(chatClientRequest);
        } catch (RuntimeException e) {
            long durationMs = (System.nanoTime() - startNano) / 1_000_000;

            List<String> lines = new ArrayList<>();
            lines.add(formatHeader(seq, requestTime, durationMs));
            lines.addAll(buildRequestLines(chatClientRequest));
            lines.add(BORDER_MID);
            lines.add(String.format("│ %-8s│ %s: %s", "Error", e.getClass().getSimpleName(), e.getMessage()));
            lines.addAll(buildMessageSequenceLines(chatClientRequest));
            lines.add(BORDER_BOTTOM);

            lines.forEach(log::error);
            log.error("[LoggingAdvisor] LLM call error detail", e);
            throw e;
        }

        long durationMs = (System.nanoTime() - startNano) / 1_000_000;

        List<String> lines = new ArrayList<>();
        lines.add(formatHeader(seq, requestTime, durationMs));
        lines.addAll(buildRequestLines(chatClientRequest));
        lines.add(BORDER_MID);

        ChatResponse chatResponse = response.chatResponse();
        String responseModel = null;
        String finishReason = null;
        String text = "";
        StringBuilder toolCallBuilder = new StringBuilder();
        int toolCallCount = 0;

        if (chatResponse != null) {
            responseModel = chatResponse.getMetadata() != null ? chatResponse.getMetadata().getModel() : null;
            Generation generation = chatResponse.getResult();
            if (generation != null) {
                if (generation.getMetadata() != null) {
                    finishReason = generation.getMetadata().getFinishReason();
                }
                var output = generation.getOutput();
                text = output.getText() != null ? output.getText() : "";
                var toolCalls = output.getToolCalls();
                if (!toolCalls.isEmpty()) {
                    toolCallCount = toolCalls.size();
                    int num = 1;
                    for (var toolCall : toolCalls) {
                        toolCallBuilder.append(String.format("%n│ %-8s│ %s(%s)",
                                "#" + num,
                                toolCall.name(),
                                truncate(toolCall.arguments(), 300)));
                        num++;
                    }
                }
            }
        }

        appendResponseLines(lines, responseModel, finishReason, text, toolCallCount,
                toolCallBuilder.toString(), false);

        // usage 单独从响应元数据获取（call 模式下通常完整可用）
        if (chatResponse != null && chatResponse.getMetadata() != null) {
            appendUsageLine(lines, chatResponse.getMetadata().getUsage());
        }

        lines.add(BORDER_BOTTOM);
        lines.forEach(log::info);

        return response;
    }

    // ===================== 内部辅助方法 =====================

    /**
     * 构造日志头行：┌─ LLM [#seq] agent · time · durationms ──
     * agent 为空时退化为 ┌─ LLM [#seq] time · durationms ──
     */
    private String formatHeader(int seq, LocalDateTime requestTime, long durationMs) {
        String time = requestTime.format(TIME_FORMAT);
        if (agentName != null && !agentName.isBlank()) {
            return String.format("┌─ LLM [#%d] %s · %s · %dms ────────────────────",
                    seq, agentName, time, durationMs);
        }
        return String.format("┌─ LLM [#%d] %s · %dms ────────────────────",
                seq, time, durationMs);
    }

    /**
     * 捕获流式响应中的 finishReason 和 model（取首个非空值，避免被后续 chunk 覆盖）。
     */
    private void captureResponseMetadata(ChatResponse chatResponse,
                                         AtomicReference<String> lastFinishReason,
                                         AtomicReference<String> responseModel) {
        try {
            if (chatResponse.getResult() != null
                    && chatResponse.getResult().getMetadata() != null
                    && lastFinishReason.get() == null) {
                String fr = chatResponse.getResult().getMetadata().getFinishReason();
                if (fr != null && !fr.isBlank()) {
                    lastFinishReason.set(fr);
                }
            }
        } catch (Exception ignored) {
            // 元数据访问失败不影响主流程
        }
        try {
            if (chatResponse.getMetadata() != null && responseModel.get() == null) {
                String m = chatResponse.getMetadata().getModel();
                if (m != null && !m.isBlank()) {
                    responseModel.set(m);
                }
            }
        } catch (Exception ignored) {
            // 同上
        }
    }

    /**
     * 拼接响应区日志行：Model / Finish / Text / Tools。
     * @param errorMode 错误场景下 Text 不截断（最多 TEXT_ERROR_LEN）
     */
    private void appendResponseLines(List<String> lines,
                                     String responseModel,
                                     String finishReason,
                                     String text,
                                     int toolCallCount,
                                     String toolCallLines,
                                     boolean errorMode) {
        if (responseModel != null && !responseModel.isBlank()) {
            lines.add(String.format("│ %-8s│ %s", "Model", responseModel));
        }
        if (finishReason != null && !finishReason.isBlank()) {
            lines.add(String.format("│ %-8s│ %s", "Finish", finishReason));
        }

        if (text != null && !text.isEmpty()) {
            int limit = errorMode ? TEXT_ERROR_LEN : TEXT_BRIEF_LEN;
            lines.add(String.format("│ %-8s│ %s", "Text", truncate(text, limit)));
        }

        if (toolCallCount > 0) {
            lines.add(String.format("│ %-8s│ (%d calls)", "Tools", toolCallCount));
            for (String toolLine : toolCallLines.split("\n")) {
                if (!toolLine.isBlank()) {
                    lines.add(toolLine);
                }
            }
        }

        if ((text == null || text.isEmpty()) && toolCallCount == 0) {
            lines.add(String.format("│ %-8s│ %s", "Result", "(empty)"));
        }
    }

    /**
     * 追加 Token usage 行。Usage 为 null 或全 0 时不输出。
     */
    private void appendUsageLine(List<String> lines, Usage usage) {
        if (usage == null) {
            return;
        }
        Integer in = usage.getPromptTokens();
        Integer out = usage.getCompletionTokens();
        Integer total = usage.getTotalTokens();
        if ((in == null || in <= 0) && (out == null || out <= 0) && (total == null || total <= 0)) {
            return;
        }
        lines.add(String.format("│ %-8s│ in:%s out:%s total:%s",
                "Tokens",
                in != null ? in : "-",
                out != null ? out : "-",
                total != null ? total : "-"));
    }

    private List<String> buildRequestLines(ChatClientRequest request) {
        List<String> lines = new ArrayList<>();

        // Agent 标识（若已在 header 打印则不重复）
        if (agentName != null && !agentName.isBlank()) {
            lines.add(String.format("│ %-8s│ %s", "Agent", agentName));
        }

        // 从上下文提取 sessionId 和 branch，便于追踪主/子智能体
        Object sessionIdVal = request.context().get(ChatMemory.CONVERSATION_ID);
        if (sessionIdVal instanceof String sid) {
            lines.add(String.format("│ %-8s│ %s", "Session", sid));
        }
        Object branchVal = request.context().get(SessionMemoryAdvisor.BRANCH_CONTEXT_KEY);
        if (branchVal instanceof String br) {
            lines.add(String.format("│ %-8s│ %s", "Branch", br));
        }

        var prompt = request.prompt();
        var instructions = prompt.getInstructions();

        AtomicInteger userCount = new AtomicInteger(0);
        AtomicInteger assistantCount = new AtomicInteger(0);
        AtomicInteger assistantToolCallCount = new AtomicInteger(0);
        AtomicInteger toolRespCount = new AtomicInteger(0);
        String lastUserMessage = null;
        // 收集历史 ToolResponse 内容（最多打印最近 5 条，避免日志过长）
        List<String> toolRespLines = new ArrayList<>();

        for (Message message : instructions) {
            if (message instanceof SystemMessage sysMsg) {
                lines.add(String.format("│ %-8s│ %s", "System", truncate(sysMsg.getText(), 200)));
            } else if (message instanceof UserMessage userMsg) {
                lastUserMessage = userMsg.getText();
                userCount.incrementAndGet();
            } else if (message instanceof AssistantMessage am) {
                assistantCount.incrementAndGet();
                if (am.hasToolCalls()) {
                    assistantToolCallCount.incrementAndGet();
                }
            } else if (message instanceof ToolResponseMessage trm) {
                toolRespCount.incrementAndGet();
                for (var r : trm.getResponses()) {
                    toolRespLines.add(String.format("│ %-8s│ %s: %s",
                            "ToolResp",
                            r.name(),
                            truncate(safeResponseData(r.responseData()), TOOL_RESP_BRIEF_LEN)));
                    if (toolRespLines.size() >= 5) {
                        break;
                    }
                }
            }
        }

        if (lastUserMessage != null) {
            lines.add(String.format("│ %-8s│ %s", "User", truncate(lastUserMessage, 500)));
        }

        if (userCount.get() > 1 || assistantCount.get() > 0 || toolRespCount.get() > 0) {
            StringBuilder history = new StringBuilder();
            if (userCount.get() > 1) history.append("User:").append(userCount.get()).append(" ");
            if (assistantCount.get() > 0) history.append("Asst:").append(assistantCount.get());
            if (assistantToolCallCount.get() > 0) history.append("(").append(assistantToolCallCount.get()).append("tc) ");
            if (toolRespCount.get() > 0) history.append("Tool:").append(toolRespCount.get());
            lines.add(String.format("│ %-8s│ %s", "History", history.toString().trim()));
        }

        // 历史工具响应内容（便于排查子智能体调用失败等问题）
        if (!toolRespLines.isEmpty()) {
            lines.addAll(toolRespLines);
            if (toolRespCount.get() > toolRespLines.size()) {
                lines.add(String.format("│ %-8s│ ...(%d more omitted)",
                        "ToolResp", toolRespCount.get() - toolRespLines.size()));
            }
        }

        return lines;
    }

    private String safeResponseData(Object responseData) {
        return responseData != null ? responseData.toString() : "(null)";
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        String normalized = text.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ");
        if (normalized.length() <= maxLen) return normalized;
        return normalized.substring(0, maxLen) + "...(+" + (normalized.length() - maxLen) + " chars)";
    }

    /**
     * 构建完整消息序列日志行，用于错误排查。
     * 打印每条消息的类型、tool_calls 数量、tool_response 数量，便于发现配对问题。
     */
    private List<String> buildMessageSequenceLines(ChatClientRequest request) {
        List<String> lines = new ArrayList<>();
        var instructions = request.prompt().getInstructions();
        lines.add(String.format("│ %-8s│ (%d messages in sequence)", "MsgSeq", instructions.size()));

        int idx = 0;
        for (Message message : instructions) {
            idx++;
            String type;
            String detail;
            if (message instanceof SystemMessage sysMsg) {
                type = "SYSTEM";
                detail = truncate(sysMsg.getText(), 100);
            } else if (message instanceof UserMessage userMsg) {
                type = "USER";
                detail = truncate(userMsg.getText(), 100);
            } else if (message instanceof AssistantMessage am) {
                type = "ASST";
                if (am.hasToolCalls()) {
                    var calls = am.getToolCalls();
                    detail = "tool_calls=[" + calls.stream()
                            .map(tc -> tc.name() + "(" + tc.id() + ")")
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("") + "]";
                } else {
                    detail = truncate(am.getText(), 100);
                }
            } else if (message instanceof ToolResponseMessage trm) {
                type = "TOOL";
                detail = "responses=[" + trm.getResponses().stream()
                        .map(r -> r.name() + "(" + r.id() + ")")
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("") + "]";
            } else {
                type = message.getMessageType().name();
                detail = truncate(message.getText(), 100);
            }
            lines.add(String.format("│   #%-4d  %-7s %s", idx, type, detail));
        }
        return lines;
    }
}
