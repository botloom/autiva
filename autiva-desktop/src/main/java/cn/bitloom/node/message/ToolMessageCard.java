package cn.bitloom.node.message;

import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import lombok.Getter;

import java.util.Map;

/**
 * 工具调用卡片（请求 + 响应合并）。
 * <p>
 * 通过 toolCallId 关联同一个工具调用的请求参数和响应结果。
 * 默认只显示 header（工具名 + 状态点），点击展开显示请求参数和响应结果两个区块。
 */
@Getter
public class ToolMessageCard extends MessageCard {

    private final String toolCallId;
    private final String toolName;
    private final String arguments;

    private ToolResult result;
    private boolean responseReceived = false;

    private final Circle statusDot;
    private final VBox contentBox;
    private final VBox requestBox;
    private VBox responseBox;

    public ToolMessageCard(String toolCallId, String toolName, String arguments) {
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.arguments = arguments;

        this.getStyleClass().add("chat-message");
        this.getStyleClass().add("chat-message--tool");
        this.getStyleClass().add("chat-message--tool-request");
        setFocusTraversable(false);

        // 状态点：初始 pending（灰色）
        statusDot = new Circle(4);
        statusDot.getStyleClass().add("chat-message__tool-status-dot");
        statusDot.getStyleClass().add("chat-message__tool-status-dot--pending");

        // header
        HBox header = buildHeader(toolName, statusDot, null);
        this.getChildren().add(header);

        // 折叠内容容器（默认折叠）
        contentBox = new VBox(8);
        contentBox.getStyleClass().add("chat-message__tool-content");
        contentBox.setVisible(false);
        contentBox.setManaged(false);

        // 请求参数区块
        requestBox = new VBox(4);
        requestBox.getStyleClass().add("chat-message__tool-request-box");
        Label requestLabel = new Label("请求参数");
        requestLabel.getStyleClass().add("chat-message__tool-section-label");
        requestBox.getChildren().add(requestLabel);
        TextFlow requestFlow = buildJsonContent(arguments);
        requestBox.getChildren().add(requestFlow);
        contentBox.getChildren().add(requestBox);

        this.getChildren().add(contentBox);

        header.setOnMouseClicked(e -> toggleContent(contentBox));
    }

    /**
     * 追加响应结果，更新状态点。
     */
    public void setResponse(String responseData) {
        if (responseReceived) {
            return;
        }
        responseReceived = true;

        ToolResult toolResult = ToolResult.fromJson(responseData);
        this.result = toolResult;

        // 更新状态点
        statusDot.getStyleClass().remove("chat-message__tool-status-dot--pending");
        if (toolResult != null) {
            String statusStyle = switch (toolResult.getStatus()) {
                case SUCCESS -> "chat-message--tool-success";
                case ERROR -> "chat-message--tool-error";
                case WARNING -> "chat-message--tool-warning";
            };
            this.getStyleClass().add(statusStyle);
            statusDot.getStyleClass().add("chat-message__tool-status-dot--" + toolResult.getStatus().name().toLowerCase());
        } else {
            statusDot.getStyleClass().add("chat-message__tool-status-dot--success");
        }

        // 构建响应区块
        responseBox = new VBox(4);
        responseBox.getStyleClass().add("chat-message__tool-response-box");
        Label responseLabel = new Label("执行结果");
        responseLabel.getStyleClass().add("chat-message__tool-section-label");
        responseBox.getChildren().add(responseLabel);

        if (toolResult != null) {
            buildStructuredResponse(responseBox, toolResult);
        } else {
            TextFlow responseFlow = new TextFlow();
            responseFlow.getStyleClass().add("chat-message__tool-output");
            Text text = new Text(formatJSON(responseData));
            text.setFont(Font.font("\"SF Mono\", Monaco, \"Cascadia Code\", monospace", 12));
            responseFlow.getChildren().add(text);
            responseBox.getChildren().add(responseFlow);
        }

        contentBox.getChildren().add(responseBox);
    }

    private void buildStructuredResponse(VBox responseBox, ToolResult toolResult) {
        if (toolResult.getMessage() != null && !toolResult.getMessage().isEmpty()) {
            Label summaryLabel = new Label(toolResult.getMessage());
            summaryLabel.getStyleClass().add("chat-message__tool-summary");
            responseBox.getChildren().add(summaryLabel);
        }

        if (toolResult.getData() != null && !toolResult.getData().isEmpty()) {
            FlowPane dataPane = new FlowPane();
            dataPane.getStyleClass().add("chat-message__tool-data");
            dataPane.setHgap(6);
            dataPane.setVgap(6);
            dataPane.setPadding(new Insets(0, 0, 4, 0));

            for (Map.Entry<String, Object> entry : toolResult.getData().entrySet()) {
                HBox dataItem = new HBox(4);
                dataItem.getStyleClass().add("chat-message__tool-data-item");
                dataItem.setAlignment(Pos.CENTER_LEFT);

                Label keyLabel = new Label(entry.getKey());
                keyLabel.getStyleClass().add("chat-message__tool-data-key");

                Label valueLabel = new Label(String.valueOf(entry.getValue()));
                valueLabel.getStyleClass().add("chat-message__tool-data-value");

                dataItem.getChildren().addAll(keyLabel, valueLabel);
                dataPane.getChildren().add(dataItem);
            }
            responseBox.getChildren().add(dataPane);
        }

        if (toolResult.getRawOutput() != null && !toolResult.getRawOutput().isEmpty()) {
            if (!responseBox.getChildren().isEmpty()) {
                Region divider = new Region();
                divider.getStyleClass().add("chat-message__tool-output-divider");
                responseBox.getChildren().add(divider);
            }

            TextFlow outputFlow = new TextFlow();
            outputFlow.getStyleClass().add("chat-message__tool-output");
            Text outputText = new Text(toolResult.getRawOutput());
            outputText.setFont(Font.font("\"SF Mono\", Monaco, \"Cascadia Code\", monospace", 12));
            outputFlow.getChildren().add(outputText);
            responseBox.getChildren().add(outputFlow);
        }

        if (responseBox.getChildren().size() == 1 && toolResult.getMessage() != null) {
            // 只有标题，没有其他内容时，用 message 填充
            TextFlow msgFlow = new TextFlow();
            msgFlow.getStyleClass().add("chat-message__tool-output");
            Text msgText = new Text(toolResult.getMessage());
            msgText.setFont(Font.font("\"SF Pro Text\", -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif", 13));
            msgFlow.getChildren().add(msgText);
            responseBox.getChildren().add(msgFlow);
        }
    }

    @Override
    public MessageEvent.Type getMessageType() {
        return MessageEvent.Type.TOOL;
    }

    @Override
    public String getContent() {
        return arguments;
    }

    private HBox buildHeader(String toolName, Circle statusDot, Label summaryLabel) {
        HBox header = new HBox(8);
        header.getStyleClass().add("chat-message__tool-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setFocusTraversable(false);

        if (statusDot != null) {
            header.getChildren().add(statusDot);
        }

        Label nameLabel = new Label(toolName);
        nameLabel.getStyleClass().add("chat-message__tool-name");
        nameLabel.setFocusTraversable(false);
        header.getChildren().add(nameLabel);

        if (summaryLabel != null) {
            header.getChildren().add(summaryLabel);
        }

        return header;
    }

    private TextFlow buildJsonContent(String arguments) {
        TextFlow contentFlow = new TextFlow();
        contentFlow.getStyleClass().add("chat-message__tool-output");

        String formattedJson = formatJSON(arguments);
        Text jsonText = new Text(formattedJson);
        jsonText.setFont(Font.font("\"SF Mono\", Monaco, \"Cascadia Code\", monospace", 12));
        contentFlow.getChildren().add(jsonText);
        return contentFlow;
    }

    private void toggleContent(javafx.scene.Node content) {
        boolean expanded = content.isVisible();
        content.setVisible(!expanded);
        content.setManaged(!expanded);
    }

    private String formatJSON(String jsonString) {
        try {
            JsonNode obj = JsonUtils.parse(jsonString);
            return JsonUtils.toPrettyJson(obj);
        } catch (Exception e) {
            return jsonString;
        }
    }
}
