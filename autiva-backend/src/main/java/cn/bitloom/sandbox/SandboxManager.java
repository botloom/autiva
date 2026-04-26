package cn.bitloom.sandbox;

import com.alibaba.opensandbox.sandbox.Sandbox;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.Execution;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
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

    public record ExecutionResult(boolean success, String stdout, String stderr, Exception error) {
        public boolean hasError() {
            return error != null || !stderr.isEmpty();
        }
    }
}
