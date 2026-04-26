package cn.bitloom.agentic.event;

import lombok.Builder;
import lombok.Value;
import org.springframework.ai.chat.messages.Message;

import java.time.LocalDateTime;

@Value
@Builder
public class Event {

    @Builder.Default
    LocalDateTime timestamp = LocalDateTime.now();
    String sessionId;
    Message message;

}
