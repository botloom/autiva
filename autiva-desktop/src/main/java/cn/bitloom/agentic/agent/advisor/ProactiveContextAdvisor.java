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
import reactor.core.publisher.Flux;

/**
 * 主动上下文注入 Advisor，基于 Session 结构化注入上下文。
 * <p>
 * 注入内容：
 * 1. 基于 Session 的结构化上下文（任务清单、计划模式、对话摘要）
 * <p>
 * 注：相关记忆召回和进化提示已移除，改为智能体通过 memory_search 工具主动搜索。
 * Session 通过 RuntimeContext 传递，无需在构建时持有 SessionManager。
 */
@Slf4j
@Builder
public class ProactiveContextAdvisor implements StreamAdvisor, CallAdvisor {

    @Override
    public @NonNull String getName() {
        return "ProactiveContextAdvisor";
    }

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public @NonNull Flux<ChatClientResponse> adviseStream(@NonNull ChatClientRequest request,
                                                           @NonNull StreamAdvisorChain chain) {
        return chain.nextStream(request);
    }

    @Override
    public @NonNull ChatClientResponse adviseCall(@NonNull ChatClientRequest request,
                                                   @NonNull CallAdvisorChain chain) {
        return chain.nextCall(request);
    }

}
