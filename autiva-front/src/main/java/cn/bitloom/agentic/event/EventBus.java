package cn.bitloom.agentic.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class EventBus {

    private static final String METADATA_LOG = "EventBus emit failed, event may be lost";
    private static final Sinks.Many<Event> inBox = Sinks.many()
            .multicast()
            .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE * 10, false);
    private static final Sinks.Many<Event> outBox = Sinks.many()
            .multicast()
            .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE * 10, false);
    private static final ConcurrentHashMap<String, Boolean> cancelFlags = new ConcurrentHashMap<>();

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

    public static void cancelPublish(String sessionId) {
        cancelFlags.put(sessionId, Boolean.TRUE);
        log.debug("Cancel signal published for session: {}", sessionId);
    }

    public static boolean isCancelled(String sessionId) {
        return Boolean.TRUE.equals(cancelFlags.get(sessionId));
    }

    public static void clearCancelFlag(String sessionId) {
        cancelFlags.remove(sessionId);
    }

}
