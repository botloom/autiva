package cn.bitloom.project.controller;

import cn.bitloom.project.entity.TestCaseEntity;
import cn.bitloom.project.service.TestCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseService testCaseService;

    @PostMapping("/api/projects/{projectId}/test-cases")
    public Mono<ResponseEntity<TestCaseEntity>> create(
            @PathVariable Long projectId,
            @RequestBody TestCaseEntity entity) {
        entity.setProjectId(projectId);
        return testCaseService.create(entity)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/api/projects/{projectId}/test-cases")
    public Mono<ResponseEntity<Iterable<TestCaseEntity>>> findByProjectId(
            @PathVariable Long projectId) {
        return testCaseService.findByProjectId(projectId)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @GetMapping("/api/test-cases/{id}")
    public Mono<ResponseEntity<TestCaseEntity>> findById(@PathVariable Long id) {
        return testCaseService.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/api/test-cases/{id}")
    public Mono<ResponseEntity<TestCaseEntity>> update(
            @PathVariable Long id,
            @RequestBody TestCaseEntity entity) {
        return testCaseService.update(id, entity)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/test-cases/{id}/submit")
    public Mono<ResponseEntity<TestCaseEntity>> submit(@PathVariable Long id) {
        return testCaseService.submit(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/test-cases/{id}/review")
    public Mono<ResponseEntity<TestCaseEntity>> review(
            @PathVariable Long id,
            @RequestParam String reviewerId,
            @RequestParam(required = false) String comment,
            @RequestParam boolean approved) {
        return testCaseService.review(id, reviewerId, comment, approved)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/test-cases/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable Long id) {
        return testCaseService.delete(id)
                .thenReturn(ResponseEntity.ok().<Void>build());
    }
}
