package cn.bitloom.project.service;

import cn.bitloom.project.entity.DesignProposalEntity;
import cn.bitloom.project.repository.DesignProposalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DesignProposalService {

    private final DesignProposalRepository designProposalRepository;

    public Mono<DesignProposalEntity> create(DesignProposalEntity entity) {
        entity.setStatus(entity.getStatus() != null ? entity.getStatus() : "DRAFT");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return designProposalRepository.save(entity);
    }

    public Mono<DesignProposalEntity> findById(Long id) {
        return designProposalRepository.findById(id);
    }

    public Flux<DesignProposalEntity> findByProjectId(Long projectId) {
        return designProposalRepository.findByProjectId(projectId);
    }

    public Mono<DesignProposalEntity> update(Long id, DesignProposalEntity updates) {
        return designProposalRepository.findById(id)
                .flatMap(existing -> {
                    if (updates.getTitle() != null) {
                        existing.setTitle(updates.getTitle());
                    }
                    if (updates.getContent() != null) {
                        existing.setContent(updates.getContent());
                    }
                    if (updates.getRequirementId() != null) {
                        existing.setRequirementId(updates.getRequirementId());
                    }
                    existing.setUpdatedAt(LocalDateTime.now());
                    return designProposalRepository.save(existing);
                });
    }

    public Mono<DesignProposalEntity> submit(Long id) {
        return designProposalRepository.findById(id)
                .flatMap(entity -> {
                    entity.setStatus("SUBMITTED");
                    entity.setUpdatedAt(LocalDateTime.now());
                    return designProposalRepository.save(entity);
                });
    }

    public Mono<DesignProposalEntity> review(Long id, String reviewerId, String comment, boolean approved) {
        return designProposalRepository.findById(id)
                .flatMap(entity -> {
                    entity.setStatus(approved ? "APPROVED" : "REJECTED");
                    entity.setReviewerId(reviewerId);
                    entity.setReviewComment(comment);
                    entity.setUpdatedAt(LocalDateTime.now());
                    return designProposalRepository.save(entity);
                });
    }

    public Mono<Void> delete(Long id) {
        return designProposalRepository.deleteById(id);
    }
}
