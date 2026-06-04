package cn.bitloom.sandbox;

import com.alibaba.opensandbox.sandbox.Sandbox;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.Execution;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SandboxManager {

    private static final Map<String, String> RUNTIME_IMAGES = Map.of(
            "node", "opensandbox/code-interpreter:v1.0.2",
            "python", "opensandbox/code-interpreter:v1.0.2",
            "java", "opensandbox/code-interpreter:v1.0.2"
    );

    private final Map<String, Sandbox> sandboxCache = new ConcurrentHashMap<>();

    private final SandboxService sandboxService;

    public SandboxManager(@Lazy SandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    @PostConstruct
    public void restoreFromDatabase() {
        try {
            sandboxService.listAllServices()
                    .collectList()
                    .subscribe(services -> {
                        for (SandboxInfo info : services) {
                            if (info.containerId() != null && !"STOPPED".equals(info.status())) {
                                try {
                                    Sandbox sandbox = Sandbox.builder()
                                            .image(RUNTIME_IMAGES.getOrDefault(info.runtime(), RUNTIME_IMAGES.get("node")))
                                            .build();
                                    sandboxCache.put(info.containerId(), sandbox);
                                    log.info("Restored sandbox from database: id={}, runtime={}", info.containerId(), info.runtime());
                                } catch (Exception e) {
                                    log.warn("Failed to restore sandbox {}: {}", info.containerId(), e.getMessage());
                                }
                            }
                        }
                        log.info("Sandbox restoration complete: {} instances restored", sandboxCache.size());
                    });
        } catch (Exception e) {
            log.warn("Failed to restore sandboxes from database: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up {} sandbox instances...", sandboxCache.size());
        sandboxCache.forEach((id, sandbox) -> {
            try {
                sandbox.kill();
                log.info("Sandbox killed: {}", id);
            } catch (Exception e) {
                log.error("Failed to kill sandbox {}: {}", id, e.getMessage());
            }
        });
        sandboxCache.clear();
    }

    public Sandbox create(String sandboxId, String runtime) {
        String image = RUNTIME_IMAGES.getOrDefault(runtime, RUNTIME_IMAGES.get("node"));
        Sandbox sandbox = Sandbox.builder().image(image).build();
        sandboxCache.put(sandboxId, sandbox);
        log.info("Sandbox created: id={}, image={}", sandboxId, image);
        return sandbox;
    }

    public void kill(String sandboxId) {
        Sandbox sandbox = sandboxCache.remove(sandboxId);
        if (sandbox != null) {
            sandbox.kill();
            log.info("Sandbox killed: {}", sandboxId);
        } else {
            log.warn("Sandbox not found in cache: {}", sandboxId);
        }
    }

    public Sandbox getSandbox(String sandboxId) {
        return sandboxCache.get(sandboxId);
    }

    public boolean exists(String sandboxId) {
        return sandboxCache.containsKey(sandboxId);
    }

    public ExecutionResult execute(Sandbox sandbox, String command) {
        try {
            Execution execution = sandbox.commands().run(command);
            String stdout = extractStdout(execution);
            String stderr = extractStderr(execution);

            if (!stderr.isEmpty()) {
                log.warn("[SandboxExec] stderr: {}", stderr);
            }

            return new ExecutionResult(true, stdout, stderr, null);
        } catch (Exception e) {
            log.error("[SandboxExec] Command failed: {}", e.getMessage());
            return new ExecutionResult(false, "", e.getMessage(), e);
        }
    }

    private String extractStdout(Execution execution) {
        if (execution.getLogs() == null || execution.getLogs().getStdout() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        execution.getLogs().getStdout().forEach(msg -> sb.append(msg.getText()).append("\n"));
        return sb.toString().trim();
    }

    private String extractStderr(Execution execution) {
        if (execution.getLogs() == null || execution.getLogs().getStderr() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        execution.getLogs().getStderr().forEach(msg -> sb.append(msg.getText()).append("\n"));
        return sb.toString().trim();
    }

    public String generateSandboxId() {
        return "sandbox-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000);
    }

    /**
     * 获取沙箱的端点 URL。
     * 通过在沙箱内执行命令获取实际监听端口，构建端点 URL。
     * 如果无法获取，返回 null，调用方应使用 fallback 策略。
     *
     * @param sandboxId 沙箱 ID
     * @param runtime   运行时类型
     * @return 沙箱端点 URL，如果无法获取则返回 null
     */
    public String getEndpoint(String sandboxId, String runtime) {
        Sandbox sandbox = sandboxCache.get(sandboxId);
        if (sandbox == null) {
            log.warn("Sandbox not found in cache for endpoint query: {}", sandboxId);
            return null;
        }

        try {
            // 尝试获取沙箱的主机端口映射
            // OpenSandbox 的 Sandbox 实例在创建时会分配端口映射
            // 通过执行命令确认应用正在监听
            int targetPort = getPortForRuntime(runtime);
            SandboxManager.ExecutionResult result = execute(sandbox,
                    "curl -s -o /dev/null -w '%{http_code}' http://localhost:" + targetPort + "/ 2>/dev/null || echo 'not_ready'");

            if (result.success() && !result.stdout().contains("not_ready")) {
                // 应用正在运行，构建端点 URL
                // 在 Docker bridge 网络模式下，需要使用宿主机映射的端口
                // 这里返回 null 让调用方使用 fallback 策略（通过 Traefik 或直接端口映射）
                return null;
            }
        } catch (Exception e) {
            log.warn("Failed to check endpoint for sandbox {}: {}", sandboxId, e.getMessage());
        }

        return null;
    }

    private int getPortForRuntime(String runtime) {
        return switch (runtime) {
            case "node" -> 3000;
            case "python" -> 8000;
            case "java" -> 8080;
            default -> 3000;
        };
    }

    public record ExecutionResult(boolean success, String stdout, String stderr, Exception error) {
        public boolean hasError() {
            return error != null || !stderr.isEmpty();
        }
    }
}
