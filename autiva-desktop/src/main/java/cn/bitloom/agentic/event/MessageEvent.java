package cn.bitloom.agentic.event;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@Setter
@SuperBuilder
public final class MessageEvent extends AbstractEvent {

    public enum Type { USER, ASSISTANT, TOOL }

    private Type type;

    // ===== 通用字段 =====
    private String text;

    // ===== 助手消息字段 =====
    /** null=流式片段, "STOP"=完成, "TOOL_CALLS"=工具调用 */
    private String finishReason;
    private List<ToolCallInfo> toolCalls;

    // ===== 工具响应字段 =====
    private List<ToolResponseInfo> responses;

    // ===== 用户消息字段 =====
    private List<String> attachments;

    // ===== 便捷方法 =====
    public boolean isUserMessage() { return type == Type.USER; }
    public boolean isAssistantMessage() { return type == Type.ASSISTANT; }
    public boolean isToolResponse() { return type == Type.TOOL; }

    // ===== 静态工厂 =====
    public static MessageEvent userMessage(String sessionId, String text) {
        return MessageEvent.builder().sessionId(sessionId).type(Type.USER).text(text).build();
    }

    public static MessageEvent assistantStream(String sessionId, String text) {
        return MessageEvent.builder().sessionId(sessionId).type(Type.ASSISTANT).text(text).build();
    }

    public static MessageEvent assistantStop(String sessionId, String text) {
        return MessageEvent.builder().sessionId(sessionId).type(Type.ASSISTANT).text(text)
                .finishReason("STOP").build();
    }

    public static MessageEvent assistantToolCalls(String sessionId, List<ToolCallInfo> toolCalls) {
        return MessageEvent.builder().sessionId(sessionId).type(Type.ASSISTANT)
                .finishReason("TOOL_CALLS").toolCalls(toolCalls).build();
    }

    public static MessageEvent toolResponse(String sessionId, List<ToolResponseInfo> responses) {
        return MessageEvent.builder().sessionId(sessionId).type(Type.TOOL).responses(responses).build();
    }

    // ===== 内部 record =====
    public record ToolCallInfo(String name, String arguments) {}
    public record ToolResponseInfo(String name, String responseData) {}
}
