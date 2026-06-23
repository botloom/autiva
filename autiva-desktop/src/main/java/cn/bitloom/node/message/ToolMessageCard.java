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

@Getter
public class ToolMessageCard extends MessageCard {

    private final String toolName;
    private final String arguments;
    private final boolean isRequest;

    public ToolMessageCard(String toolName, String arguments, boolean isRequest) {
        this.toolName = toolName;
        this.arguments = arguments;
        this.isRequest = isRequest;

        this.getStyleClass().add("chat-message");
        this.getStyleClass().add("chat-message--tool");
        setFocusTraversable(false);

        if (isRequest) {
            this.getStyleClass().add("chat-message--tool-request");
            buildRequestCard(toolName, arguments);
        } else {
            ToolResult result = ToolResult.fromJson(arguments);
            if (result != null) {
                buildStructuredResponseCard(toolName, result);
            } else {
                this.getStyleClass().add("chat-message--tool-response");
                buildPlainCard(toolName, arguments);
            }
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

    private void buildRequestCard(String toolName, String arguments) {
        HBox header = buildHeader(toolName, null, null);
        this.getChildren().add(header);

        TextFlow contentFlow = buildJsonContent(arguments);
        this.getChildren().add(contentFlow);

        header.setOnMouseClicked(e -> toggleContent(contentFlow));
    }

    private void buildStructuredResponseCard(String toolName, ToolResult result) {
        String statusStyle = switch (result.getStatus()) {
            case SUCCESS -> "chat-message--tool-success";
            case ERROR -> "chat-message--tool-error";
            case WARNING -> "chat-message--tool-warning";
        };
        this.getStyleClass().add(statusStyle);

        Circle statusDot = new Circle(4);
        statusDot.getStyleClass().add("chat-message__tool-status-dot");
        statusDot.getStyleClass().add("chat-message__tool-status-dot--" + result.getStatus().name().toLowerCase());

        Label summaryLabel = null;
        if (result.getMessage() != null && !result.getMessage().isEmpty()) {
            summaryLabel = new Label(result.getMessage());
            summaryLabel.getStyleClass().add("chat-message__tool-summary");
        }

        HBox header = buildHeader(toolName, statusDot, summaryLabel);
        this.getChildren().add(header);

        VBox contentBox = new VBox(8);
        contentBox.getStyleClass().add("chat-message__tool-content");
        contentBox.setVisible(false);
        contentBox.setManaged(false);

        if (result.getData() != null && !result.getData().isEmpty()) {
            FlowPane dataPane = new FlowPane();
            dataPane.getStyleClass().add("chat-message__tool-data");
            dataPane.setHgap(6);
            dataPane.setVgap(6);
            dataPane.setPadding(new Insets(0, 0, 4, 0));

            for (Map.Entry<String, Object> entry : result.getData().entrySet()) {
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
            contentBox.getChildren().add(dataPane);
        }

        if (result.getRawOutput() != null && !result.getRawOutput().isEmpty()) {
            if (!contentBox.getChildren().isEmpty()) {
                Region divider = new Region();
                divider.getStyleClass().add("chat-message__tool-output-divider");
                contentBox.getChildren().add(divider);
            }

            TextFlow outputFlow = new TextFlow();
            outputFlow.getStyleClass().add("chat-message__tool-output");
            Text outputText = new Text(result.getRawOutput());
            outputText.setFont(Font.font("\"SF Mono\", Monaco, \"Cascadia Code\", monospace", 12));
            outputFlow.getChildren().add(outputText);
            contentBox.getChildren().add(outputFlow);
        }

        if (contentBox.getChildren().isEmpty() && result.getMessage() != null) {
            TextFlow msgFlow = new TextFlow();
            msgFlow.getStyleClass().add("chat-message__tool-output");
            Text msgText = new Text(result.getMessage());
            msgText.setFont(Font.font("\"SF Pro Text\", -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif", 13));
            msgFlow.getChildren().add(msgText);
            contentBox.getChildren().add(msgFlow);
        }

        this.getChildren().add(contentBox);

        header.setOnMouseClicked(e -> toggleContent(contentBox));
    }

    private void buildPlainCard(String toolName, String arguments) {
        HBox header = buildHeader(toolName, null, null);
        this.getChildren().add(header);

        TextFlow contentFlow = new TextFlow();
        contentFlow.getStyleClass().add("chat-message__tool-content");
        contentFlow.setVisible(false);
        contentFlow.setManaged(false);

        String formatted = formatJSON(arguments);
        Text text = new Text(formatted);
        text.setFont(Font.font("\"SF Mono\", Monaco, \"Cascadia Code\", monospace", 13));
        contentFlow.getChildren().add(text);
        this.getChildren().add(contentFlow);

        header.setOnMouseClicked(e -> toggleContent(contentFlow));
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
        contentFlow.getStyleClass().add("chat-message__tool-content");
        contentFlow.setVisible(false);
        contentFlow.setManaged(false);

        String formattedJson = formatJSON(arguments);
        Text jsonText = new Text(formattedJson);
        jsonText.setFont(Font.font("\"SF Mono\", Monaco, \"Cascadia Code\", monospace", 13));
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
            try {
                JsonNode obj = JsonUtils.parse(jsonString);
                return JsonUtils.toPrettyJson(obj);
            } catch (Exception e2) {
                return jsonString;
            }
        }
    }
}
