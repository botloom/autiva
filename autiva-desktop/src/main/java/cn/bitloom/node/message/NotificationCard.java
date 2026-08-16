package cn.bitloom.node.message;

import org.springframework.ai.chat.messages.MessageType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/**
 * 系统通知卡片：后台任务完成/失败等系统级通知在聊天流中的展示，
 * 视觉上区别于用户消息（居中、灰色小字，与 CompactionCard 同风格）。
 */
public class NotificationCard extends MessageCard {

    public NotificationCard(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #86868b; -fx-font-size: 12px; -fx-padding: 8 0;");
        HBox box = new HBox(label);
        box.setStyle("-fx-alignment: center; -fx-padding: 4 0;");
        getChildren().add(box);
    }

    @Override
    public MessageType getMessageType() {
        // 系统提示，视觉上居中
        return MessageType.ASSISTANT;
    }

    @Override
    public String getContent() {
        return "[系统通知]";
    }
}
