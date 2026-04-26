package cn.bitloom.node;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;

public class ChatMessage {

    public enum Type { USER, ASSISTANT, TOOL }

    public enum FinishReason { STOP, TOOL_CALLS }

    @Getter
    private final Type type;
    private final StringProperty content = new SimpleStringProperty("");
    private final ObjectProperty<FinishReason> finishReason = new SimpleObjectProperty<>(null);
    private final BooleanProperty streaming = new SimpleBooleanProperty(false);
    @Getter
    private final ObservableList<ToolCallInfo> toolCalls = FXCollections.observableArrayList();
    @Getter
    private final ObservableList<ToolResponseInfo> responses = FXCollections.observableArrayList();

    public ChatMessage(Type type) {
        this.type = type;
    }

    public StringProperty contentProperty() {
        return content;
    }

    public String getContent() {
        return content.get();
    }

    public void setContent(String value) {
        content.set(value);
    }

    public ObjectProperty<FinishReason> finishReasonProperty() {
        return finishReason;
    }

    public FinishReason getFinishReason() {
        return finishReason.get();
    }

    public void setFinishReason(FinishReason value) {
        finishReason.set(value);
    }

    public BooleanProperty streamingProperty() {
        return streaming;
    }

    public boolean isStreaming() {
        return streaming.get();
    }

    public void setStreaming(boolean value) {
        streaming.set(value);
    }

    public record ToolCallInfo(String name, String arguments) {

    }

    public record ToolResponseInfo(String name, String responseData) {

    }
}
