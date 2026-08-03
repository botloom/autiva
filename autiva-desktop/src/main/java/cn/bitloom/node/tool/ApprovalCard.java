package cn.bitloom.node.tool;

import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.BiConsumer;

/**
 * 批准卡片 — 显示在输入框上方的 approvalBar 中（不持久化到聊天历史）。
 *
 * <p>支持两种操作类型：
 * <ul>
 *   <li>{@code COMMAND}：命令执行批准（CommandTool），展示命令文本 + 风险等级</li>
 *   <li>{@code FILE}：文件写操作批准（WriteTool / EditTool），展示工具名 + 文件路径 + 动作</li>
 * </ul>
 *
 * <p>三个按钮：批准一次（蓝色主按钮）、永久批准（次要按钮）、拒绝（灰色按钮）。
 * 用户选择后自动从 approvalBar 移除，approvalBar 无内容时自动隐藏。
 */
public class ApprovalCard extends VBox {

    public ApprovalCard(String approvalJson, String approvalId, BiConsumer<String, String> onAnswered) {
        this.getStyleClass().add("approval-card");

        JsonNode node = parse(approvalJson);
        String operation = getString(node, "operation");
        String command = getString(node, "command");
        String commandClass = getString(node, "commandClass");
        String reason = getString(node, "reason");
        String projectDir = getString(node, "projectDir");
        String toolName = getString(node, "toolName");
        String filePath = getString(node, "filePath");
        String action = getString(node, "action");

        boolean isFile = "FILE".equalsIgnoreCase(operation);
        boolean isDestructive = "DESTRUCTIVE".equalsIgnoreCase(commandClass);

        // ===== 标题行：图标 + 标题 + 风险标签 =====
        HBox header = new HBox(8);
        header.getStyleClass().add("approval-card__header");

        Label iconLabel = new Label(isDestructive ? "⚠" : "!");
        iconLabel.getStyleClass().add("approval-card__icon");
        if (isDestructive) {
            iconLabel.getStyleClass().add("approval-card__icon--danger");
        }

        Label titleLabel = new Label(isFile ? "文件操作批准" : "命令批准");
        titleLabel.getStyleClass().add("approval-card__title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label riskTag = new Label();
        riskTag.getStyleClass().add("approval-card__risk-tag");
        if (isDestructive) {
            riskTag.setText("破坏性");
            riskTag.getStyleClass().add("approval-card__risk-tag--danger");
        } else {
            riskTag.setText("写操作");
            riskTag.getStyleClass().add("approval-card__risk-tag--warn");
        }

        header.getChildren().addAll(iconLabel, titleLabel, spacer, riskTag);
        this.getChildren().add(header);

        // ===== 内容区 =====
        VBox body = new VBox(4);
        body.getStyleClass().add("approval-card__body");

        if (isFile) {
            // 文件操作：工具名 + 动作 + 文件路径
            HBox metaRow = new HBox(12);
            metaRow.getStyleClass().add("approval-card__meta-row");
            if (toolName != null && !toolName.isBlank()) {
                Label toolLabel = new Label(toolName);
                toolLabel.getStyleClass().add("approval-card__meta-tag");
                metaRow.getChildren().add(toolLabel);
            }
            if (action != null && !action.isBlank()) {
                Label actionLabel = new Label(action);
                actionLabel.getStyleClass().add("approval-card__meta-tag");
                metaRow.getChildren().add(actionLabel);
            }
            if (!metaRow.getChildren().isEmpty()) {
                body.getChildren().add(metaRow);
            }
            if (filePath != null && !filePath.isBlank()) {
                Label pathLabel = new Label(filePath);
                pathLabel.getStyleClass().add("approval-card__code");
                pathLabel.setWrapText(true);
                body.getChildren().add(pathLabel);
            }
        } else {
            // 命令：命令原文（等宽字体）
            if (command != null && !command.isBlank()) {
                Label cmdLabel = new Label(command);
                cmdLabel.getStyleClass().add("approval-card__code");
                cmdLabel.setWrapText(true);
                body.getChildren().add(cmdLabel);
            }
        }

        if (reason != null && !reason.isBlank()) {
            Label reasonLabel = new Label(reason);
            reasonLabel.getStyleClass().add("approval-card__reason");
            reasonLabel.setWrapText(true);
            body.getChildren().add(reasonLabel);
        }

        if (projectDir != null && !projectDir.isBlank()) {
            Label dirLabel = new Label(projectDir);
            dirLabel.getStyleClass().add("approval-card__project");
            dirLabel.setWrapText(true);
            body.getChildren().add(dirLabel);
        }

        this.getChildren().add(body);

        // ===== 按钮区 =====
        HBox buttonRow = new HBox(8);
        buttonRow.getStyleClass().add("approval-card__buttons");

        Button approveOnceBtn = new Button("批准");
        approveOnceBtn.getStyleClass().addAll("approval-card__btn", "approval-card__btn--primary");
        approveOnceBtn.setOnAction(e -> submit(approvalId, "APPROVE_ONCE", onAnswered, buttonRow));

        Button approvePermanentBtn = new Button("永久批准");
        approvePermanentBtn.getStyleClass().addAll("approval-card__btn", "approval-card__btn--secondary");
        approvePermanentBtn.setTooltip(new javafx.scene.control.Tooltip(
                isFile ? "写入 .autiva/command-approvals.json，该项目内同工具调用自动放行"
                       : "写入 .autiva/command-approvals.json，后续同类命令自动放行"));
        approvePermanentBtn.setOnAction(e -> submit(approvalId, "APPROVE_PERMANENT", onAnswered, buttonRow));

        Button denyBtn = new Button("拒绝");
        denyBtn.getStyleClass().addAll("approval-card__btn", "approval-card__btn--deny");
        denyBtn.setOnAction(e -> submit(approvalId, "DENY", onAnswered, buttonRow));

        Region btnSpacer = new Region();
        HBox.setHgrow(btnSpacer, Priority.ALWAYS);
        buttonRow.getChildren().addAll(btnSpacer, denyBtn, approvePermanentBtn, approveOnceBtn);
        this.getChildren().add(buttonRow);
    }

    /**
     * 提交选择：禁用所有按钮，回调 onAnswered，然后从父容器移除。
     */
    private void submit(String approvalId, String result,
                        BiConsumer<String, String> onAnswered,
                        HBox buttonRow) {
        buttonRow.getChildren().forEach(node -> {
            if (node instanceof Button btn) {
                btn.setDisable(true);
            }
        });

        String resultJson = JsonUtils.toJson(java.util.Map.of("result", result));
        onAnswered.accept(approvalId, resultJson);

        // 从 approvalBar 移除，无内容时隐藏
        Platform.runLater(() -> {
            if (getParent() instanceof VBox parent) {
                parent.getChildren().remove(this);
                if (parent.getChildren().isEmpty()) {
                    parent.setVisible(false);
                    parent.setManaged(false);
                }
            }
        });
    }

    private JsonNode parse(String json) {
        try {
            return JsonUtils.parse(json);
        } catch (Exception e) {
            return JsonUtils.parse("{}");
        }
    }

    private String getString(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value != null && !value.isNull() ? value.asText() : null;
    }
}
