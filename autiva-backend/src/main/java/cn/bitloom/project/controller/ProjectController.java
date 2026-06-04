package cn.bitloom.project.controller;

import cn.bitloom.project.entity.ProjectEntity;
import cn.bitloom.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public Mono<ResponseEntity<ProjectEntity>> create(@RequestBody ProjectEntity entity) {
        return projectService.create(entity)
                .map(ResponseEntity::ok);
    }

    @GetMapping
    public Mono<ResponseEntity<Iterable<ProjectEntity>>> findAll() {
        return projectService.findAll()
                .collectList()
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ProjectEntity>> findById(@PathVariable Long id) {
        return projectService.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/owner/{ownerId}")
    public Mono<ResponseEntity<Iterable<ProjectEntity>>> findByOwnerId(@PathVariable String ownerId) {
        return projectService.findByOwnerId(ownerId)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<ProjectEntity>> update(@PathVariable Long id, @RequestBody ProjectEntity entity) {
        return projectService.update(id, entity)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    public Mono<ResponseEntity<ProjectEntity>> transitionStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return projectService.transitionStatus(id, status)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable Long id) {
        return projectService.delete(id)
                .thenReturn(ResponseEntity.ok().<Void>build());
    }
}
