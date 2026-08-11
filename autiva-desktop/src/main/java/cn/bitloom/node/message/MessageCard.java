package cn.bitloom.node.message;

import javafx.scene.layout.VBox;
import org.springframework.ai.chat.messages.MessageType;

/**
 * 消息卡片抽象基类，消除 ChatMessage 中间层。
 * 继承 VBox，可以直接添加到 JavaFX 容器中。
 * 各卡片继承此抽象类，实现 getType() 和 getContent() 方法。
 */
public abstract class MessageCard extends VBox {

    /**
     * 获取消息类型
     */
    public abstract MessageType getMessageType();

    /**
     * 获取消息内容（用于复制按钮等操作）
     */
    public abstract String getContent();

}
