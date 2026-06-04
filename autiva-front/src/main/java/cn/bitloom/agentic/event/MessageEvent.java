package cn.bitloom.agentic.event;

import cn.bitloom.agentic.session.MessageChannel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.ai.chat.messages.Message;

@Getter
@Setter
@SuperBuilder
public class MessageEvent extends AbstractEvent {
    public Message message;
    @lombok.Builder.Default
    public EventType eventType = EventType.MESSAGE;
    @lombok.Builder.Default
    public MessageChannel messageChannel = MessageChannel.USER;
}
