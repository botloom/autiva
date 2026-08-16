package cn.bitloom.node.tool;

import cn.bitloom.agentic.tool.plan.ExitPlanModeTool;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * 计划批准卡片（Plan Mode）：展示智能体提交的实施计划，等待用户决策。
 *
 * <p>三个动作：
 * <ul>
 *   <li>批准并执行 → {@link ExitPlanModeTool#DECISION_APPROVED}（退出计划模式，流结束后自动执行）</li>
 *   <li>发送反馈 → {@link ExitPlanModeTool#FEEDBACK_PREFIX} + 反馈文本（智能体调整计划后重新提交）</li>
 *   <li>放弃 → {@link ExitPlanModeTool#DECISION_ABANDONED}（退出计划模式，不执行）</li>
 * </ul>
 * 决策后卡片从父容器移除（dismiss）。
 */
public class PlanApprovalCard extends VBox {

    /** 决策回调：APPROVED / FEEDBACK::文本 / ABANDONED */
    private final Consumer<String> decisionConsumer;

    public PlanApprovalCard(String plan, Consumer<String> decisionConsumer) {
        this.decisionConsumer = decisionConsumer;

        setPadding(new Insets(12, 14, 12, 14));
        setSpacing(8);
        setStyle("""
                -fx-background-color: #ffffff;
                -fx-background-radius: 12;
                -fx-border-radius: 12;
                -fx-border-color: rgba(0, 113, 227, 0.3);
                -fx-border-width: 1;
                """);

        Label title = new Label("计划待批准");
        title.setStyle("-fx-text-fill: #1d1d1f; -fx-font-size: 13px; -fx-font-weight: 600;");

        TextArea planArea = new TextArea(plan);
        planArea.setEditable(false);
        planArea.setWrapText(true);
        planArea.setPrefRowCount(Math.min(12, Math.max(4, plan.split("\n").length)));
        planArea.setStyle("""
                -fx-background-color: #f5f5f7;
                -fx-background-radius: 8;
                -fx-border-color: transparent;
                -fx-font-size: 12px;
                -fx-text-fill: #1d1d1f;
                """);
        VBox.setVgrow(planArea, Priority.ALWAYS);

        // 反馈输入行（默认隐藏，点击"发送反馈"展开）
        TextField feedbackField = new TextField();
        feedbackField.setPromptText("输入调整意见...");
        feedbackField.setStyle("""
                -fx-background-color: #ffffff;
                -fx-background-radius: 8;
                -fx-border-color: rgba(0, 0, 0, 0.12);
                -fx-border-radius: 8;
                -fx-font-size: 12px;
                """);
        feedbackField.setVisible(false);
        feedbackField.setManaged(false);
        HBox.setHgrow(feedbackField, Priority.ALWAYS);
        Runnable sendFeedback = () -> {
            String text = feedbackField.getText() == null ? "" : feedbackField.getText().trim();
            if (text.isEmpty()) {
                return;
            }
            decide(ExitPlanModeTool.FEEDBACK_PREFIX + text);
        };
        feedbackField.setOnAction(e -> sendFeedback.run());

        Button feedbackSendBtn = new Button("发送");
        feedbackSendBtn.setStyle("""
                -fx-background-color: #0071e3;
                -fx-text-fill: #ffffff;
                -fx-background-radius: 8;
                -fx-font-size: 12px;
                -fx-padding: 4 12 4 12;
                """);
        feedbackSendBtn.setVisible(false);
        feedbackSendBtn.setManaged(false);
        feedbackSendBtn.setOnAction(e -> sendFeedback.run());
        HBox feedbackRow = new HBox(8, feedbackField, feedbackSendBtn);
        feedbackRow.setAlignment(Pos.CENTER_LEFT);

        Button feedbackBtn = new Button("发送反馈");
        feedbackBtn.setStyle("""
                -fx-background-color: #f5f5f7;
                -fx-text-fill: #1d1d1f;
                -fx-background-radius: 8;
                -fx-font-size: 12px;
                -fx-padding: 5 14 5 14;
                -fx-border-color: rgba(0, 0, 0, 0.08);
                -fx-border-radius: 8;
                """);
        feedbackBtn.setOnAction(e -> {
            boolean show = !feedbackField.isVisible();
            feedbackField.setVisible(show);
            feedbackField.setManaged(show);
            feedbackSendBtn.setVisible(show);
            feedbackSendBtn.setManaged(show);
            if (show) {
                feedbackField.requestFocus();
            }
        });

        Button abandonBtn = new Button("放弃");
        abandonBtn.setStyle("""
                -fx-background-color: #f5f5f7;
                -fx-text-fill: #1d1d1f;
                -fx-background-radius: 8;
                -fx-font-size: 12px;
                -fx-padding: 5 14 5 14;
                -fx-border-color: rgba(0, 0, 0, 0.08);
                -fx-border-radius: 8;
                """);
        abandonBtn.setOnAction(e -> decide(ExitPlanModeTool.DECISION_ABANDONED));

        Button approveBtn = new Button("批准并执行");
        approveBtn.setStyle("""
                -fx-background-color: #0071e3;
                -fx-text-fill: #ffffff;
                -fx-background-radius: 8;
                -fx-font-size: 12px;
                -fx-font-weight: 600;
                -fx-padding: 5 16 5 16;
                """);
        approveBtn.setOnAction(e -> decide(ExitPlanModeTool.DECISION_APPROVED));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, feedbackBtn, abandonBtn, spacer, approveBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(title, planArea, feedbackRow, buttons);
    }

    private void decide(String decision) {
        dismiss();
        decisionConsumer.accept(decision);
    }

    /** 从父容器移除自身 */
    public void dismiss() {
        if (getParent() instanceof javafx.scene.layout.Pane pane) {
            Platform.runLater(() -> pane.getChildren().remove(this));
        }
    }
}
