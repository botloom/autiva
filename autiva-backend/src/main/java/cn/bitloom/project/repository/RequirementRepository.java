package cn.bitloom.project.repository;

import cn.bitloom.project.entity.RequirementEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface RequirementRepository extends R2dbcRepository<RequirementEntity, Long> {

    Flux<RequirementEntity> findByProjectId(Long projectId);

    Flux<RequirementEntity> findByProjectIdAndStatus(Long projectId, String status);
}
