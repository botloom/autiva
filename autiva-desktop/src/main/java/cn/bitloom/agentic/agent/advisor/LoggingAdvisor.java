package cn.bitloom.agentic.agent.advisor;

import cn.bitloom.util.JsonUtils;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Builder
public class LoggingAdvisor implements StreamAdvisor, CallAdvisor {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static final String BORDER_MID = "├──────────────────────────────────────────────";
    private static final String BORDER_BOTTOM = "└──────────────────────────────────────────────";

    private final AtomicInteger requestSeq = new AtomicInteger(0);

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

        AtomicReference<StringBuilder> fullText = new AtomicReference<>(new StringBuilder());
        AtomicReference<StringBuilder> toolCallBuilder = new AtomicReference<>(new StringBuilder());
        AtomicInteger toolCallCount = new AtomicInteger(0);

        List<String> requestLines = buildRequestLines(seq, chatClientRequest);

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
                    }
                })
                .doOnComplete(() -> {
                    long durationMs = (System.nanoTime() - startNano) / 1_000_000;
                    String time = LocalDateTime.now().format(TIME_FORMAT);

                    List<String> lines = new ArrayList<>();
                    lines.add(String.format("┌─ LLM [#%d] %s · %dms ──────────────────────────", seq, time, durationMs));
                    lines.addAll(requestLines);
                    lines.add(BORDER_MID);

                    if (!fullText.get().isEmpty()) {
                        lines.add(String.format("│ %-8s│ %s", "Text", truncate(fullText.get().toString(), 1000)));
                    } else if (toolCallCount.get() > 0) {
                        lines.add(String.format("│ %-8s│ (%d calls)", "Tools", toolCallCount.get()));
                        for (String toolLine : toolCallBuilder.get().toString().split("\n")) {
                            if (!toolLine.isBlank()) {
                                lines.add(toolLine);
                            }
                        }
                    } else {
                        lines.add(String.format("│ %-8s│ %s", "Result", "(empty)"));
                    }

                    lines.add(BORDER_BOTTOM);

                    lines.forEach(log::info);
                })
                .doOnError(error -> {
                    long durationMs = (System.nanoTime() - startNano) / 1_000_000;
                    String time = LocalDateTime.now().format(TIME_FORMAT);

                    List<String> lines = new ArrayList<>();
                    lines.add(String.format("┌─ LLM [#%d] %s · %dms ──────────────────────────", seq, time, durationMs));
                    lines.addAll(requestLines);
                    lines.add(BORDER_MID);
                    lines.add(String.format("│ %-8s│ %s: %s", "Error", error.getClass().getSimpleName(), error.getMessage()));
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

        List<String> requestLines = buildRequestLines(seq, chatClientRequest);

        ChatClientResponse response;
        try {
            response = callAdvisorChain.nextCall(chatClientRequest);
        } catch (RuntimeException e) {
            long durationMs = (System.nanoTime() - startNano) / 1_000_000;
            String time = LocalDateTime.now().format(TIME_FORMAT);

            List<String> lines = new ArrayList<>();
            lines.add(String.format("┌─ LLM [#%d] %s · %dms ──────────────────────────", seq, time, durationMs));
            lines.addAll(requestLines);
            lines.add(BORDER_MID);
            lines.add(String.format("│ %-8s│ %s: %s", "Error", e.getClass().getSimpleName(), e.getMessage()));
            lines.addAll(buildMessageSequenceLines(chatClientRequest));
            lines.add(BORDER_BOTTOM);

            lines.forEach(log::error);
            log.error("[LoggingAdvisor] LLM call error detail", e);
            throw e;
        }

        long durationMs = (System.nanoTime() - startNano) / 1_000_000;
        String time = LocalDateTime.now().format(TIME_FORMAT);

        List<String> lines = new ArrayList<>();
        lines.add(String.format("┌─ LLM [#%d] %s · %dms ──────────────────────────", seq, time, durationMs));
        lines.addAll(requestLines);
        lines.add(BORDER_MID);

        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse != null) {
            var output = chatResponse.getResult().getOutput();
            String text = output.getText();
            var toolCalls = output.getToolCalls();

            if (text != null && !text.isEmpty()) {
                lines.add(String.format("│ %-8s│ %s", "Text", truncate(text, 1000)));
            }

            if (!toolCalls.isEmpty()) {
                lines.add(String.format("│ %-8s│ (%d calls)", "Tools", toolCalls.size()));
                int num = 1;
                for (var toolCall : toolCalls) {
                    lines.add(String.format("│ %-8s│ %s(%s)",
                            "#" + num,
                            toolCall.name(),
                            truncate(toolCall.arguments(), 300)));
                    num++;
                }
            }

            if ((text == null || text.isEmpty()) && toolCalls.isEmpty()) {
                lines.add(String.format("│ %-8s│ %s", "Result", "(empty)"));
            }
        } else {
            lines.add(String.format("│ %-8s│ %s", "Result", "(empty)"));
        }

        lines.add(BORDER_BOTTOM);
        lines.forEach(log::info);

        return response;
    }

    private List<String> buildRequestLines(int seq, ChatClientRequest request) {
        List<String> lines = new ArrayList<>();
        String time = LocalDateTime.now().format(TIME_FORMAT);

        lines.add(String.format("│ %-8s│ %s", "Seq", "#" + seq));
        lines.add(String.format("│ %-8s│ %s", "Time", time));

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

        return lines;
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
