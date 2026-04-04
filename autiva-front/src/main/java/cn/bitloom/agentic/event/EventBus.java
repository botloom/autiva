package cn.bitloom.agentic.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

/**
 * The type Event bus.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventBus {

    private static final Sinks.Many<Event> inBox = Sinks.many()
            .multicast()
            .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE * 10, false);
    private static final Sinks.Many<Event> outBox = Sinks.many()
            .multicast()
            .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE * 10, false);

    /**
     * In box publish.
     *
     * @param sessionId the session id
     * @param message   the message
     */
    public static void inBoxPublish(String sessionId, Message message) {
        inBox.emitNext(
                Event.builder()
                        .sessionId(sessionId)
                        .message(message)
                        .build(),
                Sinks.EmitFailureHandler.FAIL_FAST
        );
    }

    /**
     * In box subscribe flux.
     *
     * @return the flux
     */
    public static Flux<Event> inBoxSubscribe() {
        return inBox.asFlux();
    }

    /**
     * Out box publish.
     *
     * @param sessionId the session id
     * @param message   the message
     */
    public static void outBoxPublish(String sessionId, Message message) {
        outBox.emitNext(
                Event.builder()
                        .sessionId(sessionId)
                        .message(message)
                        .build(),
                Sinks.EmitFailureHandler.FAIL_FAST
        );
    }

    /**
     * Out box subscribe flux.
     *
     * @return the flux
     */
    public static Flux<Event> outBoxSubscribe() {
        return outBox.asFlux();
    }

}
