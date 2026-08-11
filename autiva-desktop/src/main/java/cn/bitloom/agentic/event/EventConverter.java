package cn.bitloom.agentic.event;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

public class EventConverter {

    public static MessageEvent fromMessage(String sessionId, Message message) {
        return MessageEvent.builder()
                .sessionId(sessionId)
                .message(message)
                .build();
    }

    public static UserMessage toUserMessage(MessageEvent event) {
        if (event.getMessage() instanceof UserMessage um) {
            return um;
        }
        String text = event.getMessage() != null ? event.getMessage().getText() : null;
        return UserMessage.builder().text(text != null ? text : "").build();
    }
}
