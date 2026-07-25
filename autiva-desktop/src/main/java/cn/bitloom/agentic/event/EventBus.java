package cn.bitloom.agentic.event;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

public class EventBus {

    public static Sinks.Many<AbstractEvent> inBox = Sinks.many()
            .multicast()
            .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE * 10, false);

    public static Sinks.Many<AbstractEvent> outBox = Sinks.many()
            .multicast()
            .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE * 10, false);

    public static void publishIn(AbstractEvent event) {
        inBox.tryEmitNext(event);
    }

    public static void publishOut(AbstractEvent event) {
        outBox.tryEmitNext(event);
    }

    public static Flux<AbstractEvent> inBoxFlux() {
        return inBox.asFlux();
    }

    public static Flux<AbstractEvent> outBoxFlux() {
        return outBox.asFlux();
    }

}