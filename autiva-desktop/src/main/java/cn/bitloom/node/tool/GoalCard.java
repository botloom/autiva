package cn.bitloom.node.tool;

import cn.bitloom.util.JsonUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * 目标状态卡片（Goal Loop）：展示当前目标、状态、判定次数与最近判定原因。
 * 每次判定后更新同一张卡片（复用引用，类似 TodoCard 主对话模式）。
 */
public class GoalCard extends VBox {

    private final Label statusLabel;
    private final Label statsLabel;
    private final Label reasonLabel;

    public GoalCard(String goalJson) {
        setPadding(new Insets(12, 14, 12, 14));
        setSpacing(6);
        setStyle("""
                -fx-background-color: #f5f5f7;
                -fx-background-radius: 12;
                -fx-border-radius: 12;
                -fx-border-color: rgba(0,0,0,0.08);
                -fx-border-width: 1;
                -fx-max-width: 520;
                """);

        Label title = new Label("目标");
        title.setStyle("-fx-text-fill: #86868b; -fx-font-size: 11px; -fx-font-weight: 600;");

        Label goalLabel = new Label();
        goalLabel.setStyle("-fx-text-fill: #1d1d1f; -fx-font-size: 13px;");
        goalLabel.setWrapText(true);

        statusLabel = new Label();
        statusLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 600;");
        statsLabel = new Label();
        statsLabel.setStyle("-fx-text-fill: #86868b; -fx-font-size: 11px;");
        reasonLabel = new Label();
        reasonLabel.setStyle("-fx-text-fill: #6e6e73; -fx-font-size: 12px;");
        reasonLabel.setWrapText(true);

        HBox meta = new HBox(10, statusLabel, statsLabel);
        meta.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(title, goalLabel, meta, reasonLabel);
        VBox.setVgrow(reasonLabel, Priority.ALWAYS);

        update(goalJson);
    }

    /** 更新卡片内容（goalJson: {goal, status, judgeCount, blockedCount, lastReason}） */
    public final void update(String goalJson) {
        try {
            var node = JsonUtils.parse(goalJson);
            String goal = node.path("goal").asText("");
            String status = node.path("status").asText("active");
            int judgeCount = node.path("judgeCount").asInt(0);
            int blockedCount = node.path("blockedCount").asInt(0);
            String lastReason = node.path("lastReason").asText("");

            ((Label) getChildren().get(1)).setText(goal);
            statusLabel.setText(statusText(status));
            statusLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: "
                    + statusColor(status) + ";");
            statsLabel.setText("判定 " + judgeCount + " 次" + (blockedCount > 0 ? " · 连续未通过 " + blockedCount + " 次" : ""));
            reasonLabel.setText(lastReason == null || lastReason.isBlank() ? "" : "最近判定：" + lastReason);
            reasonLabel.setManaged(reasonLabel.getText() != null && !reasonLabel.getText().isBlank());
            reasonLabel.setVisible(reasonLabel.isManaged());
        } catch (Exception ignored) {
            // JSON 解析失败保持原样
        }
    }

    private String statusText(String status) {
        return switch (status) {
            case "achieved" -> "已达成";
            case "impossible" -> "无法达成";
            case "blocked" -> "已暂停（等待用户）";
            default -> "推进中";
        };
    }

    private String statusColor(String status) {
        return switch (status) {
            case "achieved" -> "#34c759";      // Apple 绿
            case "impossible", "blocked" -> "#ff3b30"; // Apple 红
            default -> "#0071e3";              // Apple 蓝
        };
    }
}
