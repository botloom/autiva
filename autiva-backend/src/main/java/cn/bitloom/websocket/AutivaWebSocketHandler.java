package cn.bitloom.websocket;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutivaWebSocketHandler implements WebSocketHandler {

    private final ClientConnectionManager connectionManager;
    private final MessageDispatcher messageDispatcher;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String extractedClientId = extractClientId(session);
        final String clientId = extractedClientId != null ? extractedClientId : UUID.randomUUID().toString();

        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        connectionManager.register(clientId, outbound);

        Mono<Void> input = session.receive()
                .map(msg -> msg.getPayloadAsText())
                .doOnNext(message -> handleMessage(clientId, message))
                .doOnError(e -> log.error("Error receiving message from {}: {}", clientId, e.getMessage()))
                .then();

        Mono<Void> output = session.send(
                outbound.asFlux()
                        .map(session::textMessage)
        ).doFinally(signal -> {
            connectionManager.unregister(clientId);
            log.info("Client {} disconnected: {}", clientId, signal);
        });

        return Mono.zip(input, output).then();
    }

    private String extractClientId(WebSocketSession session) {
        String query = session.getHandshakeInfo().getUri().getQuery();
        if (query != null && query.contains("clientId=")) {
            return query.split("clientId=")[1].split("&")[0];
        }
        return null;
    }

    private void handleMessage(String clientId, String message) {
        log.debug("Received from {}: {}", clientId, message);
        try {
            JSONObject json = JSON.parseObject(message);
            String type = json.getString("type");
            Object payload = json.get("payload");

            messageDispatcher.dispatch(clientId, type, payload)
                    .subscribe(
                            response -> sendToClient(clientId, response),
                            error -> log.error("Error processing message: {}", error.getMessage())
                    );
        } catch (Exception e) {
            log.error("Failed to parse message: {}", e.getMessage());
        }
    }

    private void sendToClient(String clientId, String message) {
        connectionManager.sendToClient(clientId, message);
    }
}
