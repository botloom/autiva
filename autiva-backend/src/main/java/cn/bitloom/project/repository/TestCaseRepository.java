package cn.bitloom.project.repository;

import cn.bitloom.project.entity.TestCaseEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface TestCaseRepository extends R2dbcRepository<TestCaseEntity, Long> {

    Flux<TestCaseEntity> findByProjectId(Long projectId);
}
