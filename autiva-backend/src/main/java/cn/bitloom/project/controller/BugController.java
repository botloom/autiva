package cn.bitloom.project.controller;

import cn.bitloom.project.entity.BugEntity;
import cn.bitloom.project.service.BugService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class BugController {

    private final BugService bugService;

    @PostMapping("/api/projects/{projectId}/bugs")
    public Mono<ResponseEntity<BugEntity>> create(
            @PathVariable Long projectId,
            @RequestBody BugEntity entity) {
        entity.setProjectId(projectId);
        return bugService.create(entity)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/api/projects/{projectId}/bugs")
    public Mono<ResponseEntity<Iterable<BugEntity>>> findByProjectId(
            @PathVariable Long projectId,
            @RequestParam(required = false) String status) {
        if (status != null) {
            return bugService.findByProjectIdAndStatus(projectId, status)
                    .collectList()
                    .map(ResponseEntity::ok);
        }
        return bugService.findByProjectId(projectId)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @GetMapping("/api/bugs/{id}")
    public Mono<ResponseEntity<BugEntity>> findById(@PathVariable Long id) {
        return bugService.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/api/bugs/{id}")
    public Mono<ResponseEntity<BugEntity>> update(
            @PathVariable Long id,
            @RequestBody BugEntity entity) {
        return bugService.update(id, entity)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/bugs/{id}/assign")
    public Mono<ResponseEntity<BugEntity>> assign(
            @PathVariable Long id,
            @RequestParam String assigneeId) {
        return bugService.assign(id, assigneeId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/bugs/{id}/fix")
    public Mono<ResponseEntity<BugEntity>> fix(
            @PathVariable Long id,
            @RequestParam String fixDescription) {
        return bugService.fix(id, fixDescription)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/bugs/{id}/verify")
    public Mono<ResponseEntity<BugEntity>> verify(@PathVariable Long id) {
        return bugService.verify(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/bugs/{id}/close")
    public Mono<ResponseEntity<BugEntity>> close(@PathVariable Long id) {
        return bugService.close(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/bugs/{id}/reopen")
    public Mono<ResponseEntity<BugEntity>> reopen(@PathVariable Long id) {
        return bugService.reopen(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/bugs/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable Long id) {
        return bugService.delete(id)
                .thenReturn(ResponseEntity.ok().<Void>build());
    }
}
