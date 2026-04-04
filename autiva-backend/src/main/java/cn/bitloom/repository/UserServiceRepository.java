package cn.bitloom.repository;

import cn.bitloom.entity.UserServiceEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Repository
public interface UserServiceRepository extends R2dbcRepository<UserServiceEntity, Long> {

    Flux<UserServiceEntity> findByClientId(String clientId);

    Mono<UserServiceEntity> findBySubdomain(String subdomain);

    Mono<Boolean> existsBySubdomain(String subdomain);

    Flux<UserServiceEntity> findByStatus(String status);
}
