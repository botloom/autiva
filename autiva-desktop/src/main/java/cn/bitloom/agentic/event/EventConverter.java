package cn.bitloom.agentic.event;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;

/**
 * Spring AI Message ↔ MessageEvent 转换器。
 * 这是唯一同时依赖 Spring AI 和事件类型的类。
 */
public class EventConverter {

    /**
     * Spring AI Message → MessageEvent（保留模型生成的工具调用 id）
     */
    public static MessageEvent fromMessage(String sessionId, Message message) {
        if (message instanceof UserMessage um) {
            return MessageEvent.userMessage(sessionId, um.getText());
        } else if (message instanceof AssistantMessage am) {
            Object finishReasonObj = am.getMetadata().get("finishReason");
            String finishReason = finishReasonObj != null ? finishReasonObj.toString() : null;
            if ("TOOL_CALLS".equals(finishReason)) {
                List<MessageEvent.ToolCallInfo> toolCalls = am.getToolCalls().stream()
                .map(tc -> new MessageEvent.ToolCallInfo(tc.id(), tc.name(), tc.arguments()))
                .toList();
                return MessageEvent.assistantToolCalls(sessionId, toolCalls);
            } else if ("STOP".equals(finishReason)) {
                return MessageEvent.assistantStop(sessionId, am.getText());
            } else {
                return MessageEvent.assistantStream(sessionId, am.getText());
            }
        } else if (message instanceof ToolResponseMessage trm) {
            List<MessageEvent.ToolResponseInfo> responses = trm.getResponses().stream()
                    .map(r -> new MessageEvent.ToolResponseInfo(r.id(), r.name(), r.responseData()))
                    .toList();
            return MessageEvent.toolResponse(sessionId, responses);
        }
        throw new IllegalArgumentException("Unknown message type: " + message.getClass());
    }

    /**
     * MessageEvent → Spring AI Message（按 type 分发，供 LLM 上下文加载）
     */
    public static Message toMessage(MessageEvent event) {
        switch (event.getType()) {
            case USER -> {
                return UserMessage.builder().text(event.getText()).build();
            }
            case ASSISTANT -> {
                Map<String, Object> metadata = event.getFinishReason() != null
                        ? Map.of("finishReason", event.getFinishReason())
                        : Map.of();
                if ("TOOL_CALLS".equals(event.getFinishReason()) && event.getToolCalls() != null) {
                    List<AssistantMessage.ToolCall> toolCalls = event.getToolCalls().stream()
                            .map(tc -> new AssistantMessage.ToolCall(tc.id(), "function", tc.name(), tc.arguments()))
                            .toList();
                    return AssistantMessage.builder()
                            .content(event.getText())
                            .properties(metadata)
                            .toolCalls(toolCalls)
                            .build();
                }
                return AssistantMessage.builder()
                        .content(event.getText())
                        .properties(metadata)
                        .build();
            }
            case TOOL -> {
                List<ToolResponseMessage.ToolResponse> responses = event.getResponses().stream()
                        .map(r -> new ToolResponseMessage.ToolResponse(r.id(), r.name(), r.responseData()))
                        .toList();
                return ToolResponseMessage.builder()
                        .responses(responses)
                        .build();
            }
        }
        throw new IllegalArgumentException("Unknown event type: " + event.getType());
    }

    /**
     * MessageEvent → Spring AI UserMessage（Agent 输入用）
     */
    public static UserMessage toUserMessage(MessageEvent event) {
        return UserMessage.builder().text(event.getText()).build();
    }

    /**
     * 批量转换：Spring AI Messages → MessageEvents
     */
    public static List<MessageEvent> fromMessages(String sessionId, List<Message> messages) {
        return messages.stream().map(m -> fromMessage(sessionId, m)).toList();
    }

    /**
     * 批量转换：MessageEvents → Spring AI Messages（供 LLM 上下文加载）
     */
    public static List<Message> toMessages(List<MessageEvent> events) {
        return events.stream().map(EventConverter::toMessage).toList();
    }
}
