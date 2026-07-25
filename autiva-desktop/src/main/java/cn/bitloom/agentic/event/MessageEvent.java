package cn.bitloom.agentic.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Getter
@Setter
@SuperBuilder
@Jacksonized
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public final class MessageEvent extends AbstractEvent {

    public enum Type { USER, ASSISTANT, TOOL }

    @Builder.Default
    private EventTypeEnum eventType = EventTypeEnum.MESSAGE;

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

    @Override
    public EventTypeEnum getEventType() { return eventType; }

    // ===== 便捷方法 =====
    @JsonIgnore
    public boolean isUserMessage() { return type == Type.USER; }
    @JsonIgnore
    public boolean isAssistantMessage() { return type == Type.ASSISTANT; }
    @JsonIgnore
    public boolean isToolResponse() { return type == Type.TOOL; }

    // ===== 静态工厂 =====
    public static MessageEvent userMessage(String sessionId, String text) {
        return MessageEvent.builder().sessionId(sessionId).type(Type.USER).text(text).persist(true).build();
    }

    public static MessageEvent userMessage(String messageId, String sessionId, String text) {
        return MessageEvent.builder().messageId(messageId).sessionId(sessionId).type(Type.USER).text(text).persist(true).build();
    }

    public static MessageEvent assistantStream(String sessionId, String text) {
        return MessageEvent.builder().sessionId(sessionId).type(Type.ASSISTANT).text(text).persist(false).build();
    }

    public static MessageEvent assistantStop(String sessionId, String text) {
        return MessageEvent.builder().sessionId(sessionId).type(Type.ASSISTANT).text(text)
                .finishReason("STOP").persist(true).build();
    }

    public static MessageEvent assistantToolCalls(String sessionId, List<ToolCallInfo> toolCalls) {
        return MessageEvent.builder().sessionId(sessionId).type(Type.ASSISTANT)
                .finishReason("TOOL_CALLS").toolCalls(toolCalls).persist(true).build();
    }

    public static MessageEvent toolResponse(String sessionId, List<ToolResponseInfo> responses) {
        return MessageEvent.builder().sessionId(sessionId).type(Type.TOOL).responses(responses).persist(true).build();
    }

    // ===== 内部 record =====
    /**
     * 工具调用信息。id 为 LLM 返回的 tool_call_id，必须与 ToolResponseInfo.id 配对。
     */
    public record ToolCallInfo(String id, String name, String arguments) {}
    /**
     * 工具响应信息。id 对应 ToolCallInfo.id，必须配对。
     */
    public record ToolResponseInfo(String id, String name, String responseData) {}
}
