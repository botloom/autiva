package cn.bitloom.agentic.agent.advisor;

import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentDefinitionManager;
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 子智能体上下文注入 Advisor，将子智能体描述注入到系统提示词中。
 */
@Slf4j
@Builder
public class SubagentContextAdvisor implements StreamAdvisor, CallAdvisor {

    private final AgentDefinitionManager definitionManager;
    private final AgentDefinition definition;

    @Override
    public @NonNull String getName() {
        return "SubagentContextAdvisor";
    }

    @Override
    public int getOrder() {
        return 220;
    }

    @Override
    public @NonNull Flux<ChatClientResponse> adviseStream(@NonNull ChatClientRequest request,
                                                           @NonNull StreamAdvisorChain chain) {
        ChatClientRequest modifiedRequest = injectSubagentContext(request);
        return chain.nextStream(modifiedRequest);
    }

    @Override
    public @NonNull ChatClientResponse adviseCall(@NonNull ChatClientRequest request,
                                                   @NonNull CallAdvisorChain chain) {
        ChatClientRequest modifiedRequest = injectSubagentContext(request);
        return chain.nextCall(modifiedRequest);
    }

    private ChatClientRequest injectSubagentContext(ChatClientRequest request) {
        if (definition == null || definitionManager == null) {
            return request;
        }

        try {
            String subagentDesc = buildSubagentDescriptions();
            if (subagentDesc == null || subagentDesc.isBlank()) {
                return request;
            }

            Prompt prompt = request.prompt();
            List<Message> messages = new ArrayList<>(prompt.getInstructions());

            boolean systemMessageFound = false;
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i) instanceof SystemMessage sysMsg) {
                    String augmentedText = sysMsg.getText() + "\n\n" + subagentDesc;
                    messages.set(i, SystemMessage.builder()
                            .text(augmentedText)
                            .metadata(sysMsg.getMetadata())
                            .build());
                    systemMessageFound = true;
                    break;
                }
            }

            if (!systemMessageFound) {
                messages.addFirst(new SystemMessage(subagentDesc));
            }

            return request.mutate()
                    .prompt(prompt.mutate().messages(messages).build())
                    .build();
        } catch (Exception e) {
            log.warn("[SubagentContextAdvisor] 注入子智能体上下文失败", e);
            return request;
        }
    }

    private String buildSubagentDescriptions() {
        try {
            String descriptions = definition.subagents().stream()
                    .map(name -> {
                        AgentDefinition def = definitionManager.getDefinition(name);
                        return def != null ? "- " + name + ": " + def.description() : "";
                    })
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining("\n"));

            if (descriptions.isBlank()) {
                return null;
            }
            return "<subagents>\n" + descriptions + "\n</subagents>";
        } catch (Exception e) {
            log.debug("[SubagentContextAdvisor] 获取子智能体描述失败", e);
            return null;
        }
    }
}