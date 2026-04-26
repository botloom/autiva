package cn.bitloom.node;

import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

public class UserMessageCard extends VBox {

    public UserMessageCard(String content) {
        this.getStyleClass().add("chat-message");
        this.getStyleClass().add("chat-message--user");

        TextFlow textFlow = new TextFlow();
        textFlow.getStyleClass().add("chat-message__content");
        Text text = new Text(content != null ? content.trim() : "");
        text.setFont(Font.font("\"SF Pro Text\", -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif", 15));
        textFlow.getChildren().add(text);
        this.getChildren().add(textFlow);
    }
}
