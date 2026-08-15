package cn.bitloom.agentic.agent.advisor;

import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.hook.IAgentHook;
import cn.bitloom.agentic.hook.ToolCallDecision;
import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.model.ToolContext;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HookAdvisor 是唯一的 Advisor 桥接层，将 AgentHook 高级 API 桥接到 Spring AI Advisor 机制。
 * <p>
 * Agent 持有 List&lt;AgentHook&gt;，在创建 ChatClient 时通过此类统一注册。
 * Hook 按 order() 排序执行。
 */
@Builder
public class HookAdvisor implements CallAdvisor, StreamAdvisor {

    private final List<IAgentHook> hooks;

    public HookAdvisor(List<IAgentHook> hooks) {
        this.hooks = hooks.stream()
                .sorted(java.util.Comparator.comparingInt(IAgentHook::order))
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
        // 轮次开始
        beforeConversationRound(request);

        // beforeModelCall: 按 order 顺序执行
        ChatClientRequest current = request;
        for (IAgentHook hook : hooks) {
            ChatClientRequest modified = hook.beforeModelCall(current);
            if (modified != null) {
                current = modified;
            }
        }

        ChatClientResponse response = chain.nextCall(current);

        // afterModelCall: 按 order 顺序执行
        String finishReason = extractFinishReason(response);
        for (IAgentHook hook : hooks) {
            hook.afterModelCall(current, response, finishReason);
        }

        if (response.chatResponse() != null && response.chatResponse().hasFinishReasons(Set.of("STOP"))) {
            afterConversationRound(request);
        }

        return response;
    }

    @Override
    public @NonNull Flux<ChatClientResponse> adviseStream(@NonNull ChatClientRequest request,
                                                          @NonNull StreamAdvisorChain chain) {
        // 轮次开始
        beforeConversationRound(request);

        // beforeModelCall: 按 order 顺序执行
        ChatClientRequest current = request;
        for (IAgentHook hook : hooks) {
            ChatClientRequest modified = hook.beforeModelCall(current);
            if (modified != null) {
                current = modified;
            }
        }

        final ChatClientRequest finalRequest = current;
        Flux<ChatClientResponse> responses = chain.nextStream(finalRequest);

        // 跟踪是否遇到 STOP
        AtomicBoolean isStopped = new AtomicBoolean(false);
        return responses
                .doOnNext(resp -> {
                    // afterModelCall: 在每个 response 上按 order 顺序执行
                    String fr = extractFinishReason(resp);
                    for (IAgentHook hook : hooks) {
                        hook.afterModelCall(finalRequest, resp, fr);
                    }
                    if (resp.chatResponse() != null && resp.chatResponse().hasFinishReasons(Set.of("STOP"))) {
                        isStopped.set(true);
                    }
                })
                .doOnComplete(() -> {
                    // 此时内层 Advisor（含 MessageChatMemoryAdvisor）的 doOnComplete 已执行完毕
                    if (isStopped.get()) {
                        afterConversationRound(finalRequest);
                    }
                });
    }

    private void beforeConversationRound(ChatClientRequest request) {
        RuntimeContext ctx = (RuntimeContext) request.context().get("runtimeContext");
        if (ctx == null) return;
        for (IAgentHook hook : hooks) {
            hook.beforeConversationRound(ctx);
        }
    }

    private void afterConversationRound(ChatClientRequest request) {
        RuntimeContext ctx = (RuntimeContext) request.context().get("runtimeContext");
        if (ctx == null) return;
        for (IAgentHook hook : hooks) {
            hook.afterConversationRound(ctx);
        }
    }

    /**
     * 从响应中提取停止原因（如 "STOP"、"TOOL_CALLS"），可能为 null。
     */
    private String extractFinishReason(ChatClientResponse response) {
        if (response != null && response.chatResponse() != null
                && response.chatResponse().getResult() != null
                && response.chatResponse().getResult().getMetadata() != null) {
            return response.chatResponse().getResult().getMetadata().getFinishReason();
        }
        return null;
    }

    /**
     * 委托所有 Hook 的 beforeToolCall，链式执行。
     * <p>
     * 任一 hook 返回 block 则立即停止，返回该 block 决策。
     * hook 返回 proceed 时，其 input（非 null）会覆盖当前 input 传递给下一个 hook。
     *
     * @param toolName 工具名称
     * @param input    原始输入参数（JSON 字符串）
     * @param context  工具上下文
     * @return 工具调用决策
     */
    public ToolCallDecision beforeToolCall(String toolName, String input, ToolContext context) {
        String current = input;
        for (IAgentHook hook : hooks) {
            ToolCallDecision decision = hook.beforeToolCall(toolName, current, context);
            if (!decision.proceed()) {
                return decision;
            }
            if (decision.input() != null) {
                current = decision.input();
            }
        }
        return ToolCallDecision.proceed(current);
    }

    /**
     * 委托所有 Hook 的 afterToolCall，链式执行。
     * <p>
     * hook 返回非 null 的 result 会覆盖当前 result 传递给下一个 hook。
     *
     * @param toolName 工具名称
     * @param result   原始结果（JSON 字符串）
     * @param context  工具上下文
     * @return 修改后的结果
     */
    public String afterToolCall(String toolName, String result, ToolContext context) {
        String current = result;
        for (IAgentHook hook : hooks) {
            String modified = hook.afterToolCall(toolName, current, context);
            if (modified != null) {
                current = modified;
            }
        }
        return current;
    }
}
