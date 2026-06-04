package cn.bitloom.project.controller;

import cn.bitloom.project.entity.RequirementEntity;
import cn.bitloom.project.service.RequirementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class RequirementController {

    private final RequirementService requirementService;

    @PostMapping("/api/projects/{projectId}/requirements")
    public Mono<ResponseEntity<RequirementEntity>> create(
            @PathVariable Long projectId,
            @RequestBody RequirementEntity entity) {
        entity.setProjectId(projectId);
        return requirementService.create(entity)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/api/projects/{projectId}/requirements")
    public Mono<ResponseEntity<Iterable<RequirementEntity>>> findByProjectId(
            @PathVariable Long projectId,
            @RequestParam(required = false) String status) {
        if (status != null) {
            return requirementService.findByProjectIdAndStatus(projectId, status)
                    .collectList()
                    .map(ResponseEntity::ok);
        }
        return requirementService.findByProjectId(projectId)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @GetMapping("/api/requirements/{id}")
    public Mono<ResponseEntity<RequirementEntity>> findById(@PathVariable Long id) {
        return requirementService.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/api/requirements/{id}")
    public Mono<ResponseEntity<RequirementEntity>> update(
            @PathVariable Long id,
            @RequestBody RequirementEntity entity) {
        return requirementService.update(id, entity)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/requirements/{id}/submit")
    public Mono<ResponseEntity<RequirementEntity>> submit(@PathVariable Long id) {
        return requirementService.submit(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/requirements/{id}/review")
    public Mono<ResponseEntity<RequirementEntity>> review(
            @PathVariable Long id,
            @RequestParam String reviewerId,
            @RequestParam(required = false) String comment) {
        return requirementService.review(id, reviewerId, comment)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/requirements/{id}/approve")
    public Mono<ResponseEntity<RequirementEntity>> approve(
            @PathVariable Long id,
            @RequestParam String reviewerId,
            @RequestParam(required = false) String comment) {
        return requirementService.approve(id, reviewerId, comment)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/requirements/{id}/reject")
    public Mono<ResponseEntity<RequirementEntity>> reject(
            @PathVariable Long id,
            @RequestParam String reviewerId,
            @RequestParam(required = false) String comment) {
        return requirementService.reject(id, reviewerId, comment)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/requirements/{id}/start-implementation")
    public Mono<ResponseEntity<RequirementEntity>> startImplementation(@PathVariable Long id) {
        return requirementService.startImplementation(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/requirements/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable Long id) {
        return requirementService.delete(id)
                .thenReturn(ResponseEntity.ok().<Void>build());
    }
}
