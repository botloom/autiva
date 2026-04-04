package cn.bitloom.repository;

import cn.bitloom.entity.BaasResourceEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface BaasResourceRepository extends R2dbcRepository<BaasResourceEntity, Long> {

    Flux<BaasResourceEntity> findByServiceId(Long serviceId);

    Mono<BaasResourceEntity> findByServiceIdAndResourceType(Long serviceId, String resourceType);
}
