package cn.bitloom.agentic.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@Jacksonized
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public final class MessageEvent extends AbstractEvent {

    public static final String METADATA_SYNTHETIC = "synthetic";
    public static final String METADATA_COMPACTION_SOURCE = "compactionSource";

    public enum Type {
        USER,
        ASSISTANT,
        TOOL
    }

    @Builder.Default
    private EventTypeEnum eventType = EventTypeEnum.MESSAGE;

    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @Builder.Default
    private Long timestamp = System.currentTimeMillis();

    private String branch;

    @JsonDeserialize(using = MessageDeserializer.class)
    private Message message;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Builder.Default
    private boolean archived = false;

    @Override
    public EventTypeEnum getEventType() { return eventType; }

    public MessageEvent asArchived() {
        if (this.archived) return this;
        return MessageEvent.builder()
            .sessionId(this.getSessionId())
            .eventType(this.getEventType())
            .id(this.getId())
            .timestamp(this.getTimestamp())
            .branch(this.getBranch())
            .message(this.getMessage())
            .metadata(new HashMap<>(this.getMetadata()))
            .archived(true)
            .build();
    }

    @JsonIgnore
    public boolean isSynthetic() {
        Object v = metadata.get(METADATA_SYNTHETIC);
        return v instanceof Boolean b && b;
    }

    @JsonIgnore
    public boolean isRootEvent() { return this.branch == null; }

    @JsonIgnore
    public MessageType getMessageType() { return this.message != null ? this.message.getMessageType() : null; }

    @JsonIgnore
    public boolean hasToolCalls() {
        return this.message instanceof AssistantMessage am && am.hasToolCalls();
    }

    @JsonIgnore
    public boolean isUserMessage() {
        return message != null && message.getMessageType() == MessageType.USER;
    }

    @JsonIgnore
    public String getText() {
        return message != null ? message.getText() : null;
    }

    @JsonIgnore
    public boolean isAssistantMessage() {
        return message != null && message.getMessageType() == MessageType.ASSISTANT;
    }

    @JsonIgnore
    public boolean isToolResponse() {
        return message != null && message.getMessageType() == MessageType.TOOL;
    }

    @JsonIgnore
    public String getMessageId() {
        Object v = metadata.get("messageId");
        return v != null ? v.toString() : null;
    }

    public void setMessageId(String messageId) {
        this.metadata.put("messageId", messageId);
    }

    @JsonIgnore
    public boolean isPersist() {
        Object v = metadata.get("persist");
        return v instanceof Boolean b && b;
    }

    public void setPersist(boolean persist) {
        this.metadata.put("persist", persist);
    }

    @JsonIgnore
    public String getFinishReason() {
        Object v = metadata.get("finishReason");
        return v != null ? v.toString() : null;
    }

    public void setFinishReason(String finishReason) {
        this.metadata.put("finishReason", finishReason);
    }

    @JsonIgnore
    @SuppressWarnings("unchecked")
    public List<ToolCallInfo> getToolCalls() {
        Object v = metadata.get("toolCalls");
        return v instanceof List<?> l ? (List<ToolCallInfo>) l : null;
    }

    public void setToolCalls(List<ToolCallInfo> toolCalls) {
        this.metadata.put("toolCalls", toolCalls);
    }

    @JsonIgnore
    @SuppressWarnings("unchecked")
    public List<ToolResponseInfo> getResponses() {
        Object v = metadata.get("responses");
        return v instanceof List<?> l ? (List<ToolResponseInfo>) l : null;
    }

    public void setResponses(List<ToolResponseInfo> responses) {
        this.metadata.put("responses", responses);
    }

    @JsonIgnore
    @SuppressWarnings("unchecked")
    public List<String> getAttachments() {
        Object v = metadata.get("attachments");
        return v instanceof List<?> l ? (List<String>) l : null;
    }

    public void setAttachments(List<String> attachments) {
        this.metadata.put("attachments", attachments);
    }

    public static MessageEvent userMessage(String sessionId, String text) {
        return MessageEvent.builder()
                .sessionId(sessionId)
                .message(UserMessage.builder().text(text).build())
                .metadata(Map.of("persist", true))
                .build();
    }

    public static MessageEvent userMessage(String messageId, String sessionId, String text) {
        Map<String, Object> md = new HashMap<>();
        md.put("persist", true);
        md.put("messageId", messageId);
        return MessageEvent.builder()
                .sessionId(sessionId)
                .message(UserMessage.builder().text(text).build())
                .metadata(md)
                .build();
    }

    public static MessageEvent assistantStream(String sessionId, String text) {
        Map<String, Object> md = new HashMap<>();
        md.put("persist", false);
        return MessageEvent.builder()
                .sessionId(sessionId)
                .message(AssistantMessage.builder().content(text).build())
                .metadata(md)
                .build();
    }

    public static MessageEvent assistantStop(String sessionId, String text) {
        Map<String, Object> md = new HashMap<>();
        md.put("persist", true);
        md.put("finishReason", "STOP");
        return MessageEvent.builder()
                .sessionId(sessionId)
                .message(AssistantMessage.builder().content(text).properties(Map.of("finishReason", "STOP")).build())
                .metadata(md)
                .build();
    }

    public static MessageEvent assistantToolCalls(String sessionId, List<ToolCallInfo> toolCalls) {
        Map<String, Object> md = new HashMap<>();
        md.put("persist", true);
        md.put("finishReason", "TOOL_CALLS");
        md.put("toolCalls", toolCalls);
        List<AssistantMessage.ToolCall> tcList = toolCalls.stream()
                .map(tc -> new AssistantMessage.ToolCall(tc.id(), "function", tc.name(), tc.arguments()))
                .toList();
        return MessageEvent.builder()
                .sessionId(sessionId)
                .message(AssistantMessage.builder()
                        .toolCalls(tcList)
                        .properties(Map.of("finishReason", "TOOL_CALLS"))
                        .build())
                .metadata(md)
                .build();
    }

    public static MessageEvent toolResponse(String sessionId, List<ToolResponseInfo> responses) {
        Map<String, Object> md = new HashMap<>();
        md.put("persist", true);
        md.put("responses", responses);
        List<ToolResponseMessage.ToolResponse> trList = responses.stream()
                .map(r -> new ToolResponseMessage.ToolResponse(r.id(), r.name(), r.responseData()))
                .toList();
        return MessageEvent.builder()
                .sessionId(sessionId)
                .message(ToolResponseMessage.builder().responses(trList).build())
                .metadata(md)
                .build();
    }

    public record ToolCallInfo(String id, String name, String arguments) {}
    public record ToolResponseInfo(String id, String name, String responseData) {}
}
