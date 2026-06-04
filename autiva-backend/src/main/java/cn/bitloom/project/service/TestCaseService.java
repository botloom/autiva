package cn.bitloom.project.service;

import cn.bitloom.project.entity.TestCaseEntity;
import cn.bitloom.project.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseService {

    private final TestCaseRepository testCaseRepository;

    public Mono<TestCaseEntity> create(TestCaseEntity entity) {
        entity.setStatus(entity.getStatus() != null ? entity.getStatus() : "DRAFT");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return testCaseRepository.save(entity);
    }

    public Mono<TestCaseEntity> findById(Long id) {
        return testCaseRepository.findById(id);
    }

    public Flux<TestCaseEntity> findByProjectId(Long projectId) {
        return testCaseRepository.findByProjectId(projectId);
    }

    public Mono<TestCaseEntity> update(Long id, TestCaseEntity updates) {
        return testCaseRepository.findById(id)
                .flatMap(existing -> {
                    if (updates.getTitle() != null) {
                        existing.setTitle(updates.getTitle());
                    }
                    if (updates.getPreconditions() != null) {
                        existing.setPreconditions(updates.getPreconditions());
                    }
                    if (updates.getSteps() != null) {
                        existing.setSteps(updates.getSteps());
                    }
                    if (updates.getExpectedResult() != null) {
                        existing.setExpectedResult(updates.getExpectedResult());
                    }
                    if (updates.getRequirementId() != null) {
                        existing.setRequirementId(updates.getRequirementId());
                    }
                    existing.setUpdatedAt(LocalDateTime.now());
                    return testCaseRepository.save(existing);
                });
    }

    public Mono<TestCaseEntity> submit(Long id) {
        return testCaseRepository.findById(id)
                .flatMap(entity -> {
                    entity.setStatus("SUBMITTED");
                    entity.setUpdatedAt(LocalDateTime.now());
                    return testCaseRepository.save(entity);
                });
    }

    public Mono<TestCaseEntity> review(Long id, String reviewerId, String comment, boolean approved) {
        return testCaseRepository.findById(id)
                .flatMap(entity -> {
                    entity.setStatus(approved ? "APPROVED" : "REJECTED");
                    entity.setReviewerId(reviewerId);
                    entity.setReviewComment(comment);
                    entity.setUpdatedAt(LocalDateTime.now());
                    return testCaseRepository.save(entity);
                });
    }

    public Mono<Void> delete(Long id) {
        return testCaseRepository.deleteById(id);
    }
}
