package cn.bitloom.agentic.session;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.event.AbstractEvent;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.event.EventConverter;
import cn.bitloom.agentic.event.MemoryEvent;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.memory.MemoryManager;
import cn.bitloom.agentic.model.ModelTypeEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 会话实体类，消息处理的核心编排者，也是唯一的状态源。
 * <p>
 * 所有持久化字段直接在本类中定义，序列化为 metadata.json。
 * 瞬态字段（box、agent、messages、interruptControl）不参与序列化。
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    // ===== 标识 =====

    private String id;
    private String agentId;
    private String userId;

    // ===== 会话分类 =====

    @Getter
    private SessionTypeEnum sessionType;

    @Getter
    private SessionRespTypeEnum respType;

    @Getter
    private String source;

    @Getter
    private String parentId;

    // ===== 会话元数据 =====

    private ModelTypeEnum model;

    @Builder.Default
    private String title = "新对话";

    @Builder.Default
    private Long createdAt = System.currentTimeMillis();

    @Builder.Default
    private Long updateAt = System.currentTimeMillis();

    @Builder.Default
    private SessionState sessionState = SessionState.IDLE;

    // ===== 对话上下文 =====

    @Builder.Default
    private int messageCount = 0;

    @Builder.Default
    private int memoryCursor = 0;

    private String summary;

    // ===== 压缩上下文 =====

    @Builder.Default
    private int contextCapacity = 64000;

    @Builder.Default
    private double compactionThreshold = 0.8;

    @Builder.Default
    private int currentContextLength = 0;

    // ===== 工具上下文 =====

    @Builder.Default
    private Set<String> activatedToolGroups = Set.of();

    private String permissionMode;

    // ===== 任务上下文 =====

    @Builder.Default
    private List<TaskItem> tasks = List.of();

    // ===== 计划模式上下文 =====

    @Builder.Default
    private boolean planModeActive = false;

    private String planFilePath;

    // ===== 元数据 =====

    private Long savedAt;

    @Builder.Default
    private boolean shutdownInterrupted = false;

    // ===== 瞬态字段（不序列化） =====

    @Getter
    @Setter
    @JsonIgnore
    private Agent agent;

    @Getter
    @Setter
    @JsonIgnore
    @Builder.Default
    private List<Message> messages = new ArrayList<>();

    @Setter
    @JsonIgnore
    private Disposable subscription;

    @Getter
    @Setter
    @JsonIgnore
    private MemoryManager memoryManager;

    /**
     * 启动消息处理循环：订阅 inBox，按事件类型分类处理。
     * <p>
     * - MessageEvent.USER → 串行调用 Agent 执行对话
     * - MemoryEvent → 异步处理记忆事件（不阻塞对话流）
     */
    public void start() {
        this.subscription = EventBus.inBoxFlux()
                .filter(event -> event.getSessionId().equals(this.id))
                .concatMap(event -> switch (event) {
                    case MessageEvent messageEvent when messageEvent.isUserMessage() -> {
                        UserMessage userMessage = EventConverter.toUserMessage(messageEvent);
                        // 主智能体：构造包含 Session 的 RuntimeContext，填充 toolContext 参数
                        RuntimeContext ctx = new RuntimeContext(this);
                        ctx.param("sessionId", this.getId())
                           .param("model", this.getModel());
                        if (this.getRespType() == SessionRespTypeEnum.STREAM) {
                            yield this.agent.runStream(ctx, userMessage)
                                    .map(msg -> EventConverter.fromMessage(this.getId(), msg))
                                    .doOnNext(EventBus::publishOut)
                                    .doOnComplete(() -> this.setSessionState(SessionState.IDLE));
                        } else {
                            AssistantMessage assistantMessage = this.agent.runBlock(ctx, userMessage);
                            EventBus.publishOut(EventConverter.fromMessage(this.getId(), assistantMessage));
                            this.setSessionState(SessionState.IDLE);
                            yield Flux.<AbstractEvent>empty();
                        }
                    }
                    case MessageEvent ignored -> Flux.just(event); // 非用户消息，原样返回
                    case MemoryEvent memoryEvent ->
                        // 异步处理记忆事件（不阻塞对话流）
                        Mono.fromRunnable(() -> handleMemoryEvent(memoryEvent))
                                .subscribeOn(Schedulers.boundedElastic())
                                .then(Mono.<AbstractEvent>empty())
                                .flux();
                })
                .subscribe();
    }

    /**
     * 处理记忆事件（异步执行，不阻塞对话流）
     */
    private void handleMemoryEvent(MemoryEvent event) {
        try {
            if (this.memoryManager == null) {
                log.warn("[Session] MemoryManager 未注入，跳过记忆事件: {}", event.getType());
                return;
            }
            switch (event.getType()) {
                case CONTEXT_COMPACT -> handleContextCompact(event);
                case SESSION_END -> handleSessionEnd(event);
            }
        } catch (Exception e) {
            log.warn("[Session] 处理记忆事件失败: {}", event.getType(), e);
        }
    }

    /**
     * 异步上下文压缩
     */
    private void handleContextCompact(MemoryEvent event) {
        List<Message> messages = new ArrayList<>(this.messages);
        String existingSummary = this.summary;
        String newSummary = this.memoryManager.compact(this.agentId, this.id, messages, existingSummary);
        if (newSummary != null && !newSummary.isBlank()) {
            this.summary = newSummary;
        }
        // 推进游标，重置 token 计数
        this.memoryCursor = this.messages.size();
        this.currentContextLength = 0;
        log.info("[Session] 上下文压缩完成: sessionId={}, newCursor={}", this.id, this.memoryCursor);
    }

    /**
     * 会话结束异步检查遗漏记忆
     */
    private void handleSessionEnd(MemoryEvent event) {
        List<Message> messages = new ArrayList<>(this.messages);
        this.memoryManager.consolidate(this.agentId, messages);
    }

    public void publish(AbstractEvent message) {
        EventBus.publishIn(message);
    }

    public Flux<AbstractEvent> subscribe() {
        return EventBus.outBoxFlux();
    }

    /**
     * 停止会话：发布 SESSION_END 事件触发异步记忆检查，然后取消订阅。
     * <p>
     * 注意：暂停场景不应调用此方法，应直接 dispose subscription。
     */
    public void stop() {
        this.setSessionState(SessionState.STOPPED);
        // 发布会话结束事件到 inBox，触发异步记忆检查
        EventBus.publishIn(MemoryEvent.sessionEnd(this.id, this.agentId));
        if (this.subscription != null) {
            this.subscription.dispose();
            this.subscription = null;
        }
    }

    public void pause() {
    }

    public Boolean isStop() {
        return this.getSessionState() == SessionState.STOPPED;
    }

    /**
     * 任务条目
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskItem {
        private String id;
        private String content;
        /**
         * pending / in_progress / completed
         */
        private String status;
        /**
         * high / medium / low
         */
        private String priority;
    }
}
