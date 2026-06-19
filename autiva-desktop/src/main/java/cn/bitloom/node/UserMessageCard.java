package cn.bitloom.node;

import cn.bitloom.agentic.event.MessageEvent;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import lombok.Getter;

@Getter
public class UserMessageCard extends MessageCard {

    private final String content;

    public UserMessageCard(String content) {
        this.content = content != null ? content.trim() : "";

        this.getStyleClass().add("chat-message");
        this.getStyleClass().add("chat-message--user");

        TextFlow textFlow = new TextFlow();
        textFlow.getStyleClass().add("chat-message__content");
        Text text = new Text(this.content);
        text.setFont(Font.font("\"SF Pro Text\", -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif", 15));
        textFlow.getChildren().add(text);
        this.getChildren().add(textFlow);
    }

    @Override
    public MessageEvent.Type getMessageType() {
        return MessageEvent.Type.USER;
    }
}
