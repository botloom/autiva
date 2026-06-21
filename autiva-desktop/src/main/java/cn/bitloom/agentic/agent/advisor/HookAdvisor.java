package cn.bitloom.agentic.agent.advisor;

import cn.bitloom.agentic.hook.AgentHook;
import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * HookAdvisor 是唯一的 Advisor 桥接层，将 AgentHook 高级 API 桥接到 Spring AI Advisor 机制。
 * <p>
 * Agent 持有 List&lt;AgentHook&gt;，在创建 ChatClient 时通过此类统一注册。
 * Hook 按 order() 排序执行。
 */
@Builder
public class HookAdvisor implements CallAdvisor, StreamAdvisor {

    private final List<AgentHook> hooks;

    public HookAdvisor(List<AgentHook> hooks) {
        this.hooks = hooks.stream()
                .sorted(java.util.Comparator.comparingInt(AgentHook::order))
                .toList();
    }

    @Override
    public @NonNull String getName() {
        return "HookAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public @NonNull ChatClientResponse adviseCall(@NonNull ChatClientRequest request,
                                                   @NonNull CallAdvisorChain chain) {
        // beforeModelCall: 按 order 顺序执行
        ChatClientRequest current = request;
        for (AgentHook hook : hooks) {
            ChatClientRequest modified = hook.beforeModelCall(current);
            if (modified != null) {
                current = modified;
            }
        }

        ChatClientResponse response = chain.nextCall(current);

        // afterModelCall: 按 order 顺序执行
        for (AgentHook hook : hooks) {
            hook.afterModelCall(current, response);
        }

        return response;
    }

    @Override
    public @NonNull Flux<ChatClientResponse> adviseStream(@NonNull ChatClientRequest request,
                                                           @NonNull StreamAdvisorChain chain) {
        // beforeModelCall: 按 order 顺序执行
        ChatClientRequest current = request;
        for (AgentHook hook : hooks) {
            ChatClientRequest modified = hook.beforeModelCall(current);
            if (modified != null) {
                current = modified;
            }
        }

        final ChatClientRequest finalRequest = current;
        Flux<ChatClientResponse> responses = chain.nextStream(finalRequest);

        // afterModelCall: 在每个 response 上按 order 顺序执行
        return responses.doOnNext(resp -> {
            for (AgentHook hook : hooks) {
                hook.afterModelCall(finalRequest, resp);
            }
        });
    }

    /**
     * 委托所有 Hook 的 beforeToolCall
     */
    public void beforeToolCall(String toolName, String input) {
        for (AgentHook hook : hooks) {
            hook.beforeToolCall(toolName, input);
        }
    }

    /**
     * 委托所有 Hook 的 afterToolCall
     */
    public void afterToolCall(String toolName, String result) {
        for (AgentHook hook : hooks) {
            hook.afterToolCall(toolName, result);
        }
    }
}
