package cn.bitloom.agentic.event;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventConverter {

    public static MessageEvent fromMessage(String sessionId, Message message) {
        Map<String, Object> eventMetadata = new HashMap<>();
        eventMetadata.put("persist", true);

        if (message instanceof UserMessage um) {
            return MessageEvent.builder()
                    .sessionId(sessionId)
                    .message(message)
                    .metadata(eventMetadata)
                    .build();
        } else if (message instanceof AssistantMessage am) {
            Object finishReasonObj = am.getMetadata().get("finishReason");
            String finishReason = finishReasonObj != null ? finishReasonObj.toString() : null;
            if ("TOOL_CALLS".equals(finishReason)) {
                List<MessageEvent.ToolCallInfo> toolCalls = am.getToolCalls().stream()
                        .map(tc -> new MessageEvent.ToolCallInfo(tc.id(), tc.name(), tc.arguments()))
                        .toList();
                eventMetadata.put("finishReason", "TOOL_CALLS");
                eventMetadata.put("toolCalls", toolCalls);
                return MessageEvent.builder()
                        .sessionId(sessionId)
                        .message(message)
                        .metadata(eventMetadata)
                        .build();
            } else if ("STOP".equals(finishReason)) {
                eventMetadata.put("finishReason", "STOP");
                return MessageEvent.builder()
                        .sessionId(sessionId)
                        .message(message)
                        .metadata(eventMetadata)
                        .build();
            } else {
                return MessageEvent.builder()
                        .sessionId(sessionId)
                        .message(message)
                        .metadata(eventMetadata)
                        .build();
            }
        } else if (message instanceof ToolResponseMessage trm) {
            List<MessageEvent.ToolResponseInfo> responses = trm.getResponses().stream()
                    .map(r -> new MessageEvent.ToolResponseInfo(r.id(), r.name(), r.responseData()))
                    .toList();
            eventMetadata.put("responses", responses);
            return MessageEvent.builder()
                    .sessionId(sessionId)
                    .message(message)
                    .metadata(eventMetadata)
                    .build();
        }
        return MessageEvent.builder()
                .sessionId(sessionId)
                .message(message)
                .metadata(eventMetadata)
                .build();
    }

    public static Message toMessage(MessageEvent event) {
        return event.getMessage();
    }

    public static UserMessage toUserMessage(MessageEvent event) {
        if (event.getMessage() instanceof UserMessage um) {
            return um;
        }
        String text = event.getMessage() != null ? event.getMessage().getText() : null;
        return UserMessage.builder().text(text != null ? text : "").build();
    }

    public static List<MessageEvent> fromMessages(String sessionId, List<Message> messages) {
        return messages.stream().map(m -> fromMessage(sessionId, m)).toList();
    }

    public static List<Message> toMessages(List<MessageEvent> events) {
        return events.stream().map(EventConverter::toMessage).toList();
    }
}
