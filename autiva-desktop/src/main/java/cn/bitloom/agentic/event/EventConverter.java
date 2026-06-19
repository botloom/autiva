package cn.bitloom.agentic.event;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

/**
 * Spring AI Message ↔ MessageEvent 转换器。
 * 这是唯一同时依赖 Spring AI 和事件类型的类。
 */
public class EventConverter {

    /**
     * Spring AI Message → MessageEvent
     */
    public static MessageEvent fromMessage(String sessionId, Message message) {
        if (message instanceof UserMessage um) {
            return MessageEvent.userMessage(sessionId, um.getText());
        } else if (message instanceof AssistantMessage am) {
            Object finishReasonObj = am.getMetadata().get("finishReason");
            String finishReason = finishReasonObj != null ? finishReasonObj.toString() : null;
            if ("TOOL_CALLS".equals(finishReason)) {
                List<MessageEvent.ToolCallInfo> toolCalls = am.getToolCalls().stream()
                .map(tc -> new MessageEvent.ToolCallInfo(tc.name(), tc.arguments()))
                .toList();
                return MessageEvent.assistantToolCalls(sessionId, toolCalls);
            } else if ("STOP".equals(finishReason)) {
                return MessageEvent.assistantStop(sessionId, am.getText());
            } else {
                return MessageEvent.assistantStream(sessionId, am.getText());
            }
        } else if (message instanceof ToolResponseMessage trm) {
            List<MessageEvent.ToolResponseInfo> responses = trm.getResponses().stream()
                    .map(r -> new MessageEvent.ToolResponseInfo(r.name(), r.responseData()))
                    .toList();
            return MessageEvent.toolResponse(sessionId, responses);
        }
        throw new IllegalArgumentException("Unknown message type: " + message.getClass());
    }

    /**
     * MessageEvent → Spring AI UserMessage（Agent 输入用）
     */
    public static UserMessage toUserMessage(MessageEvent event) {
        return UserMessage.builder().text(event.getText()).build();
    }

    /**
     * 批量转换历史消息
     */
    public static List<MessageEvent> fromMessages(String sessionId, List<Message> messages) {
        return messages.stream().map(m -> fromMessage(sessionId, m)).toList();
    }
}
