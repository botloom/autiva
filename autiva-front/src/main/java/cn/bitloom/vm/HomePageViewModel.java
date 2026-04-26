package cn.bitloom.vm;

import cn.bitloom.agentic.event.Event;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.session.SessionManager;
import cn.bitloom.agentic.session.SessionRespTypeEnum;
import cn.bitloom.agentic.session.SessionTypeEnum;
import cn.bitloom.node.ChatMessage;
import cn.bitloom.store.Store;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HomePageViewModel {

    private Session session;
    private final SessionManager sessionManager;
    private static final String SOURCE = "desktopApp";
    private static final String TARGET = "bitloom";

    @Getter
    private final ObservableList<ChatMessage> messages = FXCollections.observableArrayList();

    private StringBuilder streamMessage = new StringBuilder();
    private ChatMessage currentStreamingMessage = null;

    @PostConstruct
    public void init() {
        this.session = sessionManager.getOrCreate(SOURCE, SessionTypeEnum.DM, SessionRespTypeEnum.STREAM, TARGET);
        EventBus.outBoxSubscribe()
                .filter(event -> event.getSessionId().equals(this.session.getId()))
                .map(Event::getMessage)
                .subscribe(
                        message -> Platform.runLater(() -> processMessage(message)),
                        error -> {
                            log.error("Failed to send message", error);
                            Platform.runLater(() -> Store.statusText.set("发送失败: " + error.getMessage()));
                        },
                        () -> {
                            log.info("Message processing completed");
                            Platform.runLater(() -> Store.statusText.set("就绪"));
                        }
                );
    }

    private void processMessage(Message msg) {
        JSONObject jsonObject = (JSONObject) JSON.toJSON(msg);

        String messageType = jsonObject.getString("messageType");
        if (messageType == null) {
            JSONObject metadata = jsonObject.getJSONObject("metadata");
            if (metadata != null) {
                messageType = metadata.getString("messageType");
            }
        }
        if (messageType == null) {
            messageType = msg.getMessageType().name();
        }

        if (MessageType.USER.name().equals(messageType)) {
            streamMessage = new StringBuilder();
            currentStreamingMessage = null;
            ChatMessage chatMsg = new ChatMessage(ChatMessage.Type.USER);
            chatMsg.setContent(jsonObject.getString("text"));
            messages.add(chatMsg);
        } else if (MessageType.ASSISTANT.name().equals(messageType)) {
            processAssistantMessage(jsonObject);
        } else if (MessageType.TOOL.name().equals(messageType)) {
            processToolMessage(jsonObject);
        }
    }

    private void processAssistantMessage(JSONObject jsonObject) {
        JSONObject metadata = jsonObject.getJSONObject("metadata");
        String finishReason = null;
        if (metadata != null) {
            finishReason = metadata.getString("finishReason");
        }
        String text = jsonObject.getString("text");
        if (text == null) {
            text = jsonObject.getString("content");
        }

        if (finishReason == null || finishReason.isBlank()) {
            streamMessage.append(text != null ? text : "");
            if (currentStreamingMessage == null) {
                currentStreamingMessage = new ChatMessage(ChatMessage.Type.ASSISTANT);
                currentStreamingMessage.setStreaming(true);
                messages.add(currentStreamingMessage);
            }
            currentStreamingMessage.setContent(streamMessage.toString());
        } else if ("STOP".equals(finishReason)) {
            if (currentStreamingMessage != null) {
                currentStreamingMessage.setStreaming(false);
                currentStreamingMessage.setFinishReason(ChatMessage.FinishReason.STOP);
                currentStreamingMessage = null;
            } else {
                ChatMessage chatMsg = new ChatMessage(ChatMessage.Type.ASSISTANT);
                chatMsg.setContent(text);
                chatMsg.setFinishReason(ChatMessage.FinishReason.STOP);
                messages.add(chatMsg);
            }
            streamMessage = new StringBuilder();
        } else if ("TOOL_CALLS".equals(finishReason)) {
            if (currentStreamingMessage != null) {
                currentStreamingMessage.setStreaming(false);
                currentStreamingMessage.setFinishReason(ChatMessage.FinishReason.TOOL_CALLS);
                currentStreamingMessage = null;
            }

            JSONArray toolCalls = jsonObject.getJSONArray("toolCalls");
            if (toolCalls != null) {
                for (int i = 0; i < toolCalls.size(); i++) {
                    JSONObject tc = toolCalls.getJSONObject(i);
                    ChatMessage toolCallMsg = new ChatMessage(ChatMessage.Type.TOOL);
                    toolCallMsg.getToolCalls().add(new ChatMessage.ToolCallInfo(
                            tc.getString("name"), tc.getString("arguments")));
                    messages.add(toolCallMsg);
                }
            }
            streamMessage = new StringBuilder();
        }
    }

    private void processToolMessage(JSONObject jsonObject) {
        JSONArray responses = jsonObject.getJSONArray("responses");
        if (responses != null && !responses.isEmpty()) {
            ChatMessage toolRespMsg = new ChatMessage(ChatMessage.Type.TOOL);
            for (int i = 0; i < responses.size(); i++) {
                JSONObject resp = responses.getJSONObject(i);
                toolRespMsg.getResponses().add(new ChatMessage.ToolResponseInfo(
                        resp.getString("name"), resp.getString("responseData")));
            }
            messages.add(toolRespMsg);
        }
    }

    public void prepareHistoricalMessages() {
        List<Message> historicalMessages = this.session.getMessages();
        if (historicalMessages.isEmpty()) {
            return;
        }
        for (Message msg : historicalMessages) {
            processMessage(msg);
        }
    }

    public boolean hasHistoricalMessages() {
        return !this.session.getMessages().isEmpty();
    }

    public void addUserMessage(String text) {
        ChatMessage chatMsg = new ChatMessage(ChatMessage.Type.USER);
        chatMsg.setContent(text);
        messages.add(chatMsg);
    }

    public void sendMessage(UserMessage message) {
        Platform.runLater(() -> Store.statusText.set("正在处理..."));
        EventBus.inBoxPublish(this.session.getId(), message);
        Platform.runLater(() -> Store.statusText.set("就绪"));
    }

    public void clear() {
        messages.clear();
        streamMessage = new StringBuilder();
        currentStreamingMessage = null;
        sessionManager.clearSessionMessages(this.session.getId());
        Platform.runLater(() -> Store.statusText.set("就绪"));
    }
}
