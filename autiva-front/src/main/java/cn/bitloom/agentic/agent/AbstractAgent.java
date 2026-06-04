package cn.bitloom.agentic.agent;

import cn.bitloom.agentic.event.AbstractEvent;
import cn.bitloom.agentic.event.EventType;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.model.ChatClientBuilderFactory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.session.MessageChannel;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.session.SessionManager;
import cn.bitloom.agentic.session.SessionRespTypeEnum;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.config.ConfigManager;
import cn.bitloom.cron.CronManager;
import cn.bitloom.store.ToolUIBridge;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public abstract class AbstractAgent {

    private final EnumMap<ModelTypeEnum, ChatClient> chatClientMap = new EnumMap<>(ModelTypeEnum.class);

    @Resource
    protected ChatClientBuilderFactory chatClientBuilderFactory;
    @Resource
    protected SkillManager skillManager;
    @Resource
    protected SessionManager sessionManager;
    @Resource
    protected AgentManager agentManager;
    @Resource
    protected ToolUIBridge toolUIBridge;
    @Resource
    protected CronManager cronManager;
    @Resource
    protected ConfigManager configManager;
    @Resource
    protected AsyncMcpToolCallbackProvider mcpToolCallbackProvider;
    @Resource
    protected AgentLifecycleHook lifecycleHook;

    @PostConstruct
    public void init() {
        String defaultSystemPrompt = this.getDefaultSystemPrompt();
        List<ToolCallback> defaultTools = this.getDefaultTools();
        chatClientBuilderFactory.chatClientBuilderMap().forEach((key, chatClientBuilder) -> this.chatClientMap.put(
                key,
                chatClientBuilder
                        .clone()
                        .defaultSystem(defaultSystemPrompt)
                        .defaultToolCallbacks(defaultTools)
                        .build()
        ));
        this.run();
    }

    protected void run() {
        EventBus.inBoxSubscribe()
                .filter(event -> event.getSessionId().startsWith(this.getIdentity().name()))
                .groupBy(AbstractEvent::getSessionId)
                .flatMap(eventStream -> eventStream
                        .concatMap(event -> {
                            EventType eventType = event.getEventType();
                            if (eventType == null) {
                                eventType = EventType.MESSAGE;
                            }

                            if (!canHandle(eventType)) {
                                return Flux.empty();
                            }

                            if (eventType == EventType.MESSAGE) {
                                return handleMessageEvent(event);
                            } else {
                                return handleAgentEvent(eventType, event);
                            }
                        }))
                .subscribe(
                        null,
                        error -> log.error("[Agent] inBox subscription error: {}", error.getMessage(), error),
                        () -> log.warn("[Agent] inBox subscription completed unexpectedly")
                );
    }

    protected boolean canHandle(EventType type) {
        return getHandledEventTypes().contains(type);
    }

    protected Set<EventType> getHandledEventTypes() {
        return Set.of(EventType.MESSAGE);
    }

    protected Flux<Void> handleAgentEvent(EventType type, MessageEvent event) {
        return Flux.empty();
    }

    protected Flux<Void> handleMessageEvent(MessageEvent event) {
        Session session = sessionManager.getById(event.getSessionId());
        if (session == null) {
            log.warn("[Agent] 会话不存在，跳过消息: sessionId={}", event.getSessionId());
            return Flux.empty();
        }

        MessageChannel channel = event.getMessageChannel();
        String channelAwareConversationId = event.getSessionId() + "#" + channel.name();
        EventBus.clearStopFlag(event.getSessionId());
        EventBus.markBusy(event.getSessionId());

        if (session.getRespType().equals(SessionRespTypeEnum.STREAM)) {
            AtomicReference<List<AssistantMessage>> collectedMessages = new AtomicReference<>(new ArrayList<>());
            return this.model(session.getModel())
                    .prompt()
                    .advisors(
                            a -> a.param(ChatMemory.CONVERSATION_ID, channelAwareConversationId)
                                    .param("model", session.getModel())
                    )
                    .toolContext(Map.of("sessionId", event.getSessionId(), "model", session.getModel()))
                    .toolCallbacks(this.getTools())
                    .messages(event.getMessage())
                    .stream()
                    .chatResponse()
                    .publishOn(Schedulers.boundedElastic())
                    .takeWhile(message -> !EventBus.isStop(event.getSessionId()))
                    .doOnNext(message -> {
                        collectedMessages.get().add(message.getResult().getOutput());
                        if (channel.shouldPublishToOutBox()) {
                            EventBus.outBoxPublish(event.getSessionId(), message.getResult().getOutput());
                        }
                    })
                    .doFinally(signal -> {
                        EventBus.clearStopFlag(event.getSessionId());
                        EventBus.clearBusy(event.getSessionId());
                        if (signal == SignalType.ON_COMPLETE) {
                            lifecycleHook.onSessionEnd(event.getSessionId(), collectedMessages.get(), channel);
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("LLM stream error for session {}: {}", event.getSessionId(), e.getMessage(), e);
                        if (channel.shouldPublishToOutBox()) {
                            EventBus.outBoxPublish(
                                    event.getSessionId(),
                                    AssistantMessage.builder()
                                            .content("""
                                                    ### 呜呜呜，小脑袋打了个盹儿…

                                                    出了一点小问题，暂时无法回复你 >_<

                                                    **试试以下方法：**
                                                    - **清空消息**后重新发送
                                                    - 如果还是不行，**重启应用**再试试

                                                    > 抱歉给你添麻烦啦～""")
                                            .properties(Map.of("finishReason", "STOP"))
                                            .build()
                                   );
                        }
                        return Flux.empty();
                    })
                    .then().flux();
        } else {
            try {
                ChatResponse chatResponse = this.model(session.getModel())
                        .prompt()
                        .advisors(
                                a -> a.param(ChatMemory.CONVERSATION_ID, channelAwareConversationId)
                                        .param("model", session.getModel())
                        )
                        .toolContext(Map.of("sessionId", event.getSessionId(), "model", session.getModel()))
                        .toolCallbacks(this.getTools())
                        .messages(event.getMessage())
                        .call()
                        .chatResponse();
                if (chatResponse != null) {
                    if (channel.shouldPublishToOutBox()) {
                        EventBus.outBoxPublish(event.getSessionId(), chatResponse.getResult().getOutput());
                    }
                    lifecycleHook.onSessionEnd(event.getSessionId(),
                            List.of(chatResponse.getResult().getOutput()), channel);
                }
            } catch (Exception e) {
                log.error("LLM call error for session {}: {}", event.getSessionId(), e.getMessage(), e);
                if (channel.shouldPublishToOutBox()) {
                    EventBus.outBoxPublish(
                            event.getSessionId(),
                            AssistantMessage.builder()
                                    .content("""
                                            ### 呜呜呜，小脑袋打了个盹儿…

                                            出了一点小问题，暂时无法回复你 >_<

                                            **试试以下方法：**
                                            - **清空消息**后重新发送
                                            - 如果还是不行，**重启应用**再试试

                                            > 抱歉给你添麻烦啦～""")
                                    .properties(Map.of("finishReason", "STOP"))
                                    .build()
                    );
                }
            } finally {
                EventBus.clearStopFlag(event.getSessionId());
                EventBus.clearBusy(event.getSessionId());
            }
            return Flux.empty();
        }
    }

    protected ChatClient model(ModelTypeEnum model) {
        return this.chatClientMap.getOrDefault(model, this.chatClientMap.get(ModelTypeEnum.DEEPSEEK));
    }

    protected abstract List<ToolCallback> getDefaultTools();

    protected abstract List<ToolCallback> getTools();

    protected abstract String getDefaultSystemPrompt();

    protected abstract AgentIdentityEnum getIdentity();

}
