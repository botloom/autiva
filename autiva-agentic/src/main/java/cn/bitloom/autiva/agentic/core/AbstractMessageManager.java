package cn.bitloom.autiva.agentic.core;

import cn.bitloom.autiva.agentic.enums.MetaDataInfoEnum;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;

/**
 * The type Session manager.
 *
 * @param <Input>  the type parameter
 * @param <Output> the type parameter
 * @param <Memory> the type parameter
 * @author bitloom
 */
public abstract class AbstractMessageManager<Input, Output, Memory> implements StreamAdvisor {
    /**
     * The Input.
     */
    protected Input input;
    /**
     * The Output.
     */
    protected Output output;
    /**
     * The Memory list.
     */
    protected List<Memory> memoryList;
    /**
     * The Message sink.
     */
    protected final Sinks.Many<UserMessage> eventSink;

    /**
     * The Message sink.
     */
    protected final Sinks.Many<UserMessage> manualSink;

    /**
     * Instantiates a new Message manager.
     */
    public AbstractMessageManager() {
        this.eventSink = Sinks.many().multicast().onBackpressureBuffer();
        this.manualSink = Sinks.many().multicast().onBackpressureBuffer();
        this.init();
    }

    @Override
    public @NonNull Flux<ChatClientResponse> adviseStream(@NonNull ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        chatClientRequest = this.preHandle(chatClientRequest);
        Flux<ChatClientResponse> response = streamAdvisorChain.nextStream(chatClientRequest);
        return new ChatClientMessageAggregator().aggregateChatClientResponse(response, this::postHandle);
    }

    @Override
    public @NonNull String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * Emit.
     *
     * @param message the message
     */
    public void emit(UserMessage message) {
        Map<String, Object> metadata = message.getMetadata();
        if (metadata.containsKey(MetaDataInfoEnum.MANUAL.name())) {
            this.eventSink.tryEmitNext(message);
        }
        if (metadata.containsKey(MetaDataInfoEnum.EVENT.name())) {
            this.manualSink.tryEmitNext(message);
        }
        if (metadata.containsKey(MetaDataInfoEnum.ASSISTANT.name())) {
            this.eventSink.tryEmitNext(message);
        }
    }

    /**
     * Subscribe flux.
     *
     * @param sessionId the session id
     * @return the flux
     */
    public Flux<UserMessage> subscribe(String sessionId) {
        return Flux.defer(() ->
                eventSink.asFlux()
                        .filter(message -> sessionId.equals(message.getMetadata().get(MetaDataInfoEnum.SESSION_ID.name())))
                        .switchIfEmpty(
                                manualSink.asFlux()
                                        .filter(message -> sessionId.equals(message.getMetadata().get(MetaDataInfoEnum.SESSION_ID.name())))
                        )
        );
    }

    /**
     * Init.
     */
    protected abstract void init();

    /**
     * Pre handle message.
     *
     * @param request the request
     */
    protected abstract ChatClientRequest preHandle(ChatClientRequest request);

    /**
     * Post handle message.
     *
     * @param response the response
     */
    protected abstract void postHandle(ChatClientResponse response);
}
