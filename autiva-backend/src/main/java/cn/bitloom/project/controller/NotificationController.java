package cn.bitloom.project.controller;

import cn.bitloom.project.entity.NotificationEntity;
import cn.bitloom.project.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public Mono<ResponseEntity<Iterable<NotificationEntity>>> findByTargetClient(
            @RequestParam String targetClientId,
            @RequestParam(defaultValue = "PENDING") String status) {
        return notificationService.findByTargetClient(targetClientId, status)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{id}/acknowledge")
    public Mono<ResponseEntity<NotificationEntity>> acknowledge(@PathVariable Long id) {
        return notificationService.acknowledge(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
