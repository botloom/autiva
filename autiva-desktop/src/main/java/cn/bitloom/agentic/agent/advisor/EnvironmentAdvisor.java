package cn.bitloom.agentic.agent.advisor;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 环境信息注入 Advisor，将 OS 和时间信息注入到系统提示词中。
 */
@Slf4j
@Builder
public class EnvironmentAdvisor implements StreamAdvisor, CallAdvisor {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public @NonNull String getName() {
        return "EnvironmentAdvisor";
    }

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public @NonNull Flux<ChatClientResponse> adviseStream(@NonNull ChatClientRequest request,
                                                           @NonNull StreamAdvisorChain chain) {
        ChatClientRequest modifiedRequest = injectEnvironment(request);
        return chain.nextStream(modifiedRequest);
    }

    @Override
    public @NonNull ChatClientResponse adviseCall(@NonNull ChatClientRequest request,
                                                   @NonNull CallAdvisorChain chain) {
        ChatClientRequest modifiedRequest = injectEnvironment(request);
        return chain.nextCall(modifiedRequest);
    }

    private ChatClientRequest injectEnvironment(ChatClientRequest request) {
        try {
            String environmentText = buildEnvironmentText();
            if (environmentText.isBlank()) {
                return request;
            }

            Prompt prompt = request.prompt();
            List<Message> messages = new ArrayList<>(prompt.getInstructions());

            boolean systemMessageFound = false;
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i) instanceof SystemMessage sysMsg) {
                    String augmentedText = sysMsg.getText() + "\n\n" + environmentText;
                    messages.set(i, SystemMessage.builder()
                            .text(augmentedText)
                            .metadata(sysMsg.getMetadata())
                            .build());
                    systemMessageFound = true;
                    break;
                }
            }

            if (!systemMessageFound) {
                messages.addFirst(new SystemMessage(environmentText));
            }

            return request.mutate()
                    .prompt(prompt.mutate().messages(messages).build())
                    .build();
        } catch (Exception e) {
            log.warn("[EnvironmentAdvisor] 注入环境信息失败", e);
            return request;
        }
    }

    private String buildEnvironmentText() {
        StringBuilder sb = new StringBuilder();
        sb.append("<environment>");

        String osName = System.getProperty("os.name", "unknown");
        String osVersion = System.getProperty("os.version", "");
        String osArch = System.getProperty("os.arch", "unknown");

        sb.append("\n- OS: ").append(osName);
        if (!osVersion.isEmpty()) {
            sb.append(" ").append(osVersion);
        }
        sb.append(" (").append(osArch).append(")");

        ZoneId zoneId = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        sb.append("\n- Time: ").append(now.format(TIME_FORMATTER));
        sb.append(" (UTC").append(now.getOffset().getId()).append(")");

        sb.append("\n</environment>");
        return sb.toString();
    }
}