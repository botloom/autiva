package cn.bitloom.node;

import cn.bitloom.agentic.session.SessionManager;
import cn.bitloom.util.MarkdownFxRenderer;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
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
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TaskCard extends VBox {

    private static final String FONT_FAMILY = "\"SF Pro Text\", -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif";

    private final Label statusLabel;
    private final VBox body;
    private Timeline pulseTimeline;
    private final Circle pulseDot;

    private final VBox messageContainer;
    private final StringBuilder streamBuffer = new StringBuilder();
    private VBox currentStreamBox = null;
    private ToolGroupCard currentToolGroup = null;

    private Disposable eventSubscription;
    private boolean userCollapsed = false;

    @Setter
    private Consumer<String> onContentChanged;

    public TaskCard(String taskJson) {
        this.getStyleClass().add("chat-message");
        this.getStyleClass().add("chat-message--tool");
        this.getStyleClass().add("chat-message--task");

        JSONObject task = parseTask(taskJson);

        HBox header = new HBox(10);
        header.getStyleClass().add("chat-message__task-header");
        header.setAlignment(Pos.CENTER_LEFT);

        pulseDot = new Circle(5);
        pulseDot.getStyleClass().add("chat-message__task-pulse");
        startPulseAnimation();

        Label typeLabel = new Label(getSubagentDisplayName(task.getString("subagentName")));
        typeLabel.getStyleClass().add("chat-message__task-type");

        Label separatorLabel = new Label("·");
        separatorLabel.getStyleClass().add("chat-message__task-separator");

        Label descLabel = new Label(task.getString("description"));
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

        messageContainer = new VBox(6);
        messageContainer.getStyleClass().add("chat-message__task-messages");
        body.getChildren().add(messageContainer);

        this.getChildren().add(body);

        header.setOnMouseClicked(e -> toggleBody());
    }

    public void subscribeSession(String sessionId, SessionManager sessionManager) {
        if (sessionId == null || sessionManager == null) {
            return;
        }

        cn.bitloom.agentic.session.Session session = sessionManager.getById(sessionId);
        if (session == null || session.getMessageBus() == null) {
            return;
        }

        eventSubscription = session.getMessageBus().outBoxSubscribe()
                .subscribe(
                        message -> Platform.runLater(() -> processMessage(message)),
                        error -> {},
                        () -> {}
                );
    }

    public void dispose() {
        if (eventSubscription != null && !eventSubscription.isDisposed()) {
            eventSubscription.dispose();
        }
        stopPulseAnimation();
    }

    private void processMessage(Message msg) {
        JSONObject jsonObject = (JSONObject) JSON.toJSON(msg);
        String messageType = jsonObject.getString("messageType");
        if (messageType == null) {
            JSONObject metadata = jsonObject.getJSONObject("metadata");
            if (metadata != null) {
                messageType = metadata.getString("messageType");
            }
        }
        if (messageType == null) {
            messageType = msg.getMessageType().name();
        }

        if (MessageType.ASSISTANT.name().equals(messageType)) {
            processAssistantMessage(jsonObject);
        } else if (MessageType.TOOL.name().equals(messageType)) {
            processToolMessage(jsonObject);
        }

        ensureBodyVisible();
        notifyContentChanged();
    }

    private void processAssistantMessage(JSONObject jsonObject) {
        JSONObject metadata = jsonObject.getJSONObject("metadata");
        String finishReason = null;
        if (metadata != null) {
            finishReason = metadata.getString("finishReason");
        }
        String text = jsonObject.getString("text");
        if (text == null) {
            text = jsonObject.getString("content");
        }

        if (finishReason == null || finishReason.isBlank()) {
            streamBuffer.append(text != null ? text : "");
            String accumulated = streamBuffer.toString();
            if (accumulated.isBlank()) {
                return;
            }
            if (currentStreamBox == null) {
                closeCurrentToolGroup();
                currentStreamBox = new VBox(4);
                currentStreamBox.getStyleClass().add("chat-message__task-assistant");
                messageContainer.getChildren().add(currentStreamBox);
            }
            renderLightweightStream(currentStreamBox, accumulated);
        } else if ("STOP".equals(finishReason)) {
            if (currentStreamBox != null) {
                String content = streamBuffer.toString();
                if (!content.isBlank()) {
                    renderStreamContent(currentStreamBox, content);
                } else {
                    messageContainer.getChildren().remove(currentStreamBox);
                }
                currentStreamBox = null;
            } else if (text != null && !text.isBlank()) {
                appendMarkdownNode(text);
            }
            streamBuffer.setLength(0);
        } else if ("TOOL_CALLS".equals(finishReason)) {
            if (currentStreamBox != null) {
                String content = streamBuffer.toString();
                if (!content.isBlank()) {
                    renderStreamContent(currentStreamBox, content);
                } else {
                    messageContainer.getChildren().remove(currentStreamBox);
                }
                currentStreamBox = null;
            }

            JSONArray toolCalls = jsonObject.getJSONArray("toolCalls");
            if (toolCalls != null) {
                for (int i = 0; i < toolCalls.size(); i++) {
                    JSONObject tc = toolCalls.getJSONObject(i);
                    appendToolCallCard(tc.getString("name"), tc.getString("arguments"));
                }
            }
            streamBuffer.setLength(0);
        }
    }

    private void processToolMessage(JSONObject jsonObject) {
        JSONArray responses = jsonObject.getJSONArray("responses");
        if (responses != null && !responses.isEmpty()) {
            for (int i = 0; i < responses.size(); i++) {
                JSONObject resp = responses.getJSONObject(i);
                appendToolResponseCard(resp.getString("name"), resp.getString("responseData"));
            }
        }
    }

    private void renderLightweightStream(VBox container, String content) {
        container.getChildren().clear();
        if (content == null || content.isBlank()) return;

        TextFlow textFlow = new TextFlow();
        textFlow.getStyleClass().add("md-paragraph");
        textFlow.getStyleClass().add("chat-message__task-md-content");
        textFlow.setMaxWidth(Double.MAX_VALUE);
        Text text = new Text(content);
        text.setFont(Font.font(FONT_FAMILY, 13));
        textFlow.getChildren().add(text);
        container.getChildren().add(textFlow);
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
        closeCurrentToolGroup();
        VBox mdBox = new VBox(4);
        mdBox.getStyleClass().add("chat-message__task-assistant");
        renderStreamContent(mdBox, content);
        messageContainer.getChildren().add(mdBox);
    }

    private void appendToolCallCard(String toolName, String arguments) {
        ToolMessageCard card = new ToolMessageCard(toolName, arguments, true);
        HBox wrapper = new HBox(card);
        wrapper.setAlignment(Pos.CENTER_LEFT);
        addToToolGroup(wrapper, toolName);
    }

    private void appendToolResponseCard(String toolName, String responseData) {
        ToolMessageCard card = new ToolMessageCard(toolName, responseData, false);
        HBox wrapper = new HBox(card);
        wrapper.setAlignment(Pos.CENTER_LEFT);
        addToToolGroup(wrapper, toolName);
    }

    private void addToToolGroup(Node toolCard, String toolName) {
        if (currentToolGroup != null) {
            currentToolGroup.addToolCard(toolCard, toolName);
        } else {
            currentToolGroup = new ToolGroupCard();
            currentToolGroup.addToolCard(toolCard, toolName);
            messageContainer.getChildren().add(currentToolGroup);
        }
    }

    private void closeCurrentToolGroup() {
        currentToolGroup = null;
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

    public void appendOutput(String text) {
        Platform.runLater(() -> doAppendOutput(text));
    }

    private void doAppendOutput(String text) {
        ensureBodyVisible();
        streamBuffer.append(text);
        if (currentStreamBox == null) {
            closeCurrentToolGroup();
            currentStreamBox = new VBox(4);
            currentStreamBox.getStyleClass().add("chat-message__task-assistant");
            messageContainer.getChildren().add(currentStreamBox);
        }
        renderLightweightStream(currentStreamBox, streamBuffer.toString());
        notifyContentChanged();
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
            closeCurrentToolGroup();
            if (result != null && !result.isBlank()) {
                streamBuffer.append("\n").append(result);
            }
            if (currentStreamBox != null && !streamBuffer.isEmpty()) {
                renderStreamContent(currentStreamBox, streamBuffer.toString());
                currentStreamBox = null;
            }
            streamBuffer.setLength(0);
            doSetStatus("completed");
            dispose();
        });
    }

    private void notifyContentChanged() {
        if (onContentChanged != null) {
            onContentChanged.accept(streamBuffer.toString());
        }
    }

    private JSONObject parseTask(String taskJson) {
        try {
            return JSON.parseObject(taskJson);
        } catch (Exception e) {
            JSONObject fallback = new JSONObject();
            fallback.put("description", taskJson);
            return fallback;
        }
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
