package cn.bitloom.controller;

import cn.bitloom.dto.DeployRequest;
import cn.bitloom.dto.DeployResult;
import cn.bitloom.sandbox.SandboxInfo;
import cn.bitloom.sandbox.SandboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sandbox")
@RequiredArgsConstructor
public class SandboxController {

    private final SandboxService sandboxService;

    @PostMapping("/deploy")
    public Mono<ResponseEntity<DeployResult>> deployProject(@RequestBody DeployRequest request) {
        return sandboxService.deployProject(request)
                .map(result -> result.success()
                        ? ResponseEntity.ok(result)
                        : ResponseEntity.badRequest().body(result));
    }

    @PostMapping("/stop")
    public Mono<ResponseEntity<Map<String, Object>>> stopProject(
            @RequestParam String clientId,
            @RequestParam String projectName) {
        return sandboxService.stopService(clientId, projectName)
                .thenReturn(ResponseEntity.ok(Map.<String, Object>of("success", true, "message", "Service stopped")))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError()
                        .body(Map.<String, Object>of("success", false, "message", e.getMessage()))));
    }

    @GetMapping("/status")
    public Mono<ResponseEntity<List<SandboxInfo>>> getStatus(@RequestParam String clientId) {
        return sandboxService.listServices(clientId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/logs")
    public Mono<ResponseEntity<Map<String, String>>> getLogs(
            @RequestParam String clientId,
            @RequestParam String projectName) {
        return sandboxService.getLogs(clientId, projectName)
                .map(logs -> ResponseEntity.ok(Map.of("logs", logs)));
    }

    @PostMapping("/restart")
    public Mono<ResponseEntity<Map<String, Object>>> restartProject(
            @RequestParam String clientId,
            @RequestParam String projectName) {
        return sandboxService.restartProject(clientId, projectName)
                .map(result -> ResponseEntity.ok(Map.<String, Object>of(
                        "success", true,
                        "url", result.url(),
                        "sandboxId", result.sandboxId(),
                        "subdomain", result.subdomain()
                )))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError()
                        .body(Map.<String, Object>of("success", false, "message", e.getMessage()))));
    }

    @GetMapping("/details")
    public Mono<ResponseEntity<Map<String, Object>>> getProjectDetails(
            @RequestParam String clientId,
            @RequestParam String projectName) {
        return sandboxService.getProjectDetails(clientId, projectName)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{subdomain}")
    public Mono<ResponseEntity<SandboxInfo>> getSandboxBySubdomain(@PathVariable String subdomain) {
        return sandboxService.getServiceBySubdomain(subdomain)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{subdomain}/details")
    public Mono<ResponseEntity<Map<String, Object>>> getServiceWithResources(@PathVariable String subdomain) {
        return sandboxService.getServiceDetails(subdomain)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
