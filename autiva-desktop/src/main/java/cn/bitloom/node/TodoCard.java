package cn.bitloom.node;

import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.util.ArrayList;
import java.util.List;

public class TodoCard extends VBox {

    public TodoCard(String todosJson) {
        this.getStyleClass().add("chat-message");
        this.getStyleClass().add("chat-message--tool");
        this.getStyleClass().add("chat-message--todo");

        HBox header = new HBox(8);
        header.getStyleClass().add("chat-message__tool-header");

        Label iconLabel = new Label("☑");
        iconLabel.getStyleClass().add("chat-message__todo-icon");
        Label nameLabel = new Label("TodoWrite");
        nameLabel.getStyleClass().add("chat-message__tool-name");
        nameLabel.setStyle("-fx-text-fill: #b45309;");
        header.getChildren().addAll(iconLabel, nameLabel);
        this.getChildren().add(header);

        VBox body = new VBox(8);
        body.getStyleClass().add("chat-message__todo-body");

        List<JsonNode> todoItems = parseTodos(todosJson);
        int completedCount = 0;
        int totalCount = todoItems.size();

        for (JsonNode item : todoItems) {
            String content = getString(item, "content");
            String status = getString(item, "status");
            String activeForm = getString(item, "activeForm");

            HBox itemRow = new HBox(10);
            itemRow.getStyleClass().add("chat-message__todo-item");

            Circle statusIndicator = new Circle(9);
            statusIndicator.getStyleClass().add("chat-message__todo-status");
            statusIndicator.getStyleClass().add("chat-message__todo-status--" + status);
            itemRow.getChildren().add(statusIndicator);

            VBox contentArea = new VBox(2);
            Label contentLabel = new Label(content);
            contentLabel.setWrapText(true);
            contentLabel.getStyleClass().add("chat-message__todo-text");
            if ("completed".equals(status)) {
                contentLabel.getStyleClass().add("chat-message__todo-text--completed");
            }
            contentArea.getChildren().add(contentLabel);

            if (activeForm != null && !"completed".equals(status)) {
                Label activeFormLabel = new Label(activeForm);
                activeFormLabel.getStyleClass().add("chat-message__todo-active-form");
                contentArea.getChildren().add(activeFormLabel);
            }
            itemRow.getChildren().add(contentArea);

            Label statusLabel = new Label(getStatusText(status));
            statusLabel.getStyleClass().add("chat-message__todo-status-label");
            statusLabel.getStyleClass().add("chat-message__todo-status-label--" + status);
            itemRow.getChildren().add(statusLabel);

            body.getChildren().add(itemRow);

            if ("completed".equals(status)) {
                completedCount++;
            }
        }

        if (totalCount > 0) {
            HBox progressBar = new HBox();
            progressBar.getStyleClass().add("chat-message__todo-progress-bar");
            double progress = (double) completedCount / totalCount;

            Region progressFill = new Region();
            progressFill.getStyleClass().add("chat-message__todo-progress-fill");
            progressFill.setPrefWidth(progress * 100);
            progressFill.setMaxWidth(progress * 100);
            progressBar.getChildren().add(progressFill);
            body.getChildren().add(progressBar);

            Label summary = new Label(completedCount + " / " + totalCount + " 已完成");
            summary.getStyleClass().add("chat-message__todo-summary");
            body.getChildren().add(summary);
        }

        this.getChildren().add(body);
    }

    private String getStatusText(String status) {
        return switch (status) {
            case "pending" -> "待处理";
            case "in_progress" -> "进行中";
            case "completed" -> "已完成";
            default -> status;
        };
    }

    private List<JsonNode> parseTodos(String todosJson) {
        try {
            JsonNode parsed = JsonUtils.parse(todosJson);
            if (parsed != null && parsed.has("todos")) {
                JsonNode arr = parsed.get("todos");
                if (arr != null && !arr.isNull() && arr.isArray()) {
                    List<JsonNode> result = new ArrayList<>();
                    for (JsonNode item : arr) {
                        result.add(item);
                    }
                    return result;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return List.of();
    }

    private String getString(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value != null && !value.isNull() ? value.asText() : null;
    }
}
