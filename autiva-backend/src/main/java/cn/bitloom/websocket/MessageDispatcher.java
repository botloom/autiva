package cn.bitloom.websocket;

import cn.bitloom.dto.DeployRequest;
import cn.bitloom.dto.DeployResult;
import cn.bitloom.dto.ProjectFile;
import cn.bitloom.sandbox.SandboxService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @SuppressWarnings("unchecked")
    private Mono<String> handleDeploy(String clientId, Object payload) {
        JSONObject p = (JSONObject) payload;
        String projectName = p.getString("projectName");
        String runtime = p.getString("runtime");

        List<ProjectFile> files = Collections.emptyList();
        if (p.containsKey("files")) {
            files = p.getList("files", Object.class).stream()
                    .map(f -> {
                        JSONObject fileObj = (JSONObject) f;
                        return new ProjectFile(fileObj.getString("path"), fileObj.getString("content"));
                    })
                    .collect(Collectors.toList());
        }

        Map<String, String> envVars = Collections.emptyMap();
        if (p.containsKey("envVars")) {
            envVars = p.getObject("envVars", Map.class);
        }

        DeployRequest request = new DeployRequest(clientId, projectName, files, runtime, envVars);

        log.info("[WS] Deploy from {}: project={}, runtime={}, files={}", clientId, projectName, runtime, files.size());

        return sandboxService.deployProject(request)
                .map(result -> JSON.toJSONString(Map.of(
                        "type", "deploy_result",
                        "success", result.success(),
                        "url", result.url() != null ? result.url() : "",
                        "message", result.message(),
                        "sandboxId", result.sandboxId() != null ? result.sandboxId() : "",
                        "subdomain", result.subdomain() != null ? result.subdomain() : ""
                )))
                .onErrorResume(e -> Mono.just(JSON.toJSONString(Map.of(
                        "type", "deploy_result",
                        "success", false,
                        "message", e.getMessage()
                ))));
    }

    private Mono<String> handleStop(String clientId, Object payload) {
        JSONObject p = (JSONObject) payload;
        String projectName = p.getString("projectName");

        return sandboxService.stopService(clientId, projectName)
                .thenReturn(JSON.toJSONString(Map.of(
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
        return sandboxService.listServices(clientId)
                .map(status -> JSON.toJSONString(Map.of(
                        "type", "status_result",
                        "services", status
                )));
    }

    private Mono<String> handleLogs(String clientId, Object payload) {
        JSONObject p = (JSONObject) payload;
        String projectName = p.getString("projectName");

        return sandboxService.getLogs(clientId, projectName)
                .map(logs -> JSON.toJSONString(Map.of(
                        "type", "logs_result",
                        "logs", logs
                )));
    }
}
