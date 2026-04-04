package cn.bitloom.sandbox;

import cn.bitloom.baas.BaasManager;
import cn.bitloom.baas.BaasResource;
import cn.bitloom.entity.BaasResourceEntity;
import cn.bitloom.entity.UserServiceEntity;
import cn.bitloom.repository.BaasResourceRepository;
import cn.bitloom.repository.UserServiceRepository;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.opensandbox.sandbox.Sandbox;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.Execution;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SandboxService {

    private final BaasManager baasManager;
    private final UserServiceRepository userServiceRepository;
    private final BaasResourceRepository baasResourceRepository;

    private final String baseDomain = "autiva.dev";

    private final Map<String, Sandbox> sandboxCache = new ConcurrentHashMap<>();

    private static final Map<String, String> RUNTIME_IMAGES = Map.of(
            "node", "opensandbox/code-interpreter:v1.0.2",
            "python", "opensandbox/code-interpreter:v1.0.2",
            "java", "opensandbox/code-interpreter:v1.0.2"
    );

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

    public Mono<SandboxResult> createSandbox(String clientId, String projectName, String code, String runtime) {
        String subdomain = generateSubdomain(clientId, projectName);
        String url = "https://" + subdomain + "." + baseDomain;

        return checkSubdomainExists(subdomain)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.just(SandboxResult.failure("Subdomain already exists: " + subdomain));
                    }
                    return createSandboxInternal(clientId, projectName, code, runtime, subdomain, url);
                })
                .onErrorResume(e -> {
                    log.error("Failed to create sandbox: {}", e.getMessage(), e);
                    return Mono.just(SandboxResult.failure("Failed to create sandbox: " + e.getMessage()));
                });
    }

    private Mono<SandboxResult> createSandboxInternal(String clientId, String projectName, String code,
                                                        String runtime, String subdomain, String url) {
        String image = RUNTIME_IMAGES.getOrDefault(runtime, RUNTIME_IMAGES.get("node"));
        String sandboxId = "sandbox-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);

        return Mono.fromCallable(() -> {
                    Sandbox sandbox = Sandbox.builder()
                            .image(image)
                            .build();
                    
                    sandboxCache.put(sandboxId, sandbox);
                    log.info("Sandbox created: id={}", sandboxId);
                    
                    return sandbox;
                })
                .flatMap(sandbox -> deployCode(sandbox, code, runtime)
                            .then(createBaasResources(clientId, projectName))
                            .flatMap(baasResources -> saveServiceEntity(
                                    clientId, projectName, subdomain, runtime,
                                    sandboxId, null, baasResources
                            ))
                            .thenReturn(SandboxResult.success(url, sandboxId)));
    }

    private Mono<Void> deployCode(Sandbox sandbox, String code, String runtime) {
        return Mono.fromRunnable(() -> {
            try {
                String entryFile = getEntryFile(runtime);
                String writeCommand = String.format("mkdir -p /app && echo '%s' > %s", 
                        code.replace("'", "'\\''"), entryFile);
                
                Execution writeExecution = sandbox.commands().run(writeCommand);
                logExecution(writeExecution, "write file");
                
                String startCommand = getStartCommand(runtime);
                Execution startExecution = sandbox.commands().run(startCommand);
                logExecution(startExecution, "start application");
                
            } catch (Exception e) {
                log.error("Failed to deploy code: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to deploy code", e);
            }
        });
    }

    private void logExecution(Execution execution, String operation) {
        if (execution.getLogs() != null && execution.getLogs().getStdout() != null) {
            execution.getLogs().getStdout().forEach(outputMsg -> 
                    log.debug("[{}] {}", operation, outputMsg.getText()));
        }
        if (execution.getLogs() != null && execution.getLogs().getStderr() != null) {
            execution.getLogs().getStderr().forEach(outputMsg -> 
                    log.error("[{}] {}", operation, outputMsg.getText()));
        }
    }

    private Mono<Map<String, BaasResource>> createBaasResources(String clientId, String projectName) {
        String serviceId = clientId + "-" + projectName;
        return baasManager.createAllResources(serviceId);
    }

    private Mono<Void> saveServiceEntity(String clientId, String projectName, String subdomain,
                                          String runtime, String sandboxId, Integer port,
                                          Map<String, BaasResource> baasResources) {
        UserServiceEntity entity = UserServiceEntity.builder()
                .clientId(clientId)
                .projectName(projectName)
                .subdomain(subdomain)
                .runtime(runtime)
                .status("running")
                .sandboxId(sandboxId)
                .port(port)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return userServiceRepository.save(entity)
                .flatMap(saved -> {
                    List<BaasResourceEntity> resourceEntities = baasResources.entrySet().stream()
                            .map(entry -> BaasResourceEntity.builder()
                                    .serviceId(saved.getId())
                                    .resourceType(entry.getKey())
                                    .resourceName(entry.getValue().name())
                                    .connectionInfo(entry.getValue().connectionInfo())
                                    .createdAt(LocalDateTime.now())
                                    .build())
                            .toList();

                    return baasResourceRepository.saveAll(resourceEntities).then();
                });
    }

    public Mono<Void> stopSandbox(String clientId, String projectName) {
        return userServiceRepository.findByClientId(clientId)
                .filter(service -> service.getProjectName().equals(projectName))
                .next()
                .flatMap(service -> {
                    if (service.getSandboxId() != null) {
                        return Mono.fromRunnable(() -> {
                                    Sandbox sandbox = sandboxCache.remove(service.getSandboxId());
                                    if (sandbox != null) {
                                        sandbox.kill();
                                        log.info("Sandbox killed: {}", service.getSandboxId());
                                    }
                                })
                                .then(updateServiceStatus(service, "stopped"));
                    }
                    return updateServiceStatus(service, "stopped");
                });
    }

    private Mono<Void> updateServiceStatus(UserServiceEntity service, String status) {
        service.setStatus(status);
        service.setUpdatedAt(LocalDateTime.now());
        return userServiceRepository.save(service).then();
    }

    public Mono<List<SandboxInfo>> getStatus(String clientId) {
        return userServiceRepository.findByClientId(clientId)
                .map(service -> new SandboxInfo(
                        service.getSandboxId(),
                        service.getProjectName(),
                        service.getRuntime(),
                        service.getSubdomain(),
                        service.getStatus()
                ))
                .collectList();
    }

    public Mono<String> getLogs(String clientId, String projectName) {
        return userServiceRepository.findByClientId(clientId)
                .filter(service -> service.getProjectName().equals(projectName))
                .next()
                .flatMap(service -> {
                    if (service.getSandboxId() != null) {
                        return Mono.fromCallable(() -> {
                            Sandbox sandbox = sandboxCache.get(service.getSandboxId());
                            if (sandbox != null) {
                                Execution execution = sandbox.commands().run("cat /var/log/app.log");
                                if (execution.getLogs() != null && execution.getLogs().getStdout() != null) {
                                    StringBuilder sb = new StringBuilder();
                                    execution.getLogs().getStdout().forEach(log -> sb.append(log.getText()).append("\n"));
                                    return sb.toString();
                                }
                            }
                            return "No logs available";
                        });
                    }
                    return Mono.just("No logs available");
                })
                .defaultIfEmpty("Service not found");
    }

    public Mono<SandboxInfo> getSandboxBySubdomain(String subdomain) {
        return userServiceRepository.findBySubdomain(subdomain)
                .map(service -> new SandboxInfo(
                        service.getSandboxId(),
                        service.getProjectName(),
                        service.getRuntime(),
                        service.getSubdomain(),
                        service.getStatus()
                ));
    }

    public Mono<JSONObject> getServiceWithResources(String subdomain) {
        return userServiceRepository.findBySubdomain(subdomain)
                .flatMap(service -> baasResourceRepository.findByServiceId(service.getId())
                        .collectList()
                        .map(resources -> {
                            JSONObject result = new JSONObject();
                            result.put("service", service);
                            result.put("resources", resources);
                            return result;
                        }));
    }

    private Mono<Boolean> checkSubdomainExists(String subdomain) {
        return userServiceRepository.existsBySubdomain(subdomain);
    }

    private String generateSubdomain(String clientId, String projectName) {
        String combined = clientId + "-" + projectName;
        return combined.toLowerCase().replaceAll("[^a-z0-9-]", "-");
    }

    private String getEntryFile(String runtime) {
        return switch (runtime) {
            case "node" -> "/app/index.js";
            case "python" -> "/app/main.py";
            case "java" -> "/app/Main.java";
            default -> "/app/main";
        };
    }

    private String getStartCommand(String runtime) {
        return switch (runtime) {
            case "node" -> "node /app/index.js";
            case "python" -> "python /app/main.py";
            case "java" -> "java /app/Main.java";
            default -> "/app/main";
        };
    }
}
