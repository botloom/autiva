package cn.bitloom.agentic.evolve.gene;

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
import java.util.Map;

/**
 * 基因注入 Advisor，将选中的基因指令注入到系统提示词中。
 * <p>
 * 在 SkillContextAdvisor（order=210）之后执行（order=220），
 * 通过 {@link GeneSelector} 选择与当前任务最匹配的基因，
 * 将其 content 拼接为结构化指令块追加到 SystemMessage。
 */
@Slf4j
@Builder
public class GeneInjector implements StreamAdvisor, CallAdvisor {

    private final GeneSelector geneSelector;

    @Override
    public @NonNull String getName() {
        return "GeneInjector";
    }

    @Override
    public int getOrder() {
        return 220;
    }

    @Override
    public @NonNull Flux<ChatClientResponse> adviseStream(@NonNull ChatClientRequest request,
                                                           @NonNull StreamAdvisorChain chain) {
        ChatClientRequest modifiedRequest = injectGenes(request);
        return chain.nextStream(modifiedRequest);
    }

    @Override
    public @NonNull ChatClientResponse adviseCall(@NonNull ChatClientRequest request,
                                                   @NonNull CallAdvisorChain chain) {
        ChatClientRequest modifiedRequest = injectGenes(request);
        return chain.nextCall(modifiedRequest);
    }

    /**
     * before 阶段：选择基因并注入系统提示词。
     */
    private ChatClientRequest injectGenes(ChatClientRequest request) {
        if (geneSelector == null) {
            return request;
        }

        try {
            GeneSelectionContext context = resolveContext(request);
            List<Gene> selected = geneSelector.select(context);
            if (selected.isEmpty()) {
                return request;
            }

            String geneBlock = buildGeneBlock(selected);
            if (geneBlock == null || geneBlock.isBlank()) {
                return request;
            }

            Prompt prompt = request.prompt();
            List<Message> messages = new ArrayList<>(prompt.getInstructions());

            boolean systemMessageFound = false;
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i) instanceof SystemMessage sysMsg) {
                    String augmentedText = sysMsg.getText() + "\n\n" + geneBlock;
                    messages.set(i, SystemMessage.builder()
                            .text(augmentedText)
                            .metadata(sysMsg.getMetadata())
                            .build());
                    systemMessageFound = true;
                    break;
                }
            }

            if (!systemMessageFound) {
                messages.addFirst(new SystemMessage(geneBlock));
            }

            return request.mutate()
                    .prompt(prompt.mutate().messages(messages).build())
                    .build();
        } catch (Exception e) {
            log.warn("[GeneInjector] 注入基因失败", e);
            return request;
        }
    }

    /**
     * 从请求上下文中解析基因选择上下文，找不到时使用默认值。
     */
    private GeneSelectionContext resolveContext(ChatClientRequest request) {
        Map<String, Object> ctx = request.context();
        if (ctx != null) {
            Object value = ctx.get("geneSelectionContext");
            if (value instanceof GeneSelectionContext gsc) {
                return gsc;
            }
        }
        return GeneSelectionContext.defaults();
    }

    /**
     * 将选中的基因拼接为结构化指令块。
     */
    private String buildGeneBlock(List<Gene> selectedGenes) {
        StringBuilder sb = new StringBuilder();
        sb.append("<genes>\n");
        for (Gene gene : selectedGenes) {
            sb.append("- gene: ").append(gene.name() != null ? gene.name() : gene.id()).append("\n");
            if (!gene.description().isEmpty()) {
                sb.append("  description: ").append(gene.description()).append("\n");
            }
            if (gene.content() != null && !gene.content().isBlank()) {
                sb.append("  instruction: |\n");
                for (String line : gene.content().split("\n")) {
                    sb.append("    ").append(line).append("\n");
                }
            }
        }
        sb.append("</genes>");
        return sb.toString();
    }
}
