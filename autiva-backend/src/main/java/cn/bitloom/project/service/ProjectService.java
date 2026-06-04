package cn.bitloom.project.service;

import cn.bitloom.project.entity.ProjectEntity;
import cn.bitloom.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    private static final Set<String> VALID_STATUSES = Set.of(
            "PLANNING", "IN_PROGRESS", "REVIEW", "COMPLETED", "ARCHIVED"
    );

    public Mono<ProjectEntity> create(ProjectEntity entity) {
        entity.setStatus(entity.getStatus() != null ? entity.getStatus() : "PLANNING");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return projectRepository.save(entity);
    }

    public Mono<ProjectEntity> findById(Long id) {
        return projectRepository.findById(id);
    }

    public Flux<ProjectEntity> findAll() {
        return projectRepository.findAll();
    }

    public Flux<ProjectEntity> findByOwnerId(String ownerId) {
        return projectRepository.findByOwnerId(ownerId);
    }

    public Mono<ProjectEntity> update(Long id, ProjectEntity updates) {
        return projectRepository.findById(id)
                .flatMap(existing -> {
                    if (updates.getName() != null) {
                        existing.setName(updates.getName());
                    }
                    if (updates.getDescription() != null) {
                        existing.setDescription(updates.getDescription());
                    }
                    if (updates.getStatus() != null) {
                        existing.setStatus(updates.getStatus());
                    }
                    if (updates.getOwnerId() != null) {
                        existing.setOwnerId(updates.getOwnerId());
                    }
                    existing.setUpdatedAt(LocalDateTime.now());
                    return projectRepository.save(existing);
                });
    }

    public Mono<ProjectEntity> transitionStatus(Long id, String newStatus) {
        if (!VALID_STATUSES.contains(newStatus)) {
            return Mono.error(new IllegalArgumentException("Invalid status: " + newStatus));
        }
        return projectRepository.findById(id)
                .flatMap(entity -> {
                    entity.setStatus(newStatus);
                    entity.setUpdatedAt(LocalDateTime.now());
                    return projectRepository.save(entity);
                });
    }

    public Mono<Void> delete(Long id) {
        return projectRepository.deleteById(id);
    }
}
