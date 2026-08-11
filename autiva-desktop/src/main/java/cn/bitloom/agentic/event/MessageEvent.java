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

    @JsonIgnore
    public String getFinishReason() {
        if (message instanceof AssistantMessage am) {
            Object v = am.getMetadata().get("finishReason");
            return v != null ? v.toString() : null;
        }
        return null;
    }

    @JsonIgnore
    public List<ToolCallInfo> getToolCalls() {
        if (message instanceof AssistantMessage am && am.hasToolCalls()) {
            return am.getToolCalls().stream()
                    .map(tc -> new ToolCallInfo(tc.id(), tc.name(), tc.arguments()))
                    .toList();
        }
        return null;
    }

    @JsonIgnore
    public List<ToolResponseInfo> getResponses() {
        if (message instanceof ToolResponseMessage trm) {
            return trm.getResponses().stream()
                    .map(r -> new ToolResponseInfo(r.id(), r.name(), r.responseData()))
                    .toList();
        }
        return null;
    }

    public static MessageEvent userMessage(String sessionId, String text) {
        return MessageEvent.builder()
                .sessionId(sessionId)
                .message(UserMessage.builder().text(text).build())
                .build();
    }

    public static MessageEvent assistantStop(String sessionId, String text) {
        return MessageEvent.builder()
                .sessionId(sessionId)
                .message(AssistantMessage.builder()
                        .content(text)
                        .properties(Map.of("finishReason", "STOP"))
                        .build())
                .build();
    }

    public static MessageEvent toolResponse(String sessionId, List<ToolResponseInfo> responses) {
        List<ToolResponseMessage.ToolResponse> trList = responses.stream()
                .map(r -> new ToolResponseMessage.ToolResponse(r.id(), r.name(), r.responseData()))
                .toList();
        return MessageEvent.builder()
                .sessionId(sessionId)
                .message(ToolResponseMessage.builder().responses(trList).build())
                .build();
    }

    public record ToolCallInfo(String id, String name, String arguments) {}
    public record ToolResponseInfo(String id, String name, String responseData) {}
}
