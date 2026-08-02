package cn.bitloom.node.message;

import cn.bitloom.agentic.event.MessageEvent;
import javafx.scene.Node;

/**
 * 包装任意 Node（如 TaskCard / QuestionCard）作为 MessageCard 加入消息列表，
 * 统一消息区数据源为 ObservableList<MessageCard>。
 * <p>
 * cell factory 通过 instanceof NodeMessageCard 判断，取出 {@link #getNode()} 直接 setGraphic，
 * 不创建 actionBar，视觉上左对齐。
 */
public class NodeMessageCard extends MessageCard {

    private final Node node;

    public NodeMessageCard(Node node) {
        this.node = node;
    }

    public Node getNode() {
        return node;
    }

    @Override
    public MessageEvent.Type getMessageType() {
        // 助手侧内容（TaskCard/QuestionCard），视觉上左对齐
        return MessageEvent.Type.ASSISTANT;
    }

    @Override
    public String getContent() {
        return node != null ? node.toString() : null;
    }
}
