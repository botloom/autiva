package cn.bitloom.bridge.desktop;

import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.event.MessageEvent;
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
}
