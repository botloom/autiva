package cn.bitloom.project.controller;

import cn.bitloom.project.entity.DesignProposalEntity;
import cn.bitloom.project.service.DesignProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class DesignProposalController {

    private final DesignProposalService designProposalService;

    @PostMapping("/api/projects/{projectId}/design-proposals")
    public Mono<ResponseEntity<DesignProposalEntity>> create(
            @PathVariable Long projectId,
            @RequestBody DesignProposalEntity entity) {
        entity.setProjectId(projectId);
        return designProposalService.create(entity)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/api/projects/{projectId}/design-proposals")
    public Mono<ResponseEntity<Iterable<DesignProposalEntity>>> findByProjectId(
            @PathVariable Long projectId) {
        return designProposalService.findByProjectId(projectId)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @GetMapping("/api/design-proposals/{id}")
    public Mono<ResponseEntity<DesignProposalEntity>> findById(@PathVariable Long id) {
        return designProposalService.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/api/design-proposals/{id}")
    public Mono<ResponseEntity<DesignProposalEntity>> update(
            @PathVariable Long id,
            @RequestBody DesignProposalEntity entity) {
        return designProposalService.update(id, entity)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/design-proposals/{id}/submit")
    public Mono<ResponseEntity<DesignProposalEntity>> submit(@PathVariable Long id) {
        return designProposalService.submit(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/design-proposals/{id}/review")
    public Mono<ResponseEntity<DesignProposalEntity>> review(
            @PathVariable Long id,
            @RequestParam String reviewerId,
            @RequestParam(required = false) String comment,
            @RequestParam boolean approved) {
        return designProposalService.review(id, reviewerId, comment, approved)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/design-proposals/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable Long id) {
        return designProposalService.delete(id)
                .thenReturn(ResponseEntity.ok().<Void>build());
    }
}
