package cn.bitloom.node;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

public class ToolMessageCard extends VBox {

    public ToolMessageCard(String toolName, String arguments, boolean isRequest) {
        this.getStyleClass().add("chat-message");
        this.getStyleClass().add("chat-message--tool");
        this.getStyleClass().add(isRequest ? "chat-message--tool-request" : "chat-message--tool-response");

        HBox header = new HBox();
        header.getStyleClass().add("chat-message__tool-header");

        Label nameLabel = new Label(toolName);
        nameLabel.getStyleClass().add("chat-message__tool-name");
        header.getChildren().add(nameLabel);

        this.getChildren().add(header);

        TextFlow contentFlow = new TextFlow();
        contentFlow.getStyleClass().add("chat-message__tool-content");
        contentFlow.setVisible(false);
        contentFlow.setManaged(false);

        String formattedJson = formatJSON(arguments);
        Text jsonText = new Text(formattedJson);
        jsonText.setFont(Font.font("\"SF Mono\", Monaco, \"Cascadia Code\", monospace", 13));
        contentFlow.getChildren().add(jsonText);
        this.getChildren().add(contentFlow);

        header.setOnMouseClicked(e -> {
            boolean expanded = contentFlow.isVisible();
            contentFlow.setVisible(!expanded);
            contentFlow.setManaged(!expanded);
        });
    }

    private String formatJSON(String jsonString) {
        try {
            JSONObject obj = JSON.parseObject(jsonString);
            return JSON.toJSONString(obj, JSONWriter.Feature.PrettyFormat);
        } catch (Exception e) {
            try {
                Object obj = JSON.parse(jsonString);
                return JSON.toJSONString(obj, JSONWriter.Feature.PrettyFormat);
            } catch (Exception e2) {
                return jsonString;
            }
        }
    }
}
