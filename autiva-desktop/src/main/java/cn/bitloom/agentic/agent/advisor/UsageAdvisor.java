package cn.bitloom.agentic.agent.advisor;

import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.event.MemoryEvent;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.session.Session;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * Usage 提取 Advisor，从模型响应的 Usage 中提取 prompt_tokens。
 * <p>
 * 逻辑放在 Advisor 中，不侵入 Agent 类。
 * - 更新 Session.currentContextLength（语义为 token 数）
 * - 超阈值时发布 MemoryEvent.CONTEXT_COMPACT 到 inBox，由 Session 异步处理
 * <p>
 * Session 通过 RuntimeContext 传递，无需在构建时持有 SessionManager。
 */
@Slf4j
@Builder
public class UsageAdvisor implements StreamAdvisor, CallAdvisor {

    @Override
    public @NonNull String getName() {
        return "UsageAdvisor";
    }

    @Override
    public int getOrder() {
        return 50;
    }

    @Override
    public @NonNull ChatClientResponse adviseCall(@NonNull ChatClientRequest request,
                                                  @NonNull CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        handleUsage(request, response);
        return response;
    }

    @Override
    public @NonNull Flux<ChatClientResponse> adviseStream(@NonNull ChatClientRequest request,
                                                          @NonNull StreamAdvisorChain chain) {
        return chain.nextStream(request)
                .doOnNext(response -> handleUsage(request, response));
    }

    private void handleUsage(ChatClientRequest request, ChatClientResponse response) {
        try {
            RuntimeContext ctx = (RuntimeContext) request.context().get("runtimeContext");
            if (ctx == null || ctx.getSession() == null) return;
            Session session = ctx.getSession();

            if (response == null) return;
            ChatResponse chatResponse = response.chatResponse();
            if (chatResponse == null) return;
            Usage usage = chatResponse.getMetadata().getUsage();
            usage.getPromptTokens();

            int promptTokens = usage.getPromptTokens();

            // 直接操作 Session（currentContextLength 是瞬态字段，不需要持久化）
            session.setCurrentContextLength(promptTokens);

            // 检查是否需要压缩（阈值 = maxTokens * compactionThreshold）
            int maxTokens = getMaxContextTokens(session.getModel());
            double threshold = maxTokens * session.getCompactionThreshold();
            if (promptTokens >= threshold) {
                EventBus.publishIn(MemoryEvent.contextCompact(
                        session.getId(), session.getAgentId(), promptTokens, maxTokens));
                log.info("[UsageAdvisor] 触发压缩事件: sessionId={}, tokens={}, threshold={}",
                        session.getId(), promptTokens, (int) threshold);
            }
        } catch (Exception e) {
            log.warn("[UsageAdvisor] 处理 Usage 失败", e);
        }
    }

    private int getMaxContextTokens(ModelTypeEnum model) {
        // DeepSeek chat model supports 128k context window
        return 128000;
    }
}
