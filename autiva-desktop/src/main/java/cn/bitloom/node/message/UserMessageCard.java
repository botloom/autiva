package cn.bitloom.node.message;

import org.springframework.ai.chat.messages.MessageType;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import lombok.Getter;

import java.util.function.Consumer;

@Getter
public class UserMessageCard extends MessageCard {

    private final String content;

    /** 撤回回调：由外部（Controller）绑定，触发时撤回本条及之后所有消息 */
    private Consumer<UserMessageCard> onWithdraw;

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

    public void setOnWithdraw(Consumer<UserMessageCard> handler) {
        this.onWithdraw = handler;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.USER;
    }
}
