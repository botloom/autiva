package cn.bitloom.node.message;

import cn.bitloom.agentic.tool.ToolResult;
import org.springframework.ai.chat.messages.MessageType;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.geometry.Pos;
import javafx.scene.Node;
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

/**
 * 工具调用卡片。
 * <p>
 * 通过 toolCallId 关联同一个工具调用的请求参数和响应结果。
 * 直接显示工具名、状态点和请求参数（key-value 标签形式）；
 * 接收到响应后仅更新状态点，不展示执行结果。
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

    public ToolMessageCard(String toolCallId, String toolName, String arguments) {
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.arguments = arguments;

        this.getStyleClass().add("chat-message");
        this.getStyleClass().add("chat-message--tool");
        this.getStyleClass().add("chat-message--tool-request");
        setFocusTraversable(false);
        // 卡片宽度跟随 ListView cell，不基于内容自然宽度撑大
        setMaxWidth(Double.MAX_VALUE);

        // 状态点：初始 pending（灰色）
        statusDot = new Circle(4);
        statusDot.getStyleClass().add("chat-message__tool-status-dot");
        statusDot.getStyleClass().add("chat-message__tool-status-dot--pending");

        // header
        HBox header = buildHeader(toolName, statusDot, null);
        header.setMaxWidth(Double.MAX_VALUE);
        this.getChildren().add(header);

        // 内容容器（默认显示请求参数）
        contentBox = new VBox(6);
        contentBox.getStyleClass().add("chat-message__tool-content");
        contentBox.setMaxWidth(Double.MAX_VALUE);

        // 请求参数区块：直接以 key-value 标签形式展示，无标题
        requestBox = new VBox(4);
        requestBox.getStyleClass().add("chat-message__tool-request-box");
        requestBox.setMaxWidth(Double.MAX_VALUE);
        Node paramsNode = buildParamsView(arguments);
        requestBox.getChildren().add(paramsNode);
        contentBox.getChildren().add(requestBox);

        this.getChildren().add(contentBox);
    }

    /**
     * 接收响应结果，仅更新状态点（不再展示执行结果）。
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
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.TOOL;
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

    /**
     * 以 key-value 标签形式展示请求参数。
     * <p>
     * 解析 JSON 参数，每个字段渲染为一个 chip：[key: value]。
     * value 过长时截断显示；非字符串类型（数组/对象/数字/布尔）用 JSON 字符串表示。
     * 解析失败时回退为纯文本展示原始 arguments。
     */
    private Node buildParamsView(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            Label empty = new Label("（无参数）");
            empty.getStyleClass().add("chat-message__tool-param-empty");
            return empty;
        }

        JsonNode params;
        try {
            params = JsonUtils.parse(arguments);
        } catch (Exception e) {
            // 非 JSON 字符串，直接展示文本
            TextFlow rawFlow = new TextFlow();
            rawFlow.getStyleClass().add("chat-message__tool-output");
            rawFlow.setPrefWidth(0);
            rawFlow.setMaxWidth(Double.MAX_VALUE);
            Text rawText = new Text(arguments);
            rawText.setFont(Font.font("\"SF Mono\", Monaco, \"Cascadia Code\", monospace", 12));
            rawFlow.getChildren().add(rawText);
            return rawFlow;
        }

        // 非 object 类型（数组/原始值），直接展示格式化 JSON
        if (!params.isObject()) {
            TextFlow rawFlow = new TextFlow();
            rawFlow.getStyleClass().add("chat-message__tool-output");
            rawFlow.setPrefWidth(0);
            rawFlow.setMaxWidth(Double.MAX_VALUE);
            Text rawText = new Text(JsonUtils.toPrettyJson(params));
            rawText.setFont(Font.font("\"SF Mono\", Monaco, \"Cascadia Code\", monospace", 12));
            rawFlow.getChildren().add(rawText);
            return rawFlow;
        }

        // object 类型：每个字段渲染为 chip
        FlowPane chipPane = new FlowPane();
        chipPane.getStyleClass().add("chat-message__tool-params");
        chipPane.setHgap(6);
        chipPane.setVgap(6);
        chipPane.setMaxWidth(Double.MAX_VALUE);
        chipPane.setPrefWidth(Region.USE_COMPUTED_SIZE);

        ObjectNode obj = (ObjectNode) params;
        obj.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            String value = formatParamValue(entry.getValue());
            chipPane.getChildren().add(buildParamChip(key, value));
        });

        return chipPane;
    }

    private HBox buildParamChip(String key, String value) {
        HBox chip = new HBox(4);
        chip.getStyleClass().add("chat-message__tool-param-chip");
        chip.setAlignment(Pos.CENTER_LEFT);

        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("chat-message__tool-param-key");

        Label sepLabel = new Label(":");
        sepLabel.getStyleClass().add("chat-message__tool-param-sep");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("chat-message__tool-param-value");
        // 限制 value 最大宽度，过长截断为省略号
        valueLabel.setMaxWidth(220);
        valueLabel.setEllipsisString("…");

        chip.getChildren().addAll(keyLabel, sepLabel, valueLabel);
        return chip;
    }

    private String formatParamValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return "null";
        }
        if (value.isTextual()) {
            return value.asText();
        }
        if (value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        if (value.isArray()) {
            int size = value.size();
            if (size == 0) return "[]";
            // 数组只取首项预览，避免过长
            return "[" + formatParamValue(value.get(0)) + (size > 1 ? ", …" : "") + "]";
        }
        if (value.isObject()) {
            int size = value.size();
            return "{…" + (size > 0 ? "(" + size + ")" : "") + "}";
        }
        return value.toString();
    }
}
