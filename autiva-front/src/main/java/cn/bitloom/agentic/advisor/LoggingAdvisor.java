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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class LoggingAdvisor implements StreamAdvisor {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final AtomicInteger REQUEST_SEQ = new AtomicInteger(0);

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

        logRequest(seq, chatClientRequest);

        AtomicReference<StringBuilder> fullText = new AtomicReference<>(new StringBuilder());
        AtomicReference<StringBuilder> toolCallBuilder = new AtomicReference<>(new StringBuilder());
        AtomicInteger toolCallCount = new AtomicInteger(0);

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
                                toolCallCount.incrementAndGet();
                                toolCallBuilder.get()
                                        .append("\n      │  └── Tool #")
                                        .append(toolCallCount.get())
                                        .append(": ")
                                        .append(toolCall.name())
                                        .append("(")
                                        .append(toolCall.arguments())
                                        .append(")");
                            }
                        }
                    }
                })
                .doOnComplete(() -> {
                    log.info("[#{}][{}] ✅ LLM 响应完成 ({}ms)", seq, LocalDateTime.now().format(TIME_FORMAT),
                            (System.nanoTime() - startNano) / 1_000_000);

                    if (!fullText.get().isEmpty()) {
                        log.info("【响应文本】: {}", truncate(fullText.get().toString(), 1000));
                    } else if (toolCallCount.get() > 0) {
                        log.info("【工具调用】({}): {}", toolCallCount.get(), toolCallBuilder.get());
                    } else {
                        log.info("【响应】: (空)");
                    }

                    log.info("══════════════════════════════════════════════════════════");
                })
                .doOnError(error -> {
                    log.error("[#{}][{}] ❌ LLM 错误: {}", seq, LocalDateTime.now().format(TIME_FORMAT), error.getMessage());
                    log.error("══════════════════════════════════════════════════════════", error);
                });
    }

    private void logRequest(int seq, ChatClientRequest request) {
        String time = LocalDateTime.now().format(TIME_FORMAT);

        log.info("");
        log.info("══════════════════════════════════════════════════════════");
        log.info("[#{}][{}] 📥 LLM 请求", seq, time);

        var prompt = request.prompt();
        var instructions = prompt.getInstructions();

        AtomicInteger userCount = new AtomicInteger(0);
        AtomicInteger assistantCount = new AtomicInteger(0);
        AtomicInteger toolRespCount = new AtomicInteger(0);
        String lastUserMessage = null;

        for (Message message : instructions) {
            if (message instanceof SystemMessage sysMsg) {
                log.info("【系统消息】: {}", truncate(sysMsg.getText(), 200));
            } else if (message instanceof UserMessage userMsg) {
                lastUserMessage = userMsg.getText();
                userCount.incrementAndGet();
            } else if (message instanceof AssistantMessage assistantMsg) {
                assistantCount.incrementAndGet();
            } else if (message instanceof ToolResponseMessage toolMsg) {
                toolRespCount.incrementAndGet();
            }
        }

        if (lastUserMessage != null) {
            log.info("【用户消息】: {}", truncate(lastUserMessage, 500));
        }

        StringBuilder summary = new StringBuilder();
        summary.append("【历史】");
        if (userCount.get() > 1) summary.append("用户消息:").append(userCount.get());
        if (assistantCount.get() > 0) summary.append(" 助手消息:").append(assistantCount.get());
        if (toolRespCount.get() > 0) summary.append(" 工具响应:").append(toolRespCount.get());
        log.info("{}", summary);

        if (!request.context().isEmpty()) {
            log.info("【上下文】: {}", JSON.toJSONString(request.context()));
        }

        log.info("══════════════════════════════════════════════════════════");
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...(更多" + (text.length() - maxLen) + "字符)";
    }
}
