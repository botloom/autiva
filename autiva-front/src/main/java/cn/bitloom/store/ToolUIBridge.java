package cn.bitloom.store;

import javafx.application.Platform;
import javafx.scene.web.WebEngine;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ToolUIBridge {

    @Setter
    private volatile WebEngine webEngine;
    private final Map<String, CompletableFuture<String>> pendingQuestions = new ConcurrentHashMap<>();

    public void showQuestions(String questionsJson, CompletableFuture<String> answerFuture) {
        String questionId = String.valueOf(System.currentTimeMillis());
        this.pendingQuestions.put(questionId, answerFuture);
        answerFuture.whenComplete((result, error) -> this.pendingQuestions.remove(questionId));

        Platform.runLater(() -> {
            try {
                if (this.webEngine != null) {
                    String script = String.format(
                            "window.chat.showQuestions('%s', '%s');",
                            escapeJs(questionsJson),
                            questionId
                    );
                    this.webEngine.executeScript(script);
                }
            } catch (Exception e) {
                log.error("Error showing questions in WebView", e);
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
                if (this.webEngine != null) {
                    String script = String.format(
                            "window.chat.showTodos('%s');",
                            escapeJs(todosJson)
                    );
                    this.webEngine.executeScript(script);
                }
            } catch (Exception e) {
                log.error("Error showing todos in WebView", e);
            }
        });
    }

    private String escapeJs(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
