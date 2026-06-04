package cn.bitloom.project.repository;

import cn.bitloom.project.entity.NotificationEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface NotificationRepository extends R2dbcRepository<NotificationEntity, Long> {

    Flux<NotificationEntity> findByStatus(String status);

    Flux<NotificationEntity> findByTargetClientIdAndStatus(String targetClientId, String status);
}
