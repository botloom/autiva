package cn.bitloom.agentic.agent.advisor;

import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.evolve.inject.GeneInjector;
import cn.bitloom.agentic.session.Session;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Gene 配置注入 Advisor —— 将 L4 进化系统的 PROMPT Gene 注入到 UserMessage。
 *
 * <p>设计原则（对标 Claude Code 架构）：
 * <ul>
 *   <li>不追加到 SystemMessage（保持 SystemMessage 静态可缓存）</li>
 *   <li>注入到第一个 UserMessage 前面</li>
 *   <li>Gene 列表为空时完全跳过，不做无用查询</li>
 *   <li>order=250，在 ProactiveContextAdvisor (order=200) 之后</li>
 * </ul>
 */
@Slf4j
public class GeneInjectAdvisor implements StreamAdvisor, CallAdvisor {

    private final GeneInjector geneInjector;

    /** 缓存 lastKnownAgentId + lastKnownEmpty，避免连续多次空查询 */
    private volatile String lastEmptyAgentId;

    public GeneInjectAdvisor(GeneInjector geneInjector) {
        this.geneInjector = geneInjector;
    }

    @Override
    public @NonNull String getName() {
        return "GeneInjectAdvisor";
    }

    @Override
    public int getOrder() {
        return 250;
    }

    @Override
    public @NonNull Flux<ChatClientResponse> adviseStream(@NonNull ChatClientRequest request,
                                                          @NonNull StreamAdvisorChain chain) {
        ChatClientRequest modified = injectGenes(request);
        return chain.nextStream(modified);
    }

    @Override
    public @NonNull ChatClientResponse adviseCall(@NonNull ChatClientRequest request,
                                                  @NonNull CallAdvisorChain chain) {
        ChatClientRequest modified = injectGenes(request);
        return chain.nextCall(modified);
    }

    private ChatClientRequest injectGenes(ChatClientRequest request) {
        if (geneInjector == null) {
            return request;
        }
        try {
            RuntimeContext ctx = (RuntimeContext) request.context().get("runtimeContext");
            if (ctx == null) {
                return request;
            }
            Session session = ctx.getSession();
            if (session == null || session.getAgentId() == null) {
                return request;
            }

            String agentId = session.getAgentId();

            // 快速路径：上次检查该 agent 时就是空的，跳过
            if (agentId.equals(lastEmptyAgentId)) {
                return request;
            }

            String injection = geneInjector.buildPromptInjection(agentId);
            if (injection == null || injection.isBlank()) {
                // 缓存空结果，避免后续请求重复查询
                lastEmptyAgentId = agentId;
                return request;
            }

            // 注入到第一个 UserMessage
            Prompt prompt = request.prompt();
            List<Message> messages = new ArrayList<>(prompt.getInstructions());

            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i) instanceof UserMessage userMsg) {
                    String augmented = injection + "\n\n" + userMsg.getText();
                    messages.set(i, new UserMessage(augmented));
                    return request.mutate()
                            .prompt(prompt.mutate().messages(messages).build())
                            .build();
                }
            }

            // fallback: no UserMessage found
            messages.add(new UserMessage(injection));
            return request.mutate()
                    .prompt(prompt.mutate().messages(messages).build())
                    .build();
        } catch (Exception e) {
            log.warn("[GeneInjectAdvisor] 注入失败: {}", e.getMessage());
            return request;
        }
    }
}
