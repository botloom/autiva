package cn.bitloom.agentic.agent.advisor;

import cn.bitloom.agentic.skill.SkillManager;
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

/**
 * 技能上下文注入 Advisor，将技能描述注入到系统提示词中。
 */
@Slf4j
@Builder
public class SkillContextAdvisor implements StreamAdvisor, CallAdvisor {

    private final SkillManager skillManager;

    @Override
    public @NonNull String getName() {
        return "SkillContextAdvisor";
    }

    @Override
    public int getOrder() {
        return 210;
    }

    @Override
    public @NonNull Flux<ChatClientResponse> adviseStream(@NonNull ChatClientRequest request,
                                                           @NonNull StreamAdvisorChain chain) {
        ChatClientRequest modifiedRequest = injectSkillContext(request);
        return chain.nextStream(modifiedRequest);
    }

    @Override
    public @NonNull ChatClientResponse adviseCall(@NonNull ChatClientRequest request,
                                                   @NonNull CallAdvisorChain chain) {
        ChatClientRequest modifiedRequest = injectSkillContext(request);
        return chain.nextCall(modifiedRequest);
    }

    private ChatClientRequest injectSkillContext(ChatClientRequest request) {
        if (skillManager == null) {
            return request;
        }

        try {
            String skillDesc = buildSkillDescriptions();
            if (skillDesc == null || skillDesc.isBlank()) {
                return request;
            }

            Prompt prompt = request.prompt();
            List<Message> messages = new ArrayList<>(prompt.getInstructions());

            boolean systemMessageFound = false;
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i) instanceof SystemMessage sysMsg) {
                    String augmentedText = sysMsg.getText() + "\n\n" + skillDesc;
                    messages.set(i, SystemMessage.builder()
                            .text(augmentedText)
                            .metadata(sysMsg.getMetadata())
                            .build());
                    systemMessageFound = true;
                    break;
                }
            }

            if (!systemMessageFound) {
                messages.addFirst(new SystemMessage(skillDesc));
            }

            return request.mutate()
                    .prompt(prompt.mutate().messages(messages).build())
                    .build();
        } catch (Exception e) {
            log.warn("[SkillContextAdvisor] 注入技能上下文失败", e);
            return request;
        }
    }

    private String buildSkillDescriptions() {
        try {
            String descriptions = skillManager.getDescription();
            if (descriptions == null || descriptions.isBlank()) {
                return null;
            }
            return "<skills>\n" + descriptions + "\n</skills>";
        } catch (Exception e) {
            log.debug("[SkillContextAdvisor] 获取技能描述失败", e);
            return null;
        }
    }
}