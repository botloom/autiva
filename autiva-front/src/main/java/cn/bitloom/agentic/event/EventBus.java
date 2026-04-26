package cn.bitloom.agentic.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

@Slf4j
public class EventBus {

    private static final String METADATA_LOG = "EventBus emit failed, event may be lost";
    private static final Sinks.Many<Event> inBox = Sinks.many()
            .multicast()
            .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE * 10, false);
    private static final Sinks.Many<Event> outBox = Sinks.many()
            .multicast()
            .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE * 10, false);

    public static void inBoxPublish(String sessionId, Message message) {
        Sinks.EmitResult result = inBox.tryEmitNext(
                Event.builder()
                        .sessionId(sessionId)
                        .message(message)
                        .build()
        );
        if (result.isFailure()) {
            log.warn("{}, inBox publish failed: {}", METADATA_LOG, result);
        }
    }

    public static Flux<Event> inBoxSubscribe() {
        return inBox.asFlux();
    }

    public static void outBoxPublish(String sessionId, Message message) {
        Sinks.EmitResult result = outBox.tryEmitNext(
                Event.builder()
                        .sessionId(sessionId)
                        .message(message)
                        .build()
        );
        if (result.isFailure()) {
            log.warn("{}, outBox publish failed: {}", METADATA_LOG, result);
        }
    }

    public static Flux<Event> outBoxSubscribe() {
        return outBox.asFlux();
    }

}
