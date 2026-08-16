package cn.bitloom.node;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Plan Mode 指示条（计划模式状态指示）：输入框上方显示只读探索模式状态与退出按钮。
 *
 * <p>进入计划模式时由 Controller 显示，退出时隐藏。
 */
public class PlanModeBar extends HBox {

    private final Button exitButton;

    public PlanModeBar(Runnable onExit) {
        setPadding(new Insets(6, 12, 6, 12));
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(8);
        setMaxWidth(Double.MAX_VALUE);
        setStyle("""
                -fx-background-color: rgba(0, 113, 227, 0.08);
                -fx-background-radius: 12;
                -fx-border-color: rgba(0, 113, 227, 0.2);
                -fx-border-width: 1;
                -fx-border-radius: 12;
                """);

        Label modeLabel = new Label("计划模式");
        modeLabel.setStyle("-fx-text-fill: #0071e3; -fx-font-size: 12px; -fx-font-weight: 600;");

        Label hintLabel = new Label("只读探索中 · 将提交计划等待批准");
        hintLabel.setStyle("-fx-text-fill: #6e6e73; -fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        exitButton = new Button("退出计划模式");
        exitButton.setStyle("""
                -fx-background-color: #ffffff;
                -fx-text-fill: #1d1d1f;
                -fx-background-radius: 8;
                -fx-font-size: 12px;
                -fx-padding: 4 12 4 12;
                -fx-border-color: rgba(0, 0, 0, 0.12);
                -fx-border-radius: 8;
                """);
        exitButton.setOnAction(e -> onExit.run());

        getChildren().addAll(modeLabel, hintLabel, spacer, exitButton);
    }
}
