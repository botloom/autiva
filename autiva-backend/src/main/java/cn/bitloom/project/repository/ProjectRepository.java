package cn.bitloom.project.repository;

import cn.bitloom.project.entity.ProjectEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ProjectRepository extends R2dbcRepository<ProjectEntity, Long> {

    Mono<ProjectEntity> findByName(String name);

    Flux<ProjectEntity> findByOwnerId(String ownerId);
}
