package cn.bitloom.agentic.session;

import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.event.*;
import cn.bitloom.agentic.memory.MemoryManager;
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

/**
 * Session 编排器，负责主会话的消息循环和记忆事件处理。
 * <p>
 * 从 Session 类中提取的编排逻辑，使 Session 成为纯实体。
 * 每个 SessionRunner 绑定一个 Session 实例。
 */
@Slf4j
public class SessionRunner {

    private final Session session;
    private Disposable subscription;

    public SessionRunner(Session session) {
        this.session = session;
    }

    /**
     * 启动消息处理循环：订阅 inBox，按事件类型分类处理。
     * <p>
     * - MessageEvent.USER → 串行调用 Agent 执行对话
     * - MemoryEvent → 异步处理记忆事件（不阻塞对话流）
     */
    public void start() {
        this.subscription = EventBus.inBoxFlux()
                .filter(event -> event.getSessionId().equals(this.session.getId()))
                .concatMap(event -> switch (event) {
                    case MessageEvent messageEvent when messageEvent.isUserMessage() -> {
                        UserMessage userMessage = EventConverter.toUserMessage(messageEvent);
                        RuntimeContext ctx = new RuntimeContext(this.session);
                        ctx.param("sessionId", this.session.getId())
                           .param("model", this.session.getModel());
                        if (this.session.getRespType() == SessionRespTypeEnum.STREAM) {
                            yield this.session.getAgent().runStream(ctx, userMessage)
                                    .map(msg -> EventConverter.fromMessage(this.session.getId(), msg))
                                    .doOnNext(EventBus::publishOut)
                                    .doOnComplete(() -> this.session.setSessionState(SessionState.IDLE));
                        } else {
                            AssistantMessage assistantMessage = this.session.getAgent().runBlock(ctx, userMessage);
                            EventBus.publishOut(EventConverter.fromMessage(this.session.getId(), assistantMessage));
                            this.session.setSessionState(SessionState.IDLE);
                            yield Flux.<AbstractEvent>empty();
                        }
                    }
                    case MessageEvent ignored -> Flux.just(event);
                    case MemoryEvent memoryEvent ->
                        Mono.fromRunnable(() -> handleMemoryEvent(memoryEvent))
                                .subscribeOn(Schedulers.boundedElastic())
                                .then(Mono.<AbstractEvent>empty())
                                .flux();
                })
                .subscribe();
    }

    /**
     * 停止会话：发布 SESSION_END 事件触发异步记忆检查，然后取消订阅。
     */
    public void stop() {
        this.session.setSessionState(SessionState.STOPPED);
        EventBus.publishIn(MemoryEvent.sessionEnd(this.session.getId(), this.session.getAgentId()));
        if (this.subscription != null) {
            this.subscription.dispose();
            this.subscription = null;
        }
    }

    /**
     * 处理记忆事件（异步执行，不阻塞对话流）
     */
    private void handleMemoryEvent(MemoryEvent event) {
        try {
            MemoryManager memoryManager = this.session.getMemoryManager();
            if (memoryManager == null) {
                log.warn("[SessionRunner] MemoryManager 未注入，跳过记忆事件: {}", event.getType());
                return;
            }
            switch (event.getType()) {
                case CONTEXT_COMPACT -> handleContextCompact(memoryManager);
                case SESSION_END -> handleSessionEnd(memoryManager);
            }
        } catch (Exception e) {
            log.warn("[SessionRunner] 处理记忆事件失败: {}", event.getType(), e);
        }
    }

    /**
     * 异步上下文压缩
     */
    private void handleContextCompact(MemoryManager memoryManager) {
        List<Message> messages = new ArrayList<>(this.session.getMessages());
        String existingSummary = this.session.getSummary();
        String newSummary = memoryManager.compact(this.session.getAgentId(), this.session.getId(), messages, existingSummary);
        if (newSummary != null && !newSummary.isBlank()) {
            this.session.setSummary(newSummary);
        }
        this.session.setMemoryCursor(this.session.getMessages().size());
        this.session.setCurrentContextLength(0);
        log.info("[SessionRunner] 上下文压缩完成: sessionId={}, newCursor={}", this.session.getId(), this.session.getMemoryCursor());
    }

    /**
     * 会话结束异步检查遗漏记忆
     */
    private void handleSessionEnd(MemoryManager memoryManager) {
        List<Message> messages = new ArrayList<>(this.session.getMessages());
        memoryManager.consolidate(this.session.getAgentId(), messages);
    }
}
