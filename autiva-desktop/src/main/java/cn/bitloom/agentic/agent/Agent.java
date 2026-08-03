package cn.bitloom.agentic.agent;

import cn.bitloom.agentic.agent.advisor.HookAdvisor;
import cn.bitloom.agentic.agent.advisor.LoggingAdvisor;
import cn.bitloom.agentic.agent.advisor.SessionMemoryAdvisor;
import cn.bitloom.agentic.event.AbstractEvent;
import cn.bitloom.agentic.event.EventConverter;
import cn.bitloom.agentic.event.EventPublisher;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.hook.HookedToolCallback;
import cn.bitloom.agentic.hook.IAgentHook;
import cn.bitloom.agentic.tool.AutivaToolCallingManager;
import cn.bitloom.agentic.util.MessageUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
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
    private final @NonNull List<IAgentHook> hooks;

    private Agent(@NonNull String name, @NonNull AgentDefinition definition, @NonNull ChatClient chatClient,
                  @NonNull List<ToolCallback> tools, @NonNull List<IAgentHook> hooks) {
        this.name = name;
        this.definition = definition;
        this.chatClient = chatClient;
        this.tools = tools;
        this.hooks = hooks;
    }

    /**
     * 流式调用 LLM，返回事件流。
     * <p>
     * 入参 MessageEvent + RuntimeContext，出参 Flux&lt;AbstractEvent&gt;。
     * <ul>
     *   <li>Flux.create 内部创建 sink 作为事件汇聚点</li>
     *   <li>sink::next 包装为 EventPublisher 注入 ctx，通过 ToolContext 传递给工具层</li>
     *   <li>LLM 流的 AssistantMessage 通过 EventConverter 转 MessageEvent 推入 sink</li>
     *   <li>工具事件由 AutivaToolCallingManager 通过 EventPublisher 推入同一 sink</li>
     * </ul>
     * Agent 实例不绑定 session，可复用（不同 session 传不同 ctx）。
     * <p>
     * 错误处理：onErrorResume 将异常转换为兜底 MessageEvent，避免错误裸抛到订阅层。
     */
    public Flux<AbstractEvent> runStream(MessageEvent inputEvent, RuntimeContext ctx) {
        String sessionId = ctx.getSessionId();
        String branch = ctx.getBranch();
        return Flux.<AbstractEvent>create(sink -> {
            // 把 sink::next 包装为 EventPublisher，自动给 MessageEvent 设置 branch
            // （工具事件和 LLM 流事件统一打标，便于 UI 路由和历史过滤）
            EventPublisher runtimePublisher = event -> {
                if (branch != null && event instanceof MessageEvent me && me.getBranch() == null) {
                    me.setBranch(branch);
                }
                sink.next(event);
            };
            ctx.put("eventSink", runtimePublisher);
            ctx.put("sessionId", sessionId);
            if (branch != null) {
                ctx.put("branch", branch);
            }

            UserMessage userMessage = EventConverter.toUserMessage(inputEvent);
            this.chatClient.prompt()
                    .advisors(a -> {
                        if (sessionId != null) {
                            a.param(ChatMemory.CONVERSATION_ID, sessionId);
                        }
                        if (branch != null) {
                            a.param(SessionMemoryAdvisor.BRANCH_CONTEXT_KEY, branch);
                        }
                        a.param("runtimeContext", ctx);
                    })
                    .toolContext(ctx.toToolContextMap())
                    .messages(userMessage)
                    .stream()
                    .chatResponse()
                    .map(cr -> {
                        MessageEvent event = EventConverter.fromMessage(sessionId, cr.getResult().getOutput());
                        if (branch != null && event.getBranch() == null) {
                            event.setBranch(branch);
                        }
                        return event;
                    })
                    .subscribe(sink::next, sink::error, sink::complete);
        }).onErrorResume(e -> {
            log.error("LLM stream error: {}, msg: {}", e.getClass().getSimpleName(), e.getMessage());
            AssistantMessage fallback = MessageUtil.buildFallbackMessage();
            MessageEvent fallbackEvent = EventConverter.fromMessage(sessionId, fallback);
            if (branch != null && fallbackEvent.getBranch() == null) {
                fallbackEvent.setBranch(branch);
            }
            return Flux.just(fallbackEvent);
        });
    }

    /**
     * 阻塞调用 LLM，返回最终 MessageEvent。
     * 仅返回最后一条非空 assistant 消息事件。
     */
    public MessageEvent runBlock(MessageEvent inputEvent, RuntimeContext ctx) {
        return runStream(inputEvent, ctx)
                .filter(e -> e instanceof MessageEvent)
                .cast(MessageEvent.class)
                .blockLast();
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
        private List<IAgentHook> hooks = new ArrayList<>();
        private List<Advisor> advisors = new ArrayList<>();
        private boolean enableLogging = true;

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

        public Builder hooks(List<IAgentHook> hooks) {
            this.hooks = hooks;
            return this;
        }

        public Builder advisors(List<Advisor> advisors) {
            this.advisors = advisors;
            return this;
        }

        public Builder logging(boolean enableLogging) {
            this.enableLogging = enableLogging;
            return this;
        }

        public Agent build() {
            ChatClient.Builder builder = ChatClient.builder(this.model);
            if (StringUtils.isNotBlank(this.systemPrompt)) {
                builder.defaultSystem(this.systemPrompt);
            }

            // 构建 HookAdvisor（如果有 Hook）
            List<IAgentHook> allHooks = new ArrayList<>(this.hooks);
            HookAdvisor hookAdvisor = allHooks.isEmpty() ? null : new HookAdvisor(allHooks);

            // 用 HookedToolCallback 包装所有工具，使 Hook 能拦截工具调用
            List<ToolCallback> wrappedTools = new ArrayList<>();
            if (!this.tools.isEmpty()) {
                for (ToolCallback tool : this.tools) {
                    if (hookAdvisor != null) {
                        wrappedTools.add(new HookedToolCallback(tool, hookAdvisor));
                    } else {
                        wrappedTools.add(tool);
                    }
                }
                builder.defaultTools(wrappedTools);
            }
            if (this.enableLogging) {
                builder.defaultAdvisors(LoggingAdvisor.builder()
                        .agentName(this.name)
                        .build());
            }
            AutivaToolCallingManager toolCallingManager = new AutivaToolCallingManager(wrappedTools);
            builder.defaultAdvisors(
                    ToolCallingAdvisor.builder()
                            .disableInternalConversationHistory()
                            .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 300)
                            .toolCallingManager(toolCallingManager)
                            .build()
            );
            if (!this.advisors.isEmpty()) {
                builder.defaultAdvisors(new ArrayList<>(this.advisors));
            }

            if (hookAdvisor != null) {
                builder.defaultAdvisors(hookAdvisor);
            }
            return new Agent(name, definition, builder.build(), wrappedTools, allHooks);
        }
    }
}
