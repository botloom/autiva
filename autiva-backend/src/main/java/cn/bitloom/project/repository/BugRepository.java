package cn.bitloom.project.repository;

import cn.bitloom.project.entity.BugEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface BugRepository extends R2dbcRepository<BugEntity, Long> {

    Flux<BugEntity> findByProjectId(Long projectId);

    Flux<BugEntity> findByProjectIdAndStatus(Long projectId, String status);

    Flux<BugEntity> findByAssigneeId(String assigneeId);
}
