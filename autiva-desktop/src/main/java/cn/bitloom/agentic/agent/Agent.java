package cn.bitloom.agentic.agent;

import cn.bitloom.agentic.agent.advisor.HookAdvisor;
import cn.bitloom.agentic.agent.advisor.LoggingAdvisor;
import cn.bitloom.agentic.agent.advisor.ProactiveContextAdvisor;
import cn.bitloom.agentic.agent.advisor.UsageAdvisor;
import cn.bitloom.agentic.hook.AgentHook;
import cn.bitloom.agentic.util.MessageUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.jspecify.annotations.NonNull;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 统一智能体类，合并了原 AbstractAgent + MainAgent + SubagentExecutor 的功能。
 * 主智能体和子智能体都是 Agent 类的实例，区别仅在 AgentDefinition 的配置。
 * <p>
 * Agent 实例本身只持有不可变的配置，所有 per-session 的可变数据都放在 Session 里。
 * 一个 Agent 实例可以同时服务多个用户和会话。
 * <p>
 * 只能通过 Builder 创建。Hook 机制基于 Spring AI Advisor 实现，通过 HookAdvisor 桥接。
 */
@Slf4j
public class Agent {

    @Getter
    private final @NonNull String name;
    @Getter
    private final @NonNull AgentDefinition definition;
    @Getter
    private final @NonNull ChatClient chatClient;
    @Getter
    private final @NonNull List<ToolCallback> tools;
    @Getter
    private final @NonNull List<AgentHook> hooks;

    private Agent(@NonNull String name, @NonNull AgentDefinition definition, @NonNull ChatClient chatClient,
                  @NonNull List<ToolCallback> tools, @NonNull List<AgentHook> hooks) {
        this.name = name;
        this.definition = definition;
        this.chatClient = chatClient;
        this.tools = tools;
        this.hooks = hooks;
    }

    /**
     * 流式调用 LLM
     * <p>
     * 参数从 RuntimeContext 获取，与 Session 解耦：
     * - 主智能体：ctx 包含 Session，conversationId = session.getId()
     * - 子智能体：ctx 无 Session，conversationId = taskId
     * <p>
     * 错误处理：onErrorResume 将异常转换为兜底 AssistantMessage，避免错误裸抛到订阅层。
     */
    public Flux<AssistantMessage> runStream(RuntimeContext ctx, Message message) {
        String conversationId = ctx.getConversationId();
        return this.chatClient
                .prompt()
                .advisors(a -> {
                    if (conversationId != null) {
                        a.param(ChatMemory.CONVERSATION_ID, conversationId);
                    }
                    a.param("runtimeContext", ctx);
                })
                .toolContext(ctx.getParams())
                .messages(message)
                .stream()
                .chatResponse()
                .map(chatResponse -> chatResponse.getResult().getOutput())
                .onErrorResume(e -> {
                    log.error("LLM stream error: {}, msg: {}", e.getClass().getSimpleName(), e.getMessage());
                    return Flux.just(MessageUtil.buildFallbackMessage());
                });
    }

    /**
     * 阻塞调用 LLM
     */
    public AssistantMessage runBlock(RuntimeContext ctx, Message message) {
        String conversationId = ctx.getConversationId();
        try {
            var promptSpec = this.chatClient.prompt()
                    .advisors(a -> {
                        if (conversationId != null) {
                            a.param(ChatMemory.CONVERSATION_ID, conversationId);
                        }
                        a.param("runtimeContext", ctx);
                    })
                    .toolContext(ctx.getParams())
                    .messages(message);

            ChatResponse response = promptSpec.call().chatResponse();
            if (Objects.nonNull(response)) {
                return response.getResult().getOutput();
            }
        } catch (Exception e) {
            log.error("LLM block error: {}, msg: {}", e.getClass().getSimpleName(), e.getMessage());
            return MessageUtil.buildFallbackMessage();
        }
        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private AgentDefinition definition;
        private String systemPrompt;
        private ChatModel model;
        private List<ToolCallback> tools = new ArrayList<>();
        private List<AgentHook> hooks = new ArrayList<>();
        private boolean enableLogging = true;
        private boolean enableMemory = false;
        private ChatMemory chatMemory;
        private boolean enableCompact = false;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder definition(AgentDefinition definition) {
            this.definition = definition;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder model(ChatModel chatModel) {
            this.model = chatModel;
            return this;
        }

        public Builder tools(List<ToolCallback> tools) {
            this.tools = tools;
            return this;
        }

        public Builder hooks(List<AgentHook> hooks) {
            this.hooks = hooks;
            return this;
        }

        public Builder memory(ChatMemory chatMemory) {
            this.enableMemory = true;
            this.chatMemory = chatMemory;
            return this;
        }

        public Builder logging(boolean enableLogging) {
            this.enableLogging = enableLogging;
            return this;
        }

        public Builder chatMemory(ChatMemory chatMemory) {
            this.chatMemory = chatMemory;
            return this;
        }

        public Builder compact(boolean enableCompact) {
            this.enableCompact = enableCompact;
            return this;
        }

        public Agent build() {
            ChatClient.Builder builder = ChatClient.builder(this.model);
            if (StringUtils.isNotBlank(this.systemPrompt)) {
                builder.defaultSystem(this.systemPrompt);
            }
            if (!this.tools.isEmpty()) {
                builder.defaultTools(this.tools);
            }
            if (this.enableLogging) {
                builder.defaultAdvisors(LoggingAdvisor.builder().build());
            }
            builder.defaultAdvisors(
                    ToolCallingAdvisor.builder()
                            .disableInternalConversationHistory()
                            .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 300)
                            .build()
            );
            if (this.enableMemory) {
                builder.defaultAdvisors(
                        MessageChatMemoryAdvisor
                                .builder(chatMemory)
                                .order(BaseAdvisor.HIGHEST_PRECEDENCE + 400)
                                .build()
                );
            }
            if (this.enableCompact) {
                builder.defaultAdvisors(
                        UsageAdvisor.builder().build(),
                        ProactiveContextAdvisor.builder().build()
                );
            }
            if (!this.hooks.isEmpty()) {
                builder.defaultAdvisors(HookAdvisor.builder().hooks(this.hooks).build());
            }
            return new Agent(name, definition, builder.build(), tools, hooks);
        }
    }
}
