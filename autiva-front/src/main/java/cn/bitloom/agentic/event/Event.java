package cn.bitloom.agentic.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.messages.Message;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    private String sessionId;
    private Message message;
    private Map<String, Object> metadata;

}
