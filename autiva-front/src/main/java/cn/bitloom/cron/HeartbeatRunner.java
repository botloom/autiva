package cn.bitloom.cron;

import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.event.EventType;
import cn.bitloom.agentic.session.MessageChannel;
import cn.bitloom.agentic.session.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatRunner {

    private static final String DEFAULT_HEARTBEAT_PROMPT =
            "读取 HEARTBEAT.md（如果存在）。严格遵循其中的指令。不要推断或重复之前对话中的旧任务。如果没有需要注意的事项，回复 HEARTBEAT_OK。";
    private static final String EVOLVER_SESSION_KEY = "__evolver__";
    private static final Duration DEFAULT_INTERVAL = Duration.ofMinutes(30);
    private static final Duration EVOLVER_INTERVAL = Duration.ofMinutes(30);

    private final TaskScheduler taskScheduler;
    private final SessionManager sessionManager;

    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastHeartbeatTime = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        List<cn.bitloom.agentic.session.Session> userSessions = sessionManager.getAllUserSessions();
        for (cn.bitloom.agentic.session.Session userSession : userSessions) {
            registerSession(userSession.getId());
        }
        registerEvolverSession();
        log.info("[HeartbeatRunner] 初始化完成，已注册 {} 个用户会话 + EVOLVER 的心跳", userSessions.size());
    }

    @PreDestroy
    public void destroy() {
        scheduledTasks.forEach((sessionId, future) -> future.cancel(false));
        scheduledTasks.clear();
        log.info("[HeartbeatRunner] 已停止所有心跳调度");
    }

    public void registerSession(String userSessionId) {
        if (scheduledTasks.containsKey(userSessionId)) {
            return;
        }

        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(
                () -> runOnce(userSessionId),
                Instant.now().plus(DEFAULT_INTERVAL),
                DEFAULT_INTERVAL
        );

        scheduledTasks.put(userSessionId, future);
        log.info("[HeartbeatRunner] 注册心跳: userSessionId={}, interval={}", userSessionId, DEFAULT_INTERVAL);
    }

    public void unregisterSession(String userSessionId) {
        ScheduledFuture<?> future = scheduledTasks.remove(userSessionId);
        if (future != null) {
            future.cancel(false);
            lastHeartbeatTime.remove(userSessionId);
            log.info("[HeartbeatRunner] 注销心跳: userSessionId={}", userSessionId);
        }
    }

    public void registerEvolverSession() {
        if (scheduledTasks.containsKey(EVOLVER_SESSION_KEY)) {
            return;
        }

        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(
                this::runOnceForEvolver,
                Instant.now().plus(EVOLVER_INTERVAL),
                EVOLVER_INTERVAL
        );

        scheduledTasks.put(EVOLVER_SESSION_KEY, future);
        log.info("[HeartbeatRunner] 注册 EVOLVER 心跳: interval={}", EVOLVER_INTERVAL);
    }

    public void runOnceForEvolver() {
        try {
            String evolverSessionId = "EVOLVER-SYSTEM-internal-internal";

            if (EventBus.isBusy(evolverSessionId)) {
                log.debug("[HeartbeatRunner] EVOLVER 忙碌，跳过心跳");
                return;
            }

            String heartbeatMessage = buildHeartbeatMessage();
            UserMessage userMessage = UserMessage.builder()
                    .text(heartbeatMessage)
                    .metadata(Map.of("heartbeat", true))
                    .build();

            EventBus.inBoxPublish(evolverSessionId, userMessage, EventType.EVOLVE, MessageChannel.EVOLVE);
            lastHeartbeatTime.put(EVOLVER_SESSION_KEY, Instant.now());

            log.debug("[HeartbeatRunner] EVOLVER 心跳已发送: sessionId={}", evolverSessionId);
        } catch (Exception e) {
            log.error("[HeartbeatRunner] EVOLVER 心跳执行失败", e);
        }
    }

    public void runOnce(String userSessionId) {
        try {
            if (EventBus.isBusy(userSessionId)) {
                log.debug("[HeartbeatRunner] 会话忙碌，跳过心跳: userSessionId={}", userSessionId);
                return;
            }

            String heartbeatMessage = buildHeartbeatMessage();
            UserMessage userMessage = UserMessage.builder()
                    .text(heartbeatMessage)
                    .metadata(Map.of("heartbeat", true))
                    .build();

            EventBus.inBoxPublish(userSessionId, userMessage, EventType.MESSAGE, MessageChannel.SYSTEM);
            lastHeartbeatTime.put(userSessionId, Instant.now());

            log.debug("[HeartbeatRunner] 心跳已发送: userSessionId={}", userSessionId);
        } catch (Exception e) {
            log.error("[HeartbeatRunner] 心跳执行失败: userSessionId={}", userSessionId, e);
        }
    }

    private String buildHeartbeatMessage() {
        return DEFAULT_HEARTBEAT_PROMPT;
    }

    public boolean isRegistered(String userSessionId) {
        return scheduledTasks.containsKey(userSessionId);
    }

    public Instant getLastHeartbeatTime(String userSessionId) {
        return lastHeartbeatTime.get(userSessionId);
    }

    public Map<String, Instant> getAllLastHeartbeatTimes() {
        return Map.copyOf(lastHeartbeatTime);
    }
}
