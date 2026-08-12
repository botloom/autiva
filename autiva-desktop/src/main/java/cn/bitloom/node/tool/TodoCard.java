package cn.bitloom.node.tool;

import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.util.ArrayList;
import java.util.List;

public class TodoCard extends VBox {

    private static final double RING_RADIUS = 12;
    private static final double RING_STROKE = 3;
    private static final double RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS;

    public TodoCard(String todosJson) {
        getStyleClass().add("chat-message");
        getStyleClass().add("chat-message--tool");
        getStyleClass().add("chat-message--todo");
        // 卡片宽度跟随 ListView cell，不基于内容自然宽度撑大（与工具卡片 ToolMessageCard 一致）
        setMaxWidth(Double.MAX_VALUE);
        rebuild(todosJson);
    }

    /**
     * 原地更新卡片内容（不新建卡片）
     */
    public void update(String todosJson) {
        getChildren().clear();
        rebuild(todosJson);
    }

    private void rebuild(String todosJson) {
        List<JsonNode> todoItems = parseTodos(todosJson);
        int completedCount = 0;
        int totalCount = todoItems.size();
        for (JsonNode item : todoItems) {
            if ("completed".equals(getString(item, "status"))) {
                completedCount++;
            }
        }

        // 顶部：进度环 + 摘要 + 工具名
        HBox header = new HBox(10);
        header.getStyleClass().add("chat-message__tool-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setMaxWidth(Double.MAX_VALUE);

        StackPane progressRing = createProgressRing(completedCount, totalCount);
        header.getChildren().add(progressRing);

        VBox titleBox = new VBox(1);
        titleBox.setMaxWidth(Double.MAX_VALUE);
        Label nameLabel = new Label("TodoWrite");
        nameLabel.getStyleClass().add("chat-message__tool-name");
        nameLabel.setStyle("-fx-text-fill: #b45309;");
        Label summaryText = new Label(completedCount + " / " + totalCount + " 已完成");
        summaryText.getStyleClass().add("chat-message__todo-summary-text");
        titleBox.getChildren().addAll(nameLabel, summaryText);
        header.getChildren().add(titleBox);
        getChildren().add(header);

        // 紧凑清单
        VBox body = new VBox(4);
        body.getStyleClass().add("chat-message__todo-body");
        body.setPadding(new Insets(6, 0, 0, 0));
        body.setMaxWidth(Double.MAX_VALUE);

        for (JsonNode item : todoItems) {
            String content = getString(item, "content");
            String status = getString(item, "status");
            String activeForm = getString(item, "activeForm");

            HBox itemRow = new HBox(8);
            itemRow.getStyleClass().add("chat-message__todo-item");
            itemRow.setAlignment(Pos.CENTER_LEFT);

            Circle statusDot = new Circle(4);
            statusDot.getStyleClass().add("chat-message__todo-status");
            statusDot.getStyleClass().add("chat-message__todo-status--" + status);
            itemRow.getChildren().add(statusDot);

            Label contentLabel = new Label(content);
            contentLabel.setWrapText(true);
            contentLabel.getStyleClass().add("chat-message__todo-text");
            if ("completed".equals(status)) {
                contentLabel.getStyleClass().add("chat-message__todo-text--completed");
            }
            HBox.setHgrow(contentLabel, javafx.scene.layout.Priority.ALWAYS);
            itemRow.getChildren().add(contentLabel);

            if (activeForm != null && !"completed".equals(status)) {
                Label activeFormLabel = new Label(activeForm);
                activeFormLabel.getStyleClass().add("chat-message__todo-active-form");
                itemRow.getChildren().add(activeFormLabel);
            }

            Label statusLabel = new Label(getStatusText(status));
            statusLabel.getStyleClass().add("chat-message__todo-status-label");
            statusLabel.getStyleClass().add("chat-message__todo-status-label--" + status);
            itemRow.getChildren().add(statusLabel);

            body.getChildren().add(itemRow);
        }
        getChildren().add(body);
    }

    /**
     * 创建环形进度指示器：背景灰圆环 + 前景绿圆环（按完成比例显示弧长）
     */
    private StackPane createProgressRing(int completed, int total) {
        StackPane ring = new StackPane();
        ring.getStyleClass().add("chat-message__todo-progress-ring");

        Circle bg = new Circle(RING_RADIUS);
        bg.getStyleClass().add("chat-message__todo-progress-ring-bg");
        bg.setStrokeWidth(RING_STROKE);
        bg.setFill(null);

        Circle fg = new Circle(RING_RADIUS);
        fg.getStyleClass().add("chat-message__todo-progress-ring-fg");
        fg.setStrokeWidth(RING_STROKE);
        fg.setFill(null);
        fg.setRotationAxis(javafx.scene.transform.Rotate.Z_AXIS);
        fg.setRotate(-90);
        if (total > 0) {
            double ratio = (double) completed / total;
            fg.getStrokeDashArray().addAll(ratio * RING_CIRCUMFERENCE, RING_CIRCUMFERENCE);
        } else {
            fg.getStrokeDashArray().addAll(0.0, RING_CIRCUMFERENCE);
        }

        ring.getChildren().addAll(bg, fg);
        return ring;
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
