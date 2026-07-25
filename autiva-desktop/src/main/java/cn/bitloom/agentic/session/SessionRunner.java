package cn.bitloom.agentic.session;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.event.*;
import cn.bitloom.agentic.memory.MemoryManager;
import cn.bitloom.agentic.memory.TurnBufferedChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Session 编排器，负责主会话的消息循环和记忆事件处理。
 * <p>
 * 对齐 netInsight 的 SessionRunner 设计，使用 TurnBufferedChatMemory 管理本轮缓冲与批量持久化。
 * 每个 SessionRunner 绑定一个 Session 实例和一个 per-session chatMemory 实例。
 */
@Slf4j
public class SessionRunner {

    private final Session session;
    private final Agent agent;
    private final MemoryManager memoryManager;
    private final TurnBufferedChatMemory chatMemory;
    private final ISessionManager sessionManager;
    private Disposable subscription;

    /**
     * 子智能体同步等待结果用（可选）。
     * 非 null 时，本轮对话完成后完成 future，供 TaskTool 阻塞等待。
     */
    private CompletableFuture<String> resultFuture;

    /**
     * 构造 SessionRunner（主会话用，无 resultFuture）。
     *
     * @param session        绑定的 Session
     * @param agent          Agent 实例（per-session 构建）
     * @param memoryManager  记忆管理器
     * @param chatMemory     per-session TurnBufferedChatMemory（本轮缓冲 + flush 批量持久化）
     * @param sessionManager 会话管理器（用于 persistSession）
     */
    public SessionRunner(Session session, Agent agent, MemoryManager memoryManager,
                         TurnBufferedChatMemory chatMemory, ISessionManager sessionManager) {
        this.session = session;
        this.agent = agent;
        this.memoryManager = memoryManager;
        this.chatMemory = chatMemory;
        this.sessionManager = sessionManager;
    }

    /**
     * 设置 resultFuture（子智能体场景用，供 TaskTool 阻塞等待结果）。
     */
    public void setResultFuture(CompletableFuture<String> resultFuture) {
        this.resultFuture = resultFuture;
    }

    /**
     * 获取 resultFuture（子智能体场景用）。
     */
    public CompletableFuture<String> getResultFuture() {
        if (resultFuture == null) {
            resultFuture = new CompletableFuture<>();
        }
        return resultFuture;
    }

    /**
     * 启动消息处理循环：订阅 inBox，按事件类型分类处理。
     * <p>
     * - MessageEvent.USER → 串行调用 Agent 执行对话，本轮结束后 flush 批量持久化
     * - MemoryEvent → 异步处理记忆事件（不阻塞对话流）
     * - A2UIActionEvent → 构造用户消息让 Agent 继续处理，本轮结束后 flush
     */
    public void start() {
        log.info("[SessionRunner]-[start],sessionId={}", session.getId());
        this.subscription = EventBus.inBoxFlux()
                .filter(event -> event.getSessionId().equals(this.session.getId()))
                .publishOn(Schedulers.boundedElastic())
                .concatMap(event -> switch (event) {
                    case MessageEvent messageEvent when messageEvent.isUserMessage() -> {
                        // 记录当前轮次 messageId，供 flush 时填充 event.messageId
                        this.chatMemory.setCurrentMessageId(messageEvent.getMessageId());
                        UserMessage userMessage = EventConverter.toUserMessage(messageEvent);
                        RuntimeContext ctx = new RuntimeContext(this.session);
                        ctx.param("sessionId", this.session.getId())
                           .param("model", this.session.getModel())
                           .param("lastUserMessage", messageEvent.getText());
                        StringBuilder resultBuilder = new StringBuilder();
                        if (this.session.getRespType() == SessionRespTypeEnum.STREAM) {
                            yield this.agent.runStream(ctx, userMessage)
                                    .map(msg -> {
                                        if (msg.getText() != null) {
                                            resultBuilder.append(msg.getText());
                                        }
                                        return EventConverter.fromMessage(this.session.getId(), msg);
                                    })
                                    .doOnNext(EventBus::publishOut)
                                    .doOnComplete(() -> {
                                        safeFlush();
                                        if (resultFuture != null) {
                                            resultFuture.complete(resultBuilder.toString());
                                        }
                                    })
                                    .onErrorResume(e -> {
                                        safeFlush();
                                        if (resultFuture != null) {
                                            resultFuture.completeExceptionally(e);
                                        }
                                        return Flux.empty();
                                    });
                        } else {
                            AssistantMessage assistantMessage = this.agent.runBlock(ctx, userMessage);
                            safeFlush();
                            EventBus.publishOut(EventConverter.fromMessage(this.session.getId(), assistantMessage));
                            if (resultFuture != null) {
                                resultFuture.complete(assistantMessage.getText());
                            }
                            yield Flux.<AbstractEvent>empty();
                        }
                    }
                    case MessageEvent ignored -> Flux.just(event);
                    case MemoryEvent memoryEvent ->
                        Mono.fromRunnable(() -> handleMemoryEvent(memoryEvent))
                                .subscribeOn(Schedulers.boundedElastic())
                                .then(Mono.<AbstractEvent>empty())
                                .flux();
                    case A2UIActionEvent actionEvent -> {
                        // A2UI 用户交互回流：构造用户消息让 Agent 继续处理
                        String actionText = "[A2UI 用户交互] surface=" + actionEvent.getSurfaceId()
                                + " action=" + actionEvent.getActionName()
                                + " source=" + actionEvent.getSourceComponentId()
                                + " data=" + actionEvent.getContext();
                        UserMessage userMessage = new UserMessage(actionText);
                        RuntimeContext ctx = new RuntimeContext(this.session);
                        ctx.param("sessionId", this.session.getId())
                           .param("model", this.session.getModel())
                           .param("lastUserMessage", actionText);
                        yield this.agent.runStream(ctx, userMessage)
                                .map(msg -> EventConverter.fromMessage(this.session.getId(), msg))
                                .doOnNext(EventBus::publishOut)
                                .doOnComplete(this::safeFlush)
                                .onErrorResume(e -> {
                                    safeFlush();
                                    return Flux.empty();
                                });
                    }
                    default -> Flux.<AbstractEvent>empty();
                })
                .subscribe();
    }

    /**
     * 安全调用 flush（异常路径也调用，避免丢失本轮消息）。
     */
    private void safeFlush() {
        try {
            chatMemory.flush();
        } catch (Exception ex) {
            log.error("[SessionRunner] flush 失败: sessionId={}", session.getId(), ex);
        }
    }

    /**
     * 停止会话：取消订阅。
     */
    public void stop() {
        if (this.subscription != null) {
            this.subscription.dispose();
            this.subscription = null;
        }
    }

    /**
     * 判断 SessionRunner 是否已停止（subscription 为空或已 dispose）。
     */
    public boolean isStopped() {
        return this.subscription == null || this.subscription.isDisposed();
    }

    /**
     * 处理记忆事件（异步执行，不阻塞对话流）。
     * 只压缩文件历史消息（不含本轮缓冲区），压缩后推进游标。
     */
    private void handleMemoryEvent(MemoryEvent event) {
        try {
            if (this.memoryManager == null) {
                log.warn("[SessionRunner] MemoryManager 未注入，跳过记忆事件: {}", event.getType());
                return;
            }
            if (event.getType() == MemoryEvent.Type.CONTEXT_COMPACT) {
                handleContextCompact(this.memoryManager);
            }
        } catch (Exception e) {
            log.warn("[SessionRunner] 处理记忆事件失败: {}", event.getType(), e);
        }
    }

    /**
     * 异步上下文压缩。
     * 只压缩文件历史消息（不含本轮缓冲区），压缩后推进游标到文件消息总数。
     */
    private void handleContextCompact(MemoryManager memoryManager) {
        List<Message> messages = chatMemory.getHistoryFromFile();
        String existingSummary = this.session.getSummary();
        String newSummary = memoryManager.compact(this.session.getAgentId(), this.session.getId(),
                messages, existingSummary);
        if (newSummary != null && !newSummary.isBlank()) {
            this.session.setSummary(newSummary);
        }

        // 推进游标到文件消息总数（所有已入库消息都已压缩）
        int newCursor = chatMemory.countFileMessages();
        this.session.setMemoryCursor(newCursor);
        this.session.setCurrentContextLength(0);

        this.sessionManager.persistSession(this.session);
        log.info("[SessionRunner] 上下文压缩完成: sessionId={}, newCursor={}", this.session.getId(), newCursor);
    }
}
