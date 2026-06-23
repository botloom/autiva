package cn.bitloom.vm;

import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.event.EventConverter;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.session.*;
import cn.bitloom.node.message.AssistantMessageCard;
import cn.bitloom.node.message.MessageCard;
import cn.bitloom.node.message.ToolMessageCard;
import cn.bitloom.node.message.UserMessageCard;
import cn.bitloom.store.Store;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class HomePageViewModel {

    private final FileSystemSessionManager fileSystemSessionManager;

    @Getter
    private final ObservableList<MessageCard> messages = FXCollections.observableArrayList();

    private Session session;
    private AssistantMessageCard currentAssistantCard = null;
    private Disposable outBoxSubscription;

    private void subscribeOutBox() {
        if (this.outBoxSubscription != null) {
            this.outBoxSubscription.dispose();
        }
        this.outBoxSubscription = EventBus.outBoxFlux()
                .doOnNext(event -> {
                    if (event instanceof MessageEvent messageEvent
                            && this.session != null
                            && this.session.getId().equals(messageEvent.getSessionId())) {
                        Platform.runLater(() -> this.processEvent(messageEvent));
                    }
                })
                .subscribe();
    }

    public void createNewSession() {
        this.session = null;
        Store.currentSessionId.set("");
        messages.clear();
        currentAssistantCard = null;
        Store.isStreaming.set(false);
        Store.isPaused.set(false);
        if (this.outBoxSubscription != null) {
            this.outBoxSubscription.dispose();
            this.outBoxSubscription = null;
        }
    }

    public void switchToSession(String sessionId) {
        if (this.session != null && sessionId.equals(this.session.getId())) {
            return;
        }

        Session targetSession = fileSystemSessionManager.getById(sessionId);
        if (targetSession == null) {
            log.warn("切换到不存在的session: {}", sessionId);
            return;
        }

        // 同步激活（activate 只加载最近 100 条消息，速度足够快）
        fileSystemSessionManager.activate(sessionId);

        this.session = targetSession;
        Store.currentSessionId.set(sessionId);
        Store.selectedModel.set(targetSession.getModel());
        Store.currentAgent.set(targetSession.getAgentId());

        subscribeOutBox();

        messages.clear();
        currentAssistantCard = null;
        Store.isStreaming.set(false);
        Store.isPaused.set(false);

        if (hasHistoricalMessages()) {
            prepareHistoricalMessages();
        }
    }

    public void switchAgent(String agentId) {
        Store.currentAgent.set(agentId);
        createNewSession();
    }

    public void prepareHistoricalMessages() {
        List<org.springframework.ai.chat.messages.Message> historicalMessages = this.session.getMessages();
        if (historicalMessages.isEmpty()) {
            return;
        }

        List<MessageEvent> events = EventConverter.fromMessages(this.session.getId(), historicalMessages);

        int batchSize = 20;
        for (int i = 0; i < events.size(); i += batchSize) {
            int end = Math.min(i + batchSize, events.size());
            List<MessageEvent> batch = events.subList(i, end);
            if (i == 0) {
                for (MessageEvent event : batch) {
                    processEvent(event);
                }
            } else {
                List<MessageEvent> batchCopy = new ArrayList<>(batch);
                Platform.runLater(() -> {
                    for (MessageEvent event : batchCopy) {
                        processEvent(event);
                    }
                });
            }
        }
    }

    public List<MessageCard> loadMoreMessages(int count) {
        if (this.session == null) {
            return List.of();
        }

        int currentBaseOffset = this.session.getMemoryBaseOffset();
        int memoryCursor = this.session.getMemoryCursor();

        // 没有更多历史消息（已到游标位置）
        if (currentBaseOffset <= memoryCursor) {
            return List.of();
        }

        int newOffset = Math.max(memoryCursor, currentBaseOffset - count);
        int toLoad = currentBaseOffset - newOffset;

        // 从磁盘按需加载
        List<org.springframework.ai.chat.messages.Message> olderMessages =
                fileSystemSessionManager.loadMessagesRange(this.session.getId(), newOffset, toLoad);

        if (olderMessages.isEmpty()) {
            return List.of();
        }

        // 更新内存状态
        this.session.setMemoryBaseOffset(newOffset);
        this.session.getMessages().addAll(0, olderMessages);

        return EventConverter.fromMessages(this.session.getId(), olderMessages).stream()
                .flatMap(e -> convertEventToCards(e).stream())
                .filter(Objects::nonNull)
                .toList();
    }

    public void prependHistoricalMessages(List<MessageCard> olderCards) {
        this.messages.addAll(0, olderCards);
    }

    public boolean hasMoreMessages() {
        return this.session != null
                && this.session.getMemoryBaseOffset() > this.session.getMemoryCursor();
    }

    public boolean hasHistoricalMessages() {
        return this.session != null && !this.session.getMessages().isEmpty();
    }

    // ===== 事件处理 =====

    private void processEvent(MessageEvent event) {
        if (event.isUserMessage()) {
            processUserEvent(event);
        } else if (event.isAssistantMessage()) {
            processAssistantEvent(event);
        } else if (event.isToolResponse()) {
            processToolEvent(event);
        } else {
            log.warn("未处理的事件类型: {}", event.getType());
        }
    }

    private void processUserEvent(MessageEvent e) {
        currentAssistantCard = null;
        messages.add(new UserMessageCard(e.getText()));
    }

    private void processAssistantEvent(MessageEvent e) {
        String finishReason = e.getFinishReason();
        String text = e.getText();

        if (finishReason == null || finishReason.isBlank()) {
            // 流式 chunk：直接累积
            if (Store.isPaused.get()) {
                return;
            }
            if (currentAssistantCard == null) {
                currentAssistantCard = new AssistantMessageCard();
                messages.add(currentAssistantCard);
            }
            currentAssistantCard.appendContent(text);
        } else if ("STOP".equals(finishReason)) {
            // 结束流式
            Store.isStreaming.set(false);
            Store.isPaused.set(false);
            fileSystemSessionManager.updateState(this.session.getId(), SessionState.IDLE);

            if (currentAssistantCard != null) {
                currentAssistantCard.complete("STOP");
                if (currentAssistantCard.isValid()) {
                    messages.remove(currentAssistantCard);
                }
                currentAssistantCard = null;
            } else if (text != null && !text.isBlank()) {
                // 非流式消息（历史消息或一次性输出）
                messages.add(new AssistantMessageCard(text, "STOP"));
            }
        } else if ("TOOL_CALLS".equals(finishReason)) {
            // 工具调用：结束当前流式消息
            if (currentAssistantCard != null) {
                currentAssistantCard.complete("TOOL_CALLS");
                if (currentAssistantCard.isValid()) {
                    messages.remove(currentAssistantCard);
                }
                currentAssistantCard = null;
            }

            // 创建工具调用卡片
            if (e.getToolCalls() != null) {
                for (MessageEvent.ToolCallInfo tc : e.getToolCalls()) {
                    messages.add(new ToolMessageCard(tc.name(), tc.arguments(), true));
                }
            }
        }
    }

    private void processToolEvent(MessageEvent e) {
        if (e.getResponses() != null && !e.getResponses().isEmpty()) {
            for (MessageEvent.ToolResponseInfo resp : e.getResponses()) {
                messages.add(new ToolMessageCard(resp.name(), resp.responseData(), false));
            }
        }
    }

    /**
     * 将 MessageEvent 转换为卡片列表（用于历史消息加载）
     */
    private List<MessageCard> convertEventToCards(MessageEvent event) {
        List<MessageCard> cards = new ArrayList<>();
        if (event.isUserMessage()) {
            cards.add(new UserMessageCard(event.getText()));
        } else if (event.isAssistantMessage()) {
            String finishReason = event.getFinishReason();
            String text = event.getText();
            if (text != null && !text.isBlank()) {
                cards.add(new AssistantMessageCard(text, finishReason));
            }
            // TOOL_CALLS 时也创建工具调用卡片
            if ("TOOL_CALLS".equals(finishReason) && event.getToolCalls() != null) {
                for (MessageEvent.ToolCallInfo tc : event.getToolCalls()) {
                    cards.add(new ToolMessageCard(tc.name(), tc.arguments(), true));
                }
            }
        } else if (event.isToolResponse()) {
            if (event.getResponses() != null) {
                for (MessageEvent.ToolResponseInfo resp : event.getResponses()) {
                    cards.add(new ToolMessageCard(resp.name(), resp.responseData(), false));
                }
            }
        }
        return cards;
    }

    public void addUserMessage(String text) {
        messages.add(new UserMessageCard(text));
    }

    public void sendMessage(String text) {
        if (this.session == null) {
            this.session = fileSystemSessionManager.create(
                    Store.currentAgent.get(), null, SessionTypeEnum.DM,
                    SessionRespTypeEnum.STREAM, Store.selectedModel.get());
            Store.currentSessionId.set(this.session.getId());
            subscribeOutBox();
        } else if (this.session.isStop()) {
            fileSystemSessionManager.activate(this.session.getId());
            subscribeOutBox();
        }
        Store.statusText.set("正在处理...");
        Store.isStreaming.set(true);
        Store.isPaused.set(false);
        fileSystemSessionManager.updateState(this.session.getId(), SessionState.GENERATING);
        EventBus.publishIn(MessageEvent.userMessage(this.session.getId(), text));
        // 触发侧边栏刷新（更新会话标题）
        Store.refreshHistory.set(!Store.refreshHistory.get());
    }

    public void clear() {
        messages.clear();
        currentAssistantCard = null;
        Store.isStreaming.set(false);
        Store.isPaused.set(false);
        if (this.session != null) {
            fileSystemSessionManager.updateState(this.session.getId(), SessionState.IDLE);
            fileSystemSessionManager.clear(this.session.getId());
        }
        Store.statusText.set("就绪");
    }

    public void pauseGeneration() {
        if (Store.isStreaming.get() && !Store.isPaused.get()) {
            Store.isPaused.set(true);
            if (this.session != null) {
                fileSystemSessionManager.stopSession(this.session.getId());
                fileSystemSessionManager.updateState(this.session.getId(), SessionState.PAUSED);
            }

            if (currentAssistantCard != null) {
                currentAssistantCard.setStreaming(false);
                currentAssistantCard = null;
            }

            Store.statusText.set("已暂停");
        }
    }

}
