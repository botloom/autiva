package cn.bitloom.project.repository;

import cn.bitloom.project.entity.DesignProposalEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface DesignProposalRepository extends R2dbcRepository<DesignProposalEntity, Long> {

    Flux<DesignProposalEntity> findByProjectId(Long projectId);
}
