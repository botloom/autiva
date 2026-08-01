package cn.bitloom.node.tool;

import cn.bitloom.agentic.tool.file.FileDiff;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import lombok.extern.slf4j.Slf4j;

import java.util.function.BiConsumer;

/**
 * Diff 审核卡片
 * 用于 WriteTool/EditTool 的文件修改审核展示。
 * 包含双列行号（old/new）、gutter 指示条、+/- 前缀着色。
 */
@Slf4j
public class DiffReviewCard extends VBox {

    private static final double LINENO_COL_WIDTH = 36;
    private static final double GUTTER_WIDTH = 3;

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
        VBox diffContent = new VBox(0);
        diffContent.getStyleClass().add("diff-review-card__content");

        if (diff.hunks() != null) {
            for (FileDiff.Hunk hunk : diff.hunks()) {
                // 不展示 @@ hunk 头行
                if (hunk.lines() == null) continue;
                int currentOldLine = hunk.oldStart();
                int currentNewLine = hunk.newStart();

                for (FileDiff.DiffLine line : hunk.lines()) {
                    HBox lineRow = new HBox();
                    lineRow.setAlignment(Pos.CENTER_LEFT);

                    // 计算行号
                    int oldLine = -1, newLine = -1;
                    switch (line.type()) {
                        case ADD -> newLine = currentNewLine++;
                        case REMOVE -> oldLine = currentOldLine++;
                        case CONTEXT -> { oldLine = currentOldLine++; newLine = currentNewLine++; }
                    }

                    // Gutter 指示条
                    Region gutter = new Region();
                    gutter.getStyleClass().add("diff-review-card__gutter");
                    gutter.setPrefWidth(GUTTER_WIDTH);
                    gutter.setMinWidth(GUTTER_WIDTH);
                    gutter.setMaxWidth(GUTTER_WIDTH);
                    if (line.type() == FileDiff.Type.ADD) {
                        gutter.getStyleClass().add("diff-review-card__gutter--add");
                    } else if (line.type() == FileDiff.Type.REMOVE) {
                        gutter.getStyleClass().add("diff-review-card__gutter--remove");
                    }

                    // Old 行号
                    Label oldLineLabel = new Label(oldLine > 0 ? String.valueOf(oldLine) : "");
                    oldLineLabel.getStyleClass().add("diff-review-card__lineno");
                    oldLineLabel.setPrefWidth(LINENO_COL_WIDTH);
                    oldLineLabel.setMinWidth(LINENO_COL_WIDTH);
                    oldLineLabel.setAlignment(Pos.CENTER_RIGHT);
                    if (line.type() == FileDiff.Type.REMOVE) {
                        oldLineLabel.getStyleClass().add("diff-review-card__lineno--remove");
                    }

                    // New 行号
                    Label newLineLabel = new Label(newLine > 0 ? String.valueOf(newLine) : "");
                    newLineLabel.getStyleClass().add("diff-review-card__lineno");
                    newLineLabel.setPrefWidth(LINENO_COL_WIDTH);
                    newLineLabel.setMinWidth(LINENO_COL_WIDTH);
                    newLineLabel.setAlignment(Pos.CENTER_RIGHT);
                    if (line.type() == FileDiff.Type.ADD) {
                        newLineLabel.getStyleClass().add("diff-review-card__lineno--add");
                    }

                    // 文本内容（不展示 +/- 前缀，纯色覆盖）
                    TextFlow lineFlow = new TextFlow();
                    lineFlow.getStyleClass().add("diff-review-card__line");
                    Text contentText = new Text(line.content());
                    lineFlow.getChildren().add(contentText);

                    switch (line.type()) {
                        case ADD -> lineRow.getStyleClass().add("diff-review-card__line-row--add");
                        case REMOVE -> lineRow.getStyleClass().add("diff-review-card__line-row--remove");
                        case CONTEXT -> lineRow.getStyleClass().add("diff-review-card__line-row--context");
                    }

                    lineRow.getChildren().addAll(gutter, oldLineLabel, newLineLabel, lineFlow);
                    diffContent.getChildren().add(lineRow);
                }
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
