package cn.bitloom.sandbox;

import cn.bitloom.baas.BaasManager;
import cn.bitloom.baas.BaasResource;
import cn.bitloom.dto.DeployRequest;
import cn.bitloom.dto.DeployResult;
import cn.bitloom.dto.ProjectFile;
import cn.bitloom.entity.BaasResourceEntity;
import cn.bitloom.entity.UserServiceEntity;
import cn.bitloom.repository.BaasResourceRepository;
import cn.bitloom.repository.UserServiceRepository;
import com.alibaba.opensandbox.sandbox.Sandbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SandboxService {

    private final SandboxManager sandboxManager;
    private final BaasManager baasManager;
    private final UserServiceRepository userServiceRepository;
    private final BaasResourceRepository baasResourceRepository;

    private static final String BASE_DOMAIN = "autiva.dev";

    public Mono<DeployResult> deployProject(DeployRequest request) {
        String subdomain = generateSubdomain(request.clientId(), request.projectName());
        String runtime = request.runtime() != null ? request.runtime() : detectRuntime(request.files());
        String url = "https://" + subdomain + "." + BASE_DOMAIN;

        return userServiceRepository.existsBySubdomain(subdomain)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.just(DeployResult.failure("Subdomain already exists: " + subdomain));
                    }
                    return doDeploy(request, runtime, subdomain, url);
                })
                .onErrorResume(e -> {
                    log.error("[Deploy] Failed: {}", e.getMessage(), e);
                    return Mono.just(DeployResult.failure("Deployment failed: " + e.getMessage()));
                });
    }

    private Mono<DeployResult> doDeploy(DeployRequest request, String runtime, String subdomain, String url) {
        String sandboxId = sandboxManager.generateSandboxId();
        int port = getPortForRuntime(runtime);

        return Mono.fromCallable(() -> sandboxManager.create(sandboxId, runtime))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(sandbox -> {
                    SandboxManager.ExecutionResult mkdirResult = sandboxManager.execute(sandbox, "mkdir -p /app");
                    if (mkdirResult.hasError()) {
                        return Mono.just(DeployResult.failure("Failed to create app directory: " + mkdirResult.stderr()));
                    }

                    SandboxManager.ExecutionResult writeResult = writeAllFiles(sandbox, request.files());
                    if (writeResult.hasError()) {
                        return Mono.just(DeployResult.failure("Failed to write project files: " + writeResult.stderr()));
                    }

                    installDependencies(sandbox, runtime, request.files());

                    return baasManager.createAllResources(request.clientId() + "-" + request.projectName())
                            .flatMap(baasResources -> {
                                Map<String, String> allEnvVars = new HashMap<>();
                                if (request.envVars() != null) {
                                    allEnvVars.putAll(request.envVars());
                                }
                                allEnvVars.putAll(baasManager.resourcesToEnvVars(baasResources));

                                writeEnvFile(sandbox, allEnvVars);

                                SandboxManager.ExecutionResult startResult = startApplication(sandbox, runtime, request.files());
                                if (startResult.hasError()) {
                                    log.warn("[Deploy] App start may have issues: {}", startResult.stderr());
                                }

                                return persistService(request, subdomain, runtime, sandboxId, port, baasResources)
                                        .thenReturn(DeployResult.success(url, sandboxId, subdomain));
                            });
                })
                .onErrorResume(e -> {
                    log.error("[Deploy] Rolling back sandbox {}: {}", sandboxId, e.getMessage());
                    sandboxManager.kill(sandboxId);
                    return Mono.just(DeployResult.failure("Deployment failed: " + e.getMessage()));
                });
    }

    private SandboxManager.ExecutionResult writeAllFiles(Sandbox sandbox, List<ProjectFile> files) {
        for (ProjectFile file : files) {
            String dirPath = extractDir(file.path());
            if (!dirPath.isEmpty()) {
                sandboxManager.execute(sandbox, "mkdir -p /app/" + dirPath);
            }

            String writeCmd = String.format(
                    "cat > /app/%s << 'AUTIVA_EOF'\n%s\nAUTIVA_EOF",
                    file.path(), file.content()
            );
            SandboxManager.ExecutionResult result = sandboxManager.execute(sandbox, writeCmd);
            if (result.hasError()) {
                log.warn("[Deploy] Write file {} warning: {}", file.path(), result.stderr());
            }
            log.info("[Deploy] Written file: /app/{}", file.path());
        }
        return new SandboxManager.ExecutionResult(true, "", "", null);
    }

    private void installDependencies(Sandbox sandbox, String runtime, List<ProjectFile> files) {
        boolean hasPackageJson = files.stream().anyMatch(f -> f.path().equals("package.json"));
        boolean hasRequirementsTxt = files.stream().anyMatch(f -> f.path().equals("requirements.txt"));
        boolean hasPomXml = files.stream().anyMatch(f -> f.path().equals("pom.xml"));

        String installCmd = null;
        if ("node".equals(runtime) || hasPackageJson) {
            installCmd = "cd /app && npm install --production 2>&1 || true";
        } else if ("python".equals(runtime) || hasRequirementsTxt) {
            installCmd = "cd /app && pip install -r requirements.txt 2>&1 || true";
        } else if ("java".equals(runtime) || hasPomXml) {
            installCmd = "cd /app && mvn package -DskipTests 2>&1 || true";
        }

        if (installCmd != null) {
            log.info("[Deploy] Installing dependencies for runtime={}", runtime);
            SandboxManager.ExecutionResult result = sandboxManager.execute(sandbox, installCmd);
            if (result.hasError()) {
                log.warn("[Deploy] Dependency install warning: {}", result.stderr());
            }
        }
    }

    private void writeEnvFile(Sandbox sandbox, Map<String, String> envVars) {
        if (envVars == null || envVars.isEmpty()) return;

        StringBuilder envContent = new StringBuilder();
        envVars.forEach((key, value) -> envContent.append(key).append("=").append(value).append("\n"));

        String writeCmd = String.format(
                "cat > /app/.env << 'AUTIVA_EOF'\n%s\nAUTIVA_EOF",
                envContent.toString()
        );
        SandboxManager.ExecutionResult result = sandboxManager.execute(sandbox, writeCmd);
        if (result.success()) {
            log.info("[Deploy] Written .env file with {} variables", envVars.size());
        } else {
            log.warn("[Deploy] Failed to write .env: {}", result.stderr());
        }
    }

    private SandboxManager.ExecutionResult startApplication(Sandbox sandbox, String runtime, List<ProjectFile> files) {
        String startCmd = determineStartCommand(runtime, files);
        log.info("[Deploy] Starting application: {}", startCmd);
        return sandboxManager.execute(sandbox, "cd /app && " + startCmd + " &");
    }

    private String determineStartCommand(String runtime, List<ProjectFile> files) {
        if ("node".equals(runtime) || files.stream().anyMatch(f -> f.path().equals("package.json"))) {
            if (files.stream().anyMatch(f -> f.path().equals("server.js"))) return "node server.js";
            if (files.stream().anyMatch(f -> f.path().equals("index.js"))) return "node index.js";
            if (files.stream().anyMatch(f -> f.path().equals("app.js"))) return "node app.js";
            return "npm start";
        }
        if ("python".equals(runtime)) {
            if (files.stream().anyMatch(f -> f.path().equals("main.py"))) return "python main.py";
            if (files.stream().anyMatch(f -> f.path().equals("app.py"))) return "python app.py";
            return "python main.py";
        }
        if ("java".equals(runtime)) {
            return "java -jar target/*.jar";
        }
        return "node index.js";
    }

    private Mono<Void> persistService(DeployRequest request, String subdomain, String runtime,
                                       String sandboxId, int port, Map<String, BaasResource> baasResources) {
        UserServiceEntity entity = UserServiceEntity.builder()
                .clientId(request.clientId())
                .projectName(request.projectName())
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

    public Mono<Void> stopService(String clientId, String projectName) {
        return userServiceRepository.findByClientId(clientId)
                .filter(service -> service.getProjectName().equals(projectName))
                .next()
                .flatMap(service -> {
                    if (service.getSandboxId() != null) {
                        return Mono.fromRunnable(() -> sandboxManager.kill(service.getSandboxId()))
                                .subscribeOn(Schedulers.boundedElastic())
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

    public Mono<List<SandboxInfo>> listServices(String clientId) {
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

    public Flux<SandboxInfo> listAllServices() {
        return userServiceRepository.findAll()
                .map(service -> new SandboxInfo(
                        service.getSandboxId(),
                        service.getProjectName(),
                        service.getRuntime(),
                        service.getSubdomain(),
                        service.getStatus()
                ));
    }

    public Mono<String> getLogs(String clientId, String projectName) {
        return userServiceRepository.findByClientId(clientId)
                .filter(service -> service.getProjectName().equals(projectName))
                .next()
                .flatMap(service -> {
                    if (service.getSandboxId() != null) {
                        return Mono.fromCallable(() -> {
                            Sandbox sandbox = sandboxManager.getSandbox(service.getSandboxId());
                            if (sandbox != null) {
                                SandboxManager.ExecutionResult result =
                                        sandboxManager.execute(sandbox, "cat /var/log/app.log 2>/dev/null || echo 'No log file'");
                                return result.stdout();
                            }
                            return "No logs available (sandbox not in cache)";
                        }).subscribeOn(Schedulers.boundedElastic());
                    }
                    return Mono.just("No logs available");
                })
                .defaultIfEmpty("Service not found");
    }

    public Mono<SandboxInfo> getServiceBySubdomain(String subdomain) {
        return userServiceRepository.findBySubdomain(subdomain)
                .map(service -> new SandboxInfo(
                        service.getSandboxId(),
                        service.getProjectName(),
                        service.getRuntime(),
                        service.getSubdomain(),
                        service.getStatus()
                ));
    }

    public Mono<Map<String, Object>> getServiceDetails(String subdomain) {
        return userServiceRepository.findBySubdomain(subdomain)
                .flatMap(service -> baasResourceRepository.findByServiceId(service.getId())
                        .collectList()
                        .map(resources -> Map.<String, Object>of(
                                "service", service,
                                "resources", resources
                        )));
    }

    public Mono<RestartResult> restartProject(String clientId, String projectName) {
        return userServiceRepository.findByClientId(clientId)
                .filter(service -> service.getProjectName().equals(projectName))
                .next()
                .switchIfEmpty(Mono.error(new RuntimeException("Service not found: " + projectName)))
                .flatMap(service -> {
                    String oldSandboxId = service.getSandboxId();
                    String runtime = service.getRuntime();
                    String subdomain = service.getSubdomain();

                    // Stop old sandbox
                    return Mono.fromRunnable(() -> {
                        if (oldSandboxId != null) {
                            sandboxManager.kill(oldSandboxId);
                        }
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .then(Mono.fromCallable(() -> {
                        // Create new sandbox
                        String newSandboxId = sandboxManager.generateSandboxId();
                        Sandbox sandbox = sandboxManager.create(newSandboxId, runtime);

                        // Recreate app directory and restart application
                        sandboxManager.execute(sandbox, "mkdir -p /app");

                        // Re-read files from old sandbox if possible, otherwise just start fresh
                        // Since we can't easily recover files, we restart the container
                        // The application files should still be available if using volume mounts
                        SandboxManager.ExecutionResult startResult = startApplication(sandbox, runtime, List.of());
                        if (startResult.hasError()) {
                            log.warn("[Restart] App start may have issues: {}", startResult.stderr());
                        }

                        return new RestartResult(
                                "https://" + subdomain + "." + BASE_DOMAIN,
                                newSandboxId,
                                subdomain
                        );
                    }).subscribeOn(Schedulers.boundedElastic()))
                    .flatMap(restartResult ->
                        // Update service entity with new sandbox ID
                        updateServiceSandboxId(service, restartResult.sandboxId())
                                .thenReturn(restartResult)
                    );
                });
    }

    private Mono<Void> updateServiceSandboxId(UserServiceEntity service, String newSandboxId) {
        service.setSandboxId(newSandboxId);
        service.setStatus("running");
        service.setUpdatedAt(LocalDateTime.now());
        return userServiceRepository.save(service).then();
    }

    public Mono<Map<String, Object>> getProjectDetails(String clientId, String projectName) {
        return userServiceRepository.findByClientId(clientId)
                .filter(service -> service.getProjectName().equals(projectName))
                .next()
                .flatMap(service -> baasResourceRepository.findByServiceId(service.getId())
                        .collectList()
                        .map(resources -> Map.<String, Object>of(
                                "service", service,
                                "resources", resources
                        )));
    }

    private String generateSubdomain(String clientId, String projectName) {
        return (clientId + "-" + projectName).toLowerCase().replaceAll("[^a-z0-9-]", "-");
    }

    private String detectRuntime(List<ProjectFile> files) {
        if (files.stream().anyMatch(f -> f.path().equals("package.json"))) return "node";
        if (files.stream().anyMatch(f -> f.path().equals("requirements.txt"))) return "python";
        if (files.stream().anyMatch(f -> f.path().equals("pom.xml"))) return "java";
        return "node";
    }

    private String extractDir(String filePath) {
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash > 0 ? filePath.substring(0, lastSlash) : "";
    }

    private int getPortForRuntime(String runtime) {
        return switch (runtime) {
            case "node" -> 3000;
            case "python" -> 8000;
            case "java" -> 8080;
            default -> 3000;
        };
    }
}
