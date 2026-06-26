package cn.bitloom.node.tool;

import cn.bitloom.agentic.diff.FileDiff;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import lombok.extern.slf4j.Slf4j;

import java.util.function.BiConsumer;

/**
 * Diff 审核卡片
 * 用于 WriteTool/EditTool 的文件修改审核展示
 */
@Slf4j
public class DiffReviewCard extends VBox {

    public DiffReviewCard(FileDiff diff, String reviewId, BiConsumer<String, String> onReviewed) {
        getStyleClass().addAll("chat-message", "chat-message--tool", "diff-review-card");

        // Header
        HBox header = new HBox(8);
        header.getStyleClass().add("diff-review-card__header");

        Label fileLabel = new Label(diff.filePath());
        fileLabel.getStyleClass().add("diff-review-card__file");

        String operationText = diff.isCreate() ? "新建文件" : diff.isDelete() ? "删除文件" : "修改文件";
        Label operationLabel = new Label(operationText);
        operationLabel.getStyleClass().add("diff-review-card__operation");

        header.getChildren().addAll(fileLabel, operationLabel);
        getChildren().add(header);

        // Diff 内容
        VBox diffContent = new VBox(2);
        diffContent.getStyleClass().add("diff-review-card__content");

        for (FileDiff.Hunk hunk : diff.hunks()) {
            // Hunk 头
            Label hunkHeader = new Label(String.format("@@ -%d,%d +%d,%d @@",
                    hunk.oldStart(), hunk.oldCount(), hunk.newStart(), hunk.newCount()));
            hunkHeader.getStyleClass().add("diff-review-card__hunk-header");
            diffContent.getChildren().add(hunkHeader);

            // Diff 行
            for (FileDiff.DiffLine line : hunk.lines()) {
                TextFlow lineFlow = new TextFlow();
                lineFlow.getStyleClass().add("diff-review-card__line");

                String prefix = switch (line.type()) {
                    case ADD -> "+";
                    case REMOVE -> "-";
                    case CONTEXT -> " ";
                };

                Text prefixText = new Text(prefix);
                Text contentText = new Text(line.content());

                switch (line.type()) {
                    case ADD -> lineFlow.getStyleClass().add("diff-review-card__line--add");
                    case REMOVE -> lineFlow.getStyleClass().add("diff-review-card__line--remove");
                    case CONTEXT -> lineFlow.getStyleClass().add("diff-review-card__line--context");
                }

                lineFlow.getChildren().addAll(prefixText, contentText);
                diffContent.getChildren().add(lineFlow);
            }
        }

        ScrollPane scrollPane = new ScrollPane(diffContent);
        scrollPane.getStyleClass().add("diff-review-card__scroll");
        scrollPane.setMaxHeight(400);
        scrollPane.setFitToWidth(true);
        getChildren().add(scrollPane);

        // 操作按钮
        HBox buttonBar = new HBox(8);
        buttonBar.getStyleClass().add("diff-review-card__buttons");
        buttonBar.setPadding(new Insets(8));

        Button approveButton = new Button("批准修改");
        approveButton.getStyleClass().add("diff-review-card__approve-btn");

        Button rejectButton = new Button("拒绝修改");
        rejectButton.getStyleClass().add("diff-review-card__reject-btn");

        approveButton.setOnAction(event -> {
            approveButton.setDisable(true);
            rejectButton.setDisable(true);
            onReviewed.accept(reviewId, "{\"approved\":true,\"comment\":\"\"}");
        });

        rejectButton.setOnAction(event -> {
            approveButton.setDisable(true);
            rejectButton.setDisable(true);
            onReviewed.accept(reviewId, "{\"approved\":false,\"comment\":\"\"}");
        });

        buttonBar.getChildren().addAll(approveButton, rejectButton);
        getChildren().add(buttonBar);
    }
}
