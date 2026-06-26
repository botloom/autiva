package cn.bitloom.bridge.desktop;

import cn.bitloom.agentic.a2ui.A2UIMessage;
import cn.bitloom.agentic.event.A2UIActionEvent;
import cn.bitloom.agentic.event.A2UIEvent;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.node.a2ui.A2UICard;
import cn.bitloom.node.tool.QuestionCard;
import cn.bitloom.node.tool.TaskCard;
import cn.bitloom.node.tool.TodoCard;
import javafx.application.Platform;
import javafx.scene.Node;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
@Component
public class ToolUIBridge {

    private final Map<String, CompletableFuture<String>> pendingQuestions = new ConcurrentHashMap<>();
    private final Map<String, TaskCard> activeTaskCards = new ConcurrentHashMap<>();
    private final Map<String, TaskCard> sessionTaskCards = new ConcurrentHashMap<>();
    private final Map<String, A2UICard> activeSurfaces = new ConcurrentHashMap<>();
    private Disposable outBoxSubscription;

    @Setter
    private Consumer<Node> onNodeAdded;

    @PostConstruct
    public void init() {
        subscribeOutBox();
    }

    private void subscribeOutBox() {
        this.outBoxSubscription = EventBus.outBoxFlux()
                .doOnNext(event -> {
                    if (event instanceof MessageEvent messageEvent) {
                        TaskCard card = sessionTaskCards.get(messageEvent.getSessionId());
                        if (card != null) {
                            Platform.runLater(() -> card.processEvent(messageEvent));
                        }
                    } else if (event instanceof A2UIEvent a2uiEvent) {
                        A2UICard card = activeSurfaces.get(a2uiEvent.getMessage().surfaceId());
                        if (card != null) {
                            Platform.runLater(() -> card.handleMessage(a2uiEvent.getMessage()));
                        }
                    }
                })
                .subscribe();
    }

    @PreDestroy
    public void destroy() {
        if (outBoxSubscription != null && !outBoxSubscription.isDisposed()) {
            outBoxSubscription.dispose();
        }
    }

    public void showQuestions(String questionsJson, CompletableFuture<String> answerFuture, String sessionId) {
        String questionId = UUID.randomUUID().toString();
        this.pendingQuestions.put(questionId, answerFuture);
        answerFuture.whenComplete((result, error) -> this.pendingQuestions.remove(questionId));

        Platform.runLater(() -> {
            try {
                QuestionCard card = new QuestionCard(questionsJson, questionId, this::onQuestionAnswered);
                TaskCard taskCard = sessionId != null ? this.sessionTaskCards.get(sessionId) : null;
                if (taskCard != null) {
                    taskCard.addQuestionCard(card);
                } else if (this.onNodeAdded != null) {
                    this.onNodeAdded.accept(card);
                }
            } catch (Exception e) {
                log.error("Error showing questions", e);
                answerFuture.completeExceptionally(e);
            }
        });
    }

    public void onQuestionAnswered(String questionId, String answersJson) {
        CompletableFuture<String> future = this.pendingQuestions.remove(questionId);
        if (future != null) {
            future.complete(answersJson);
        } else {
            log.warn("No pending question found for id: {}", questionId);
        }
    }

    public void showTodos(String todosJson, String sessionId) {
        Platform.runLater(() -> {
            try {
                TodoCard card = new TodoCard(todosJson);
                TaskCard taskCard = sessionId != null ? this.sessionTaskCards.get(sessionId) : null;
                if (taskCard != null) {
                    taskCard.addTodoCard(card);
                } else if (this.onNodeAdded != null) {
                    this.onNodeAdded.accept(card);
                }
            } catch (Exception e) {
                log.error("Error showing todos", e);
            }
        });
    }

    public void createTaskCard(String taskId, String taskJson) {
        TaskCard card = new TaskCard(taskJson);
        this.activeTaskCards.put(taskId, card);
        this.sessionTaskCards.put(taskId, card);
        Platform.runLater(() -> {
            try {
                if (this.onNodeAdded != null) {
                    this.onNodeAdded.accept(card);
                }
            } catch (Exception e) {
                log.error("Error showing task card", e);
            }
        });
    }

    public void completeTaskCard(String taskId, String result) {
        TaskCard card = this.activeTaskCards.remove(taskId);
        this.sessionTaskCards.remove(taskId);
        if (card != null) {
            card.complete(result);
        }
    }

    public void failTaskCard(String taskId, String error) {
        TaskCard card = this.activeTaskCards.remove(taskId);
        this.sessionTaskCards.remove(taskId);
        if (card != null) {
            card.complete("\n错误: " + error);
            card.setStatus("failed");
            card.dispose();
        }
    }

    // ===== A2UI 相关方法 =====

    /**
     * 处理 A2UI 消息(实现 A2UITool.A2UIHandler 接口)。
     * <p>
     * 在 UI 线程创建/更新/删除 A2UI 卡片,返回 CompletableFuture 供工具线程等待。
     */
    public CompletableFuture<String> handleA2UIMessage(A2UIMessage message, String sessionId) {
        CompletableFuture<String> future = new CompletableFuture<>();

        Platform.runLater(() -> {
            try {
                switch (message) {
                    case A2UIMessage.CreateSurface cs -> {
                        A2UICard card = new A2UICard(cs.surfaceId());
                        card.setOnUserAction((actionName, context) ->
                                onA2UIAction(cs.surfaceId(), null, actionName, context, sessionId));
                        activeSurfaces.put(cs.surfaceId(), card);
                        if (onNodeAdded != null) {
                            onNodeAdded.accept(card);
                        }
                        future.complete("Surface created: " + cs.surfaceId());
                    }
                    case A2UIMessage.UpdateComponents uc -> {
                        A2UICard card = activeSurfaces.get(uc.surfaceId());
                        if (card != null) {
                            card.handleMessage(uc);
                            future.complete("Components updated: " + uc.components().size());
                        } else {
                            future.complete("Surface not found: " + uc.surfaceId());
                        }
                    }
                    case A2UIMessage.UpdateDataModel udm -> {
                        A2UICard card = activeSurfaces.get(udm.surfaceId());
                        if (card != null) {
                            card.handleMessage(udm);
                            future.complete("Data model updated");
                        } else {
                            future.complete("Surface not found: " + udm.surfaceId());
                        }
                    }
                    case A2UIMessage.DeleteSurface ds -> {
                        A2UICard card = activeSurfaces.remove(ds.surfaceId());
                        if (card != null) {
                            card.handleMessage(ds);
                            future.complete("Surface deleted: " + ds.surfaceId());
                        } else {
                            future.complete("Surface not found: " + ds.surfaceId());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error handling A2UI message", e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * 处理 A2UI 用户交互回流。
     * <p>
     * 通过 EventBus 发送 A2UIActionEvent 到 Agent。
     */
    public void onA2UIAction(String surfaceId, String componentId,
                             String actionName, Map<String, Object> context, String sessionId) {
        EventBus.publishIn(A2UIActionEvent.of(sessionId, surfaceId, componentId, actionName, context));
    }
}
