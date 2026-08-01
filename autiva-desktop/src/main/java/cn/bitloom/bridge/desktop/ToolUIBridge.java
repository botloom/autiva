package cn.bitloom.bridge.desktop;

import cn.bitloom.agentic.event.AbstractEvent;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.node.tool.QuestionCard;
import cn.bitloom.node.tool.TaskCard;
import cn.bitloom.node.tool.TodoCard;
import javafx.application.Platform;
import javafx.scene.Node;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
    private TodoCard currentTodoCard = null;

    @Setter
    private Consumer<Node> onNodeAdded;

    /**
     * 处理子智能体事件（供 TaskTool 直接调用）。
     * 若 sessionId 对应的 TaskCard 存在，将 MessageEvent 路由到卡片渲染。
     */
    public void processEvent(String sessionId, AbstractEvent event) {
        if (event instanceof MessageEvent messageEvent) {
            TaskCard card = sessionTaskCards.get(sessionId);
            if (card != null) {
                Platform.runLater(() -> card.processEvent(messageEvent));
            }
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
                TaskCard taskCard = sessionId != null ? this.sessionTaskCards.get(sessionId) : null;
                if (taskCard != null) {
                    // 子智能体场景：仍新建（TaskCard 内部管理）
                    TodoCard card = new TodoCard(todosJson);
                    taskCard.addTodoCard(card);
                } else {
                    // 主对话场景：复用 currentTodoCard，避免每次更新都新建卡片
                    if (currentTodoCard == null) {
                        currentTodoCard = new TodoCard(todosJson);
                        if (this.onNodeAdded != null) {
                            this.onNodeAdded.accept(currentTodoCard);
                        }
                    } else {
                        currentTodoCard.update(todosJson);
                    }
                }
            } catch (Exception e) {
                log.error("Error showing todos", e);
            }
        });
    }

    /**
     * 重置当前 TodoCard 引用（新会话时调用，使下次 showTodos 创建新卡片）
     */
    public void resetTodoCard() {
        this.currentTodoCard = null;
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
