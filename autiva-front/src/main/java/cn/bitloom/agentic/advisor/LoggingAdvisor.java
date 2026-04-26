package cn.bitloom.agentic.advisor;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class LoggingAdvisor implements StreamAdvisor {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final AtomicInteger REQUEST_SEQ = new AtomicInteger(0);

    private static final String BORDER_TOP = "┌──────────────────────────────────────────────";
    private static final String BORDER_MID = "├──────────────────────────────────────────────";
    private static final String BORDER_BOTTOM = "└──────────────────────────────────────────────";

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
        int seq = REQUEST_SEQ.incrementAndGet();
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
                    lines.add(String.format("│ %-8s│ %s (%dms)", "Error", error.getMessage(), durationMs));
                    lines.add(BORDER_BOTTOM);

                    lines.forEach(log::error);
                });
    }

    private List<String> buildRequestLines(int seq, ChatClientRequest request) {
        List<String> lines = new ArrayList<>();
        String time = LocalDateTime.now().format(TIME_FORMAT);

        lines.add(String.format("│ %-8s│ %s", "Seq", "#" + seq));
        lines.add(String.format("│ %-8s│ %s", "Time", time));

        var prompt = request.prompt();
        var instructions = prompt.getInstructions();

        AtomicInteger userCount = new AtomicInteger(0);
        AtomicInteger assistantCount = new AtomicInteger(0);
        AtomicInteger toolRespCount = new AtomicInteger(0);
        String lastUserMessage = null;

        for (Message message : instructions) {
            if (message instanceof SystemMessage sysMsg) {
                lines.add(String.format("│ %-8s│ %s", "System", truncate(sysMsg.getText(), 200)));
            } else if (message instanceof UserMessage userMsg) {
                lastUserMessage = userMsg.getText();
                userCount.incrementAndGet();
            } else if (message instanceof AssistantMessage) {
                assistantCount.incrementAndGet();
            } else if (message instanceof ToolResponseMessage) {
                toolRespCount.incrementAndGet();
            }
        }

        if (lastUserMessage != null) {
            lines.add(String.format("│ %-8s│ %s", "User", truncate(lastUserMessage, 500)));
        }

        if (userCount.get() > 1 || assistantCount.get() > 0 || toolRespCount.get() > 0) {
            StringBuilder history = new StringBuilder();
            if (userCount.get() > 1) history.append("User:").append(userCount.get()).append(" ");
            if (assistantCount.get() > 0) history.append("Asst:").append(assistantCount.get()).append(" ");
            if (toolRespCount.get() > 0) history.append("Tool:").append(toolRespCount.get());
            lines.add(String.format("│ %-8s│ %s", "History", history.toString().trim()));
        }

        if (!request.context().isEmpty()) {
            lines.add(String.format("│ %-8s│ %s", "Context", truncate(JSON.toJSONString(request.context()), 300)));
        }

        return lines;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        String normalized = text.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ");
        if (normalized.length() <= maxLen) return normalized;
        return normalized.substring(0, maxLen) + "...(+" + (normalized.length() - maxLen) + " chars)";
    }
}
