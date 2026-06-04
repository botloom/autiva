package cn.bitloom.project.service;

import cn.bitloom.project.entity.NotificationEntity;
import cn.bitloom.project.repository.NotificationRepository;
import cn.bitloom.websocket.ClientConnectionManager;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ClientConnectionManager clientConnectionManager;

    public Flux<NotificationEntity> findPending() {
        return notificationRepository.findByStatus("PENDING");
    }

    public Flux<NotificationEntity> findByTargetClient(String targetClientId, String status) {
        return notificationRepository.findByTargetClientIdAndStatus(targetClientId, status);
    }

    public Mono<NotificationEntity> send(Long id) {
        return notificationRepository.findById(id)
                .flatMap(notification -> {
                    String message = buildNotificationMessage(notification);
                    boolean sent = clientConnectionManager.sendToClient(
                            notification.getTargetClientId(), message);
                    if (sent) {
                        notification.setStatus("SENT");
                        notification.setSentAt(LocalDateTime.now());
                    } else {
                        log.warn("Failed to send notification {} to client {}, client not online",
                                id, notification.getTargetClientId());
                        notification.setStatus("SENT");
                        notification.setSentAt(LocalDateTime.now());
                    }
                    return notificationRepository.save(notification);
                });
    }

    public Mono<NotificationEntity> acknowledge(Long id) {
        return notificationRepository.findById(id)
                .flatMap(notification -> {
                    notification.setStatus("ACKNOWLEDGED");
                    return notificationRepository.save(notification);
                });
    }

    public Flux<NotificationEntity> sendAllPending() {
        return findPending()
                .flatMap(notification -> send(notification.getId()));
    }

    private String buildNotificationMessage(NotificationEntity notification) {
        JSONObject message = new JSONObject();
        message.put("type", "notification");
        message.put("notificationType", notification.getType());
        message.put("entityType", notification.getEntityType());
        message.put("entityId", notification.getEntityId());
        message.put("projectId", notification.getProjectId());
        message.put("title", notification.getTitle());
        message.put("content", notification.getContent());
        message.put("notificationId", notification.getId());
        return message.toJSONString();
    }
}
