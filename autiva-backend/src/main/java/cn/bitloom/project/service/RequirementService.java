package cn.bitloom.project.service;

import cn.bitloom.project.entity.NotificationEntity;
import cn.bitloom.project.entity.RequirementEntity;
import cn.bitloom.project.repository.NotificationRepository;
import cn.bitloom.project.repository.RequirementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementService {

    private final RequirementRepository requirementRepository;
    private final NotificationRepository notificationRepository;

    public Mono<RequirementEntity> create(RequirementEntity entity) {
        entity.setStatus(entity.getStatus() != null ? entity.getStatus() : "DRAFT");
        entity.setPriority(entity.getPriority() != null ? entity.getPriority() : "MEDIUM");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return requirementRepository.save(entity);
    }

    public Mono<RequirementEntity> findById(Long id) {
        return requirementRepository.findById(id);
    }

    public Flux<RequirementEntity> findByProjectId(Long projectId) {
        return requirementRepository.findByProjectId(projectId);
    }

    public Flux<RequirementEntity> findByProjectIdAndStatus(Long projectId, String status) {
        return requirementRepository.findByProjectIdAndStatus(projectId, status);
    }

    public Mono<RequirementEntity> update(Long id, RequirementEntity updates) {
        return requirementRepository.findById(id)
                .flatMap(existing -> {
                    if (updates.getTitle() != null) {
                        existing.setTitle(updates.getTitle());
                    }
                    if (updates.getDescription() != null) {
                        existing.setDescription(updates.getDescription());
                    }
                    if (updates.getPriority() != null) {
                        existing.setPriority(updates.getPriority());
                    }
                    existing.setUpdatedAt(LocalDateTime.now());
                    return requirementRepository.save(existing);
                });
    }

    public Mono<RequirementEntity> submit(Long id) {
        return requirementRepository.findById(id)
                .flatMap(entity -> {
                    entity.setStatus("SUBMITTED");
                    entity.setUpdatedAt(LocalDateTime.now());
                    return requirementRepository.save(entity)
                            .flatMap(saved -> createNotification(saved, "REQUIREMENT_SUBMITTED")
                                    .thenReturn(saved));
                });
    }

    public Mono<RequirementEntity> review(Long id, String reviewerId, String comment) {
        return requirementRepository.findById(id)
                .flatMap(entity -> {
                    entity.setStatus("IN_REVIEW");
                    entity.setReviewerId(reviewerId);
                    entity.setReviewComment(comment);
                    entity.setUpdatedAt(LocalDateTime.now());
                    return requirementRepository.save(entity);
                });
    }

    public Mono<RequirementEntity> approve(Long id, String reviewerId, String comment) {
        return requirementRepository.findById(id)
                .flatMap(entity -> {
                    entity.setStatus("APPROVED");
                    entity.setReviewerId(reviewerId);
                    entity.setReviewComment(comment);
                    entity.setUpdatedAt(LocalDateTime.now());
                    return requirementRepository.save(entity);
                });
    }

    public Mono<RequirementEntity> reject(Long id, String reviewerId, String comment) {
        return requirementRepository.findById(id)
                .flatMap(entity -> {
                    entity.setStatus("REJECTED");
                    entity.setReviewerId(reviewerId);
                    entity.setReviewComment(comment);
                    entity.setUpdatedAt(LocalDateTime.now());
                    return requirementRepository.save(entity);
                });
    }

    public Mono<RequirementEntity> startImplementation(Long id) {
        return requirementRepository.findById(id)
                .flatMap(entity -> {
                    entity.setStatus("IMPLEMENTING");
                    entity.setUpdatedAt(LocalDateTime.now());
                    return requirementRepository.save(entity);
                });
    }

    public Mono<Void> delete(Long id) {
        return requirementRepository.deleteById(id);
    }

    private Mono<NotificationEntity> createNotification(RequirementEntity entity, String type) {
        NotificationEntity notification = NotificationEntity.builder()
                .type(type)
                .projectId(entity.getProjectId())
                .entityType("REQUIREMENT")
                .entityId(entity.getId())
                .title("需求已提交: " + entity.getTitle())
                .content("需求 [" + entity.getTitle() + "] 已提交，等待审核。")
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();
        return notificationRepository.save(notification);
    }
}
