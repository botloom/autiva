package cn.bitloom.websocket;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import cn.bitloom.sandbox.SandboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageDispatcher {

    private final SandboxService sandboxService;

    public Mono<String> dispatch(String clientId, String type, Object payload) {
        return switch (type) {
            case "ping" -> Mono.just("{\"type\":\"pong\"}");
            case "deploy" -> handleDeploy(clientId, payload);
            case "stop" -> handleStop(clientId, payload);
            case "status" -> handleStatus(clientId);
            case "logs" -> handleLogs(clientId, payload);
            default -> Mono.just("{\"type\":\"error\",\"message\":\"Unknown message type: " + type + "\"}");
        };
    }

    private Mono<String> handleDeploy(String clientId, Object payload) {
        JSONObject deployRequest = (JSONObject) payload;
        String projectName = deployRequest.getString("projectName");
        String code = deployRequest.getString("code");
        String runtime = deployRequest.getString("runtime");

        log.info("Deploy request from {}: project={}, runtime={}", clientId, projectName, runtime);

        return sandboxService.createSandbox(clientId, projectName, code, runtime)
                .map(result -> JSON.toJSONString(Map.of(
                        "type", "deploy_result",
                        "success", result.success(),
                        "url", result.url(),
                        "message", result.message()
                )))
                .onErrorResume(e -> Mono.just(JSON.toJSONString(Map.of(
                        "type", "deploy_result",
                        "success", false,
                        "message", e.getMessage()
                ))));
    }

    private Mono<String> handleStop(String clientId, Object payload) {
        JSONObject stopRequest = (JSONObject) payload;
        String projectName = stopRequest.getString("projectName");

        return sandboxService.stopSandbox(clientId, projectName)
                .map(result -> JSON.toJSONString(Map.of(
                        "type", "stop_result",
                        "success", true,
                        "message", "Service stopped"
                )))
                .onErrorResume(e -> Mono.just(JSON.toJSONString(Map.of(
                        "type", "stop_result",
                        "success", false,
                        "message", e.getMessage()
                ))));
    }

    private Mono<String> handleStatus(String clientId) {
        return sandboxService.getStatus(clientId)
                .map(status -> JSON.toJSONString(Map.of(
                        "type", "status_result",
                        "services", status
                )));
    }

    private Mono<String> handleLogs(String clientId, Object payload) {
        JSONObject logRequest = (JSONObject) payload;
        String projectName = logRequest.getString("projectName");

        return sandboxService.getLogs(clientId, projectName)
                .map(logs -> JSON.toJSONString(Map.of(
                        "type", "logs_result",
                        "logs", logs
                )));
    }
}
