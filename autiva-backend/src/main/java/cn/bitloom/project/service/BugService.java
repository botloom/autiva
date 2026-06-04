package cn.bitloom.project.service;

import cn.bitloom.project.entity.BugEntity;
import cn.bitloom.project.entity.NotificationEntity;
import cn.bitloom.project.repository.BugRepository;
import cn.bitloom.project.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class BugService {

    private final BugRepository bugRepository;
    private final NotificationRepository notificationRepository;

    public Mono<BugEntity> create(BugEntity entity) {
        entity.setStatus(entity.getStatus() != null ? entity.getStatus() : "OPEN");
        entity.setSeverity(entity.getSeverity() != null ? entity.getSeverity() : "MINOR");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return bugRepository.save(entity)
                .flatMap(saved -> {
                    if ("OPEN".equals(saved.getStatus())) {
                        return createNotification(saved, "BUG_SUBMITTED")
                                .thenReturn(saved);
                    }
                    return Mono.just(saved);
                });
    }

    public Mono<BugEntity> findById(Long id) {
        return bugRepository.findById(id);
    }

    public Flux<BugEntity> findByProjectId(Long projectId) {
        return bugRepository.findByProjectId(projectId);
    }

    public Flux<BugEntity> findByProjectIdAndStatus(Long projectId, String status) {
        return bugRepository.findByProjectIdAndStatus(projectId, status);
    }

    public Flux<BugEntity> findByAssigneeId(String assigneeId) {
        return bugRepository.findByAssigneeId(assigneeId);
    }

    public Mono<BugEntity> update(Long id, BugEntity updates) {
        return bugRepository.findById(id)
                .flatMap(existing -> {
                    if (updates.getTitle() != null) {
                        existing.setTitle(updates.getTitle());
                    }
                    if (updates.getDescription() != null) {
                        existing.setDescription(updates.getDescription());
                    }
                    if (updates.getSeverity() != null) {
                        existing.setSeverity(updates.getSeverity());
                    }
                    existing.setUpdatedAt(LocalDateTime.now());
                    return bugRepository.save(existing);
                });
    }

    public Mono<BugEntity> assign(Long id, String assigneeId) {
        return bugRepository.findById(id)
                .flatMap(entity -> {
                    entity.setStatus("ASSIGNED");
                    entity.setAssigneeId(assigneeId);
                    entity.setUpdatedAt(LocalDateTime.now());
                    return bugRepository.save(entity);
                });
    }

    public Mono<BugEntity> fix(Long id, String fixDescription) {
        return bugRepository.findById(id)
                .flatMap(entity -> {
                    entity.setStatus("FIXED");
                    entity.setFixDescription(fixDescription);
                    entity.setUpdatedAt(LocalDateTime.now());
                    return bugRepository.save(entity);
                });
    }

    public Mono<BugEntity> verify(Long id) {
        return bugRepository.findById(id)
                .flatMap(entity -> {
                    entity.setStatus("VERIFIED");
                    entity.setUpdatedAt(LocalDateTime.now());
                    return bugRepository.save(entity);
                });
    }

    public Mono<BugEntity> close(Long id) {
        return bugRepository.findById(id)
                .flatMap(entity -> {
                    entity.setStatus("CLOSED");
                    entity.setUpdatedAt(LocalDateTime.now());
                    return bugRepository.save(entity);
                });
    }

    public Mono<BugEntity> reopen(Long id) {
        return bugRepository.findById(id)
                .flatMap(entity -> {
                    entity.setStatus("REOPENED");
                    entity.setUpdatedAt(LocalDateTime.now());
                    return bugRepository.save(entity);
                });
    }

    public Mono<Void> delete(Long id) {
        return bugRepository.deleteById(id);
    }

    private Mono<NotificationEntity> createNotification(BugEntity entity, String type) {
        NotificationEntity notification = NotificationEntity.builder()
                .type(type)
                .projectId(entity.getProjectId())
                .entityType("BUG")
                .entityId(entity.getId())
                .title("Bug已提交: " + entity.getTitle())
                .content("Bug [" + entity.getTitle() + "] 已提交，严重程度: " + entity.getSeverity())
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();
        return notificationRepository.save(notification);
    }
}
