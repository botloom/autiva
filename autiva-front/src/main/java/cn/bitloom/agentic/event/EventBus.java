package cn.bitloom.agentic.event;

import cn.bitloom.agentic.session.MessageChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

@Slf4j
public class EventBus {

    private static final Sinks.Many<MessageEvent> inBox = Sinks.many()
            .multicast()
            .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE * 10, false);
    private static final Sinks.Many<MessageEvent> outBox = Sinks.many()
            .multicast()
            .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE * 10, false);

    public static void inBoxPublish(String sessionId, Message message) {
        inBox.tryEmitNext(
                MessageEvent.builder()
                        .sessionId(sessionId)
                        .message(message)
                        .build()
        );
    }

    public static void inBoxPublish(String sessionId, Message message, EventType eventType) {
        MessageChannel channel = MessageChannel.fromEventType(eventType);
        inBoxPublish(sessionId, message, eventType, channel);
    }

    public static void inBoxPublish(String sessionId, Message message, EventType eventType, MessageChannel messageChannel) {
        inBox.tryEmitNext(
                MessageEvent.builder()
                        .sessionId(sessionId)
                        .message(message)
                        .eventType(eventType)
                        .messageChannel(messageChannel)
                        .build()
        );
    }

    public static Flux<MessageEvent> inBoxSubscribe() {
        return inBox.asFlux();
    }

    public static void outBoxPublish(String sessionId, Message message) {
        outBox.tryEmitNext(
                MessageEvent.builder()
                        .sessionId(sessionId)
                        .message(message)
                        .build()
        );
    }

    public static Flux<MessageEvent> outBoxSubscribe() {
        return outBox.asFlux();
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Boolean> busyMap = new java.util.concurrent.ConcurrentHashMap<>();

    public static void markBusy(String sessionId) {
        busyMap.put(sessionId, true);
    }

    public static void clearBusy(String sessionId) {
        busyMap.remove(sessionId);
    }

    public static boolean isBusy(String sessionId) {
        return busyMap.containsKey(sessionId);
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Boolean> stopMap = new java.util.concurrent.ConcurrentHashMap<>();

    public static void stop(String sessionId) {
        stopMap.put(sessionId, true);
    }

    public static boolean isStop(String sessionId) {
        return stopMap.containsKey(sessionId);
    }

    public static void clearStopFlag(String sessionId) {
        stopMap.remove(sessionId);
    }
}
