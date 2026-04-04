package cn.bitloom.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ClientConnectionManager {

    private final Map<String, Sinks.Many<String>> clientSinks = new ConcurrentHashMap<>();

    public void register(String clientId, Sinks.Many<String> sink) {
        clientSinks.put(clientId, sink);
        log.info("Client registered: {}", clientId);
    }

    public void unregister(String clientId) {
        clientSinks.remove(clientId);
        log.info("Client unregistered: {}", clientId);
    }

    public boolean sendToClient(String clientId, String message) {
        Sinks.Many<String> sink = clientSinks.get(clientId);
        if (sink != null) {
            sink.emitNext(message, Sinks.EmitFailureHandler.FAIL_FAST);
            return true;
        }
        log.warn("Client not found: {}", clientId);
        return false;
    }

    public Mono<Void> broadcast(String message) {
        return Flux.fromIterable(clientSinks.values())
                .flatMap(sink -> {
                    sink.emitNext(message, Sinks.EmitFailureHandler.FAIL_FAST);
                    return Mono.empty();
                })
                .then();
    }

    public boolean isOnline(String clientId) {
        return clientSinks.containsKey(clientId);
    }

    public int getOnlineCount() {
        return clientSinks.size();
    }
}
