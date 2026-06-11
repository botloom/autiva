package cn.bitloom.agentic.message;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

@Slf4j
public class MessageBus {

    private final Sinks.Many<Message> inBox = Sinks.many()
            .multicast()
            .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE * 10, false);
    private final Sinks.Many<Message> outBox = Sinks.many()
            .multicast()
            .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE * 10, false);

    public void inBoxPublish(Message message) {
        inBox.tryEmitNext(message);
    }

    public Flux<Message> inBoxSubscribe() {
        return inBox.asFlux();
    }

    public void outBoxPublish(Message message) {
        outBox.tryEmitNext(message);
    }

    public Flux<Message> outBoxSubscribe() {
        return outBox.asFlux();
    }

    public void stop(){
        this.inBox.tryEmitComplete();
        this.outBox.tryEmitComplete();
    }

}
