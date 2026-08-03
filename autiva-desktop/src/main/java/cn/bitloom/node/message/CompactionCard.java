package cn.bitloom.node.message;

import cn.bitloom.agentic.event.MessageEvent;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/**
 * 压缩提示卡片：在聊天消息展示处渲染为居中的"上下文已压缩"提示，
 * 让用户感知到此处发生了上下文压缩。
 */
public class CompactionCard extends MessageCard {

    public CompactionCard(int archivedCount, int activeCount) {
        Label label = new Label("上下文已压缩（归档 " + archivedCount + " 条，保留 " + activeCount + " 条）");
        label.setStyle("-fx-text-fill: #86868b; -fx-font-size: 12px; -fx-padding: 8 0;");
        HBox box = new HBox(label);
        box.setStyle("-fx-alignment: center; -fx-padding: 4 0;");
        getChildren().add(box);
    }

    @Override
    public MessageEvent.Type getMessageType() {
        // 系统提示，视觉上居中
        return MessageEvent.Type.ASSISTANT;
    }

    @Override
    public String getContent() {
        return "[上下文已压缩]";
    }
}
