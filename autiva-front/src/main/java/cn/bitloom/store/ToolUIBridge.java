package cn.bitloom.store;

import cn.bitloom.node.QuestionCard;
import cn.bitloom.node.TodoCard;
import javafx.application.Platform;
import javafx.scene.Node;
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
    private Consumer<Node> onNodeAdded;

    public void setOnNodeAdded(Consumer<Node> callback) {
        this.onNodeAdded = callback;
    }

    public void showQuestions(String questionsJson, CompletableFuture<String> answerFuture) {
        String questionId = UUID.randomUUID().toString();
        this.pendingQuestions.put(questionId, answerFuture);
        answerFuture.whenComplete((result, error) -> this.pendingQuestions.remove(questionId));

        Platform.runLater(() -> {
            try {
                QuestionCard card = new QuestionCard(questionsJson, questionId, this::onQuestionAnswered);
                if (this.onNodeAdded != null) {
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

    public void showTodos(String todosJson) {
        Platform.runLater(() -> {
            try {
                TodoCard card = new TodoCard(todosJson);
                if (this.onNodeAdded != null) {
                    this.onNodeAdded.accept(card);
                }
            } catch (Exception e) {
                log.error("Error showing todos", e);
            }
        });
    }
}
