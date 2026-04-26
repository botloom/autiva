package cn.bitloom.agentic.deploy;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class BackendClient {

    private static final Logger logger = LoggerFactory.getLogger(BackendClient.class);

    private final String baseUrl;
    private final HttpClient httpClient;

    public BackendClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public DeployResponse deployProject(String clientId, String projectName, List<ProjectFileInfo> files,
                                         String runtime, Map<String, String> envVars) {
        try {
            JSONObject body = new JSONObject();
            body.put("clientId", clientId);
            body.put("projectName", projectName);
            body.put("runtime", runtime);
            body.put("envVars", envVars != null ? envVars : Map.of());

            List<JSONObject> fileList = files.stream().map(f -> {
                JSONObject fileObj = new JSONObject();
                fileObj.put("path", f.path());
                fileObj.put("content", f.content());
                return fileObj;
            }).toList();
            body.put("files", fileList);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/sandbox/deploy"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .timeout(Duration.ofMinutes(5))
                    .build();

            logger.info("[Deploy] Sending deploy request: project={}, runtime={}, files={}", projectName, runtime, files.size());

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject result = JSON.parseObject(response.body());
                return new DeployResponse(
                        result.getBooleanValue("success"),
                        result.getString("url"),
                        result.getString("message"),
                        result.getString("sandboxId"),
                        result.getString("subdomain")
                );
            } else {
                logger.error("[Deploy] Deploy failed with status {}: {}", response.statusCode(), response.body());
                return new DeployResponse(false, null, "Server returned status " + response.statusCode(), null, null);
            }
        } catch (Exception e) {
            logger.error("[Deploy] Failed to deploy project: {}", e.getMessage(), e);
            return new DeployResponse(false, null, "Failed to connect to backend: " + e.getMessage(), null, null);
        }
    }

    public StopResponse stopProject(String clientId, String projectName) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/sandbox/stop?clientId=" + clientId + "&projectName=" + projectName))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject result = JSON.parseObject(response.body());
                return new StopResponse(result.getBooleanValue("success"), result.getString("message"));
            }
            return new StopResponse(false, "Server returned status " + response.statusCode());
        } catch (Exception e) {
            logger.error("[Stop] Failed to stop project: {}", e.getMessage(), e);
            return new StopResponse(false, "Failed to connect to backend: " + e.getMessage());
        }
    }

    public StatusResponse getStatus(String clientId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/sandbox/status?clientId=" + clientId))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return new StatusResponse(true, response.body());
            }
            return new StatusResponse(false, "Server returned status " + response.statusCode());
        } catch (Exception e) {
            logger.error("[Status] Failed to get status: {}", e.getMessage(), e);
            return new StatusResponse(false, "Failed to connect to backend: " + e.getMessage());
        }
    }

    public boolean isBackendAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/health"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public record ProjectFileInfo(String path, String content) {}

    public record DeployResponse(boolean success, String url, String message, String sandboxId, String subdomain) {}

    public record StopResponse(boolean success, String message) {}

    public record StatusResponse(boolean success, String data) {}
}
