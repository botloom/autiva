package cn.bitloom.node.tool;

import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.node.message.ToolMessageCard;
import cn.bitloom.util.MarkdownFxRenderer;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class TaskCard extends VBox {

    private static final String FONT_FAMILY = "\"SF Pro Text\", -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif";

    private final Label statusLabel;
    private final VBox body;
    private final VBox messagesBox;
    private Timeline pulseTimeline;
    private final Circle pulseDot;

    private final StringBuilder streamBuffer = new StringBuilder();
    private VBox currentStreamBox = null;
    /** 流式期间复用的 TextFlow 和 Text（避免每个 chunk 重建） */
    private TextFlow streamingTextFlow = null;
    private Text streamingText = null;
    /** 节流标志：同一 FX 脉冲内多次 chunk 只调度一次 flush */
    private boolean textUpdateScheduled = false;
    private final Map<String, ToolMessageCard> pendingToolCards = new ConcurrentHashMap<>();

    private boolean userCollapsed = false;

    @Setter
    private Consumer<String> onContentChanged;

    public TaskCard(String taskJson) {
        this.getStyleClass().add("chat-message");
        this.getStyleClass().add("chat-message--tool");
        this.getStyleClass().add("chat-message--task");

        JsonNode task = parseTask(taskJson);

        HBox header = new HBox(10);
        header.getStyleClass().add("chat-message__task-header");
        header.setAlignment(Pos.CENTER_LEFT);

        pulseDot = new Circle(5);
        pulseDot.getStyleClass().add("chat-message__task-pulse");
        startPulseAnimation();

        Label typeLabel = new Label(getSubagentDisplayName(getString(task, "subagentName")));
        typeLabel.getStyleClass().add("chat-message__task-type");

        Label separatorLabel = new Label("·");
        separatorLabel.getStyleClass().add("chat-message__task-separator");

        Label descLabel = new Label(getString(task, "description"));
        descLabel.getStyleClass().add("chat-message__task-desc");
        descLabel.setWrapText(true);
        HBox.setHgrow(descLabel, Priority.ALWAYS);
        HBox.setMargin(descLabel, new Insets(0, 8, 0, 0));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusLabel = new Label("运行中");
        statusLabel.getStyleClass().add("chat-message__task-status");
        statusLabel.getStyleClass().add("chat-message__task-status--running");

        header.getChildren().addAll(pulseDot, typeLabel, separatorLabel, descLabel, spacer, statusLabel);
        this.getChildren().add(header);

        Region divider = new Region();
        divider.getStyleClass().add("chat-message__task-divider");
        this.getChildren().add(divider);

        body = new VBox(8);
        body.getStyleClass().add("chat-message__task-body");
        body.setVisible(false);
        body.setManaged(false);

        messagesBox = new VBox(4);
        messagesBox.getStyleClass().add("chat-message__task-messages");
        body.getChildren().add(messagesBox);

        this.getChildren().add(body);

        header.setOnMouseClicked(e -> toggleBody());
    }

    public void addTodoCard(TodoCard card) {
        Platform.runLater(() -> {
            addMessageNode(card);
            ensureBodyVisible();
            notifyContentChanged();
        });
    }

    public void addQuestionCard(QuestionCard card) {
        Platform.runLater(() -> {
            addMessageNode(card);
            ensureBodyVisible();
            notifyContentChanged();
        });
    }

    public void addApprovalCard(ApprovalCard card) {
        Platform.runLater(() -> {
            addMessageNode(card);
            ensureBodyVisible();
            notifyContentChanged();
        });
    }

    public void dispose() {
        stopPulseAnimation();
    }

    public void processEvent(MessageEvent event) {
        if (event.isAssistantMessage()) {
            processAssistantEvent(event);
        } else if (event.isToolResponse()) {
            processToolEvent(event);
        }

        ensureBodyVisible();
        notifyContentChanged();
    }

    private void processAssistantEvent(MessageEvent e) {
        String finishReason = e.getFinishReason();
        String text = e.getText();

        if (finishReason == null || finishReason.isBlank() || "_UNKNOWN".equals(finishReason)) {
            streamBuffer.append(text != null ? text : "");
            if (streamBuffer.isEmpty()) {
                return;
            }
            if (currentStreamBox == null) {
                currentStreamBox = new VBox(4);
                currentStreamBox.getStyleClass().add("chat-message__task-assistant");
                addMessageNode(currentStreamBox);
                initStreamingTextFlow(currentStreamBox);
            }
            scheduleFlush();
        } else if ("STOP".equals(finishReason)) {
            cancelPendingFlush();
            if (currentStreamBox != null) {
                String content = streamBuffer.toString();
                if (!content.isBlank()) {
                    renderStreamContent(currentStreamBox, content);
                } else {
                    messagesBox.getChildren().remove(currentStreamBox);
                }
                currentStreamBox = null;
                streamingTextFlow = null;
                streamingText = null;
            } else if (text != null && !text.isBlank()) {
                appendMarkdownNode(text);
            }
            streamBuffer.setLength(0);
        } else if ("TOOL_CALLS".equals(finishReason)) {
            cancelPendingFlush();
            if (currentStreamBox != null) {
                String content = streamBuffer.toString();
                if (!content.isBlank()) {
                    renderStreamContent(currentStreamBox, content);
                } else {
                    messagesBox.getChildren().remove(currentStreamBox);
                }
                currentStreamBox = null;
                streamingTextFlow = null;
                streamingText = null;
            }

            if (e.getToolCalls() != null) {
                for (MessageEvent.ToolCallInfo tc : e.getToolCalls()) {
                    appendToolCallCard(tc.id(), tc.name(), tc.arguments());
                }
            }
            streamBuffer.setLength(0);
        }
    }

    private void processToolEvent(MessageEvent e) {
        if (e.getResponses() != null && !e.getResponses().isEmpty()) {
            for (MessageEvent.ToolResponseInfo resp : e.getResponses()) {
                appendToolResponseCard(resp.id(), resp.name(), resp.responseData());
            }
        }
    }

    /**
     * 初始化复用的 TextFlow 和 Text 节点，加入 container。
     * 后续 chunk 通过 flushStreamingText 更新 streamingText.setText，不重建节点。
     */
    private void initStreamingTextFlow(VBox container) {
        streamingTextFlow = new TextFlow();
        streamingTextFlow.getStyleClass().add("md-paragraph");
        streamingTextFlow.getStyleClass().add("chat-message__task-md-content");
        streamingTextFlow.setMaxWidth(Double.MAX_VALUE);
        streamingText = new Text("");
        streamingText.setFont(Font.font(FONT_FAMILY, 13));
        streamingTextFlow.getChildren().add(streamingText);
        container.getChildren().add(streamingTextFlow);
    }

    /**
     * 调度节流式 UI 更新：同一 FX 脉冲内多次 chunk 只执行一次 flush。
     */
    private void scheduleFlush() {
        if (textUpdateScheduled) return;
        textUpdateScheduled = true;
        Platform.runLater(this::flushStreamingText);
    }

    /**
     * 取消 pending flush（STOP/TOOL_CALLS 时调用，避免残留 flush 干扰 Markdown 渲染）。
     */
    private void cancelPendingFlush() {
        textUpdateScheduled = false;
    }

    /**
     * 执行节流式 UI 更新：更新 streamingText、请求布局、通知外部重排。
     */
    private void flushStreamingText() {
        if (currentStreamBox == null || streamingText == null) {
            textUpdateScheduled = false;
            return;
        }
        textUpdateScheduled = false;
        String full = streamBuffer.toString();
        streamingText.setText(full);
        notifyContentChanged();
    }

    private void renderStreamContent(VBox container, String content) {
        container.getChildren().clear();
        if (content == null || content.isBlank()) return;

        try {
            VBox rendered = MarkdownFxRenderer.render(content);
            List<Node> childrenCopy = new ArrayList<>(rendered.getChildren());
            for (Node child : childrenCopy) {
                if (child instanceof TextFlow tf) {
                    tf.setMaxWidth(Double.MAX_VALUE);
                    tf.getStyleClass().add("chat-message__task-md-content");
                } else if (child instanceof Region region) {
                    region.setMaxWidth(Double.MAX_VALUE);
                }
                container.getChildren().add(child);
            }
        } catch (Exception e) {
            TextFlow textFlow = new TextFlow();
            textFlow.getStyleClass().add("chat-message__task-md-content");
            Text text = new Text(content);
            text.setFont(Font.font(FONT_FAMILY, 13));
            textFlow.getChildren().add(text);
            container.getChildren().add(textFlow);
        }
    }

    private void appendMarkdownNode(String content) {
        VBox mdBox = new VBox(4);
        mdBox.getStyleClass().add("chat-message__task-assistant");
        renderStreamContent(mdBox, content);
        addMessageNode(mdBox);
    }

    private void appendToolCallCard(String toolCallId, String toolName, String arguments) {
        ToolMessageCard card = new ToolMessageCard(toolCallId, toolName, arguments);
        pendingToolCards.put(toolCallId, card);
        addMessageNode(card);
    }

    private void appendToolResponseCard(String toolCallId, String toolName, String responseData) {
        ToolMessageCard card = pendingToolCards.remove(toolCallId);
        if (card != null) {
            card.setResponse(responseData);
        }
    }

    /**
     * 添加消息节点到 messagesBox，并确保节点宽度跟随容器宽度（避免内容溢出）。
     */
    private void addMessageNode(Node node) {
        if (node instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        messagesBox.getChildren().add(node);
    }

    private void ensureBodyVisible() {
        if (!body.isVisible() && !userCollapsed) {
            body.setVisible(true);
            body.setManaged(true);
        }
    }

    private void toggleBody() {
        boolean expanded = body.isVisible();
        if (expanded) {
            userCollapsed = true;
        }
        body.setVisible(!expanded);
        body.setManaged(!expanded);
        notifyContentChanged();
    }

    /**
     * 折叠卡片正文（仅保留 header）。完成后自动调用，用户可点击 header 重新展开查看。
     */
    private void collapseBody() {
        if (body.isVisible()) {
            body.setVisible(false);
            body.setManaged(false);
            notifyContentChanged();
        }
    }

    private void startPulseAnimation() {
        pulseTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, e -> pulseDot.setOpacity(1.0)),
                new KeyFrame(Duration.millis(800), e -> pulseDot.setOpacity(0.3)),
                new KeyFrame(Duration.millis(1600), e -> pulseDot.setOpacity(1.0))
        );
        pulseTimeline.setCycleCount(Animation.INDEFINITE);
        pulseTimeline.play();
    }

    private void stopPulseAnimation() {
        if (pulseTimeline != null) {
            pulseTimeline.stop();
            pulseDot.setOpacity(1.0);
        }
    }

    public void setStatus(String status) {
        Platform.runLater(() -> doSetStatus(status));
    }

    private void doSetStatus(String status) {
        statusLabel.getStyleClass().removeIf(s -> s.startsWith("chat-message__task-status--"));
        statusLabel.setText(switch (status) {
            case "completed" -> "已完成";
            case "failed" -> "失败";
            case "running" -> "运行中";
            default -> status
        ;
        });
        statusLabel.getStyleClass().add("chat-message__task-status--" + status);

        if ("completed".equals(status) || "failed".equals(status)) {
            stopPulseAnimation();
            pulseDot.getStyleClass().add("chat-message__task-pulse--" + status);
        }
    }

    public void complete(String result) {
        Platform.runLater(() -> {
            cancelPendingFlush();
            if (result != null && !result.isBlank()) {
                streamBuffer.append("\n").append(result);
            }
            if (currentStreamBox != null && !streamBuffer.isEmpty()) {
                renderStreamContent(currentStreamBox, streamBuffer.toString());
                currentStreamBox = null;
                streamingTextFlow = null;
                streamingText = null;
            }
            streamBuffer.setLength(0);
            doSetStatus("completed");
            dispose();
            // 输出完成后自动折叠卡片，仅保留 header（状态/标题），点击 header 可随时展开查看
            collapseBody();
        });
    }

    private void notifyContentChanged() {
        if (onContentChanged != null) {
            onContentChanged.accept(streamBuffer.toString());
        }
    }

    private JsonNode parseTask(String taskJson) {
        try {
            return JsonUtils.parse(taskJson);
        } catch (Exception e) {
            ObjectNode fallback = JsonUtils.createObject();
            fallback.put("description", taskJson);
            return fallback;
        }
    }

    private String getString(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value != null && !value.isNull() ? value.asText() : null;
    }

    private String getSubagentDisplayName(String subagentType) {
        if (subagentType == null) return "Task";
        return switch (subagentType.toLowerCase()) {
            case "code" -> "Code";
            case "search" -> "Search";
            case "a2a" -> "A2A";
            default -> subagentType;
        };
    }
}
