package cn.bitloom.vm;

import cn.bitloom.agentic.agent.AgentIdentityEnum;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.session.SessionManager;
import cn.bitloom.agentic.session.SessionRespTypeEnum;
import cn.bitloom.agentic.session.SessionState;
import cn.bitloom.agentic.session.SessionTypeEnum;
import cn.bitloom.node.ChatMessage;
import cn.bitloom.store.Store;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HomePageViewModel {

    private final SessionManager sessionManager;
    private static final String SOURCE = "desktopApp";
    private static final String TARGET = "bitloom";

    @Getter
    private final ObservableList<ChatMessage> messages = FXCollections.observableArrayList();

    @Getter
    private final BooleanProperty isStreaming = new SimpleBooleanProperty(false);

    @Getter
    private final BooleanProperty isPaused = new SimpleBooleanProperty(false);

    @Getter
    private ObjectProperty<ModelTypeEnum> modelProperty = new SimpleObjectProperty<>(ModelTypeEnum.DEEPSEEK);

    @Getter
    private final StringProperty currentSessionId = new SimpleStringProperty();

    @Getter
    private final ObjectProperty<AgentIdentityEnum> agentProperty = new SimpleObjectProperty<>(AgentIdentityEnum.MAIN);

    private Session session;
    private StringBuilder streamMessage = new StringBuilder();
    private ChatMessage currentStreamingMessage = null;
    private Disposable outBoxSubscription;

    @PostConstruct
    public void init() {
        this.session = sessionManager.getOrCreate(AgentIdentityEnum.MAIN, SOURCE, SessionTypeEnum.DM, SessionRespTypeEnum.STREAM, this.modelProperty.get(), TARGET);
        this.currentSessionId.set(this.session.getId());
        subscribeOutBox();

        if (this.hasHistoricalMessages()) {
            this.prepareHistoricalMessages();
        }
    }

    private void subscribeOutBox() {
        if (this.outBoxSubscription != null) {
            this.outBoxSubscription.dispose();
        }
        this.outBoxSubscription = EventBus.outBoxSubscribe()
                .filter(event -> event.getSessionId().equals(this.session.getId()))
                .map(MessageEvent::getMessage)
                .subscribe(
                        message -> Platform.runLater(() -> this.processMessage(message)),
                        error -> {
                            log.error("Failed to send message", error);
                            Platform.runLater(() -> Store.statusText.set("发送失败: " + error.getMessage()));
                        },
                        () -> {
                            log.info("Message processing completed");
                            Platform.runLater(() -> {
                                if (!isPaused.get()) {
                                    Store.statusText.set("就绪");
                                }
                            });
                        }
                );
    }

    public boolean createNewSession() {
        if (!this.session.getMessages().isEmpty()) {
            String newTarget = TARGET + "-" + System.currentTimeMillis();
            Session newSession = sessionManager.getOrCreate(
                    this.agentProperty.get(), SOURCE, SessionTypeEnum.DM,
                    SessionRespTypeEnum.STREAM, this.modelProperty.get(), newTarget);
            switchToSession(newSession.getId());
            return true;
        }
        return false;
    }

    public void switchToSession(String sessionId) {
        if (sessionId.equals(this.session.getId())) {
            return;
        }

        Session targetSession = sessionManager.getById(sessionId);
        if (targetSession == null) {
            log.warn("切换到不存在的session: {}", sessionId);
            return;
        }

        this.session = targetSession;
        this.currentSessionId.set(sessionId);
        this.modelProperty.set(targetSession.getModel());

        subscribeOutBox();

        messages.clear();
        streamMessage = new StringBuilder();
        currentStreamingMessage = null;
        isStreaming.set(false);
        isPaused.set(false);

        if (hasHistoricalMessages()) {
            prepareHistoricalMessages();
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
            this.processAssistantMessage(jsonObject);
        } else if (MessageType.TOOL.name().equals(messageType)) {
            this.processToolMessage(jsonObject);
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
            if (isPaused.get()) {
                return;
            }
            streamMessage.append(text != null ? text : "");
            if (currentStreamingMessage == null) {
                currentStreamingMessage = new ChatMessage(ChatMessage.Type.ASSISTANT);
                currentStreamingMessage.setStreaming(true);
                messages.add(currentStreamingMessage);
            }
            currentStreamingMessage.setContent(streamMessage.toString());
        } else if ("STOP".equals(finishReason)) {
            isStreaming.set(false);
            isPaused.set(false);
            sessionManager.updateState(this.session.getId(), SessionState.IDLE);
            if (currentStreamingMessage != null) {
                currentStreamingMessage.setStreaming(false);
                currentStreamingMessage.setFinishReason(ChatMessage.FinishReason.STOP);
                currentStreamingMessage = null;
            } else {
                if (StringUtils.isNotBlank(text)){
                    ChatMessage chatMsg = new ChatMessage(ChatMessage.Type.ASSISTANT);
                    chatMsg.setContent(text);
                    chatMsg.setFinishReason(ChatMessage.FinishReason.STOP);
                    messages.add(chatMsg);
                }
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

    public void addUserMessage(String text, List<String> attachments) {
        ChatMessage chatMsg = new ChatMessage(ChatMessage.Type.USER);
        chatMsg.setContent(text);
        if (attachments != null && !attachments.isEmpty()) {
            chatMsg.getAttachments().addAll(attachments);
        }
        messages.add(chatMsg);
    }

    public void sendMessage(UserMessage message) {
        Platform.runLater(() -> Store.statusText.set("正在处理..."));
        isStreaming.set(true);
        isPaused.set(false);
        this.session.setModel(this.modelProperty.get());
        sessionManager.updateState(this.session.getId(), SessionState.GENERATING);
        EventBus.inBoxPublish(this.session.getId(), message);
    }

    public void clear() {
        messages.clear();
        streamMessage = new StringBuilder();
        currentStreamingMessage = null;
        isStreaming.set(false);
        isPaused.set(false);
        sessionManager.updateState(this.session.getId(), SessionState.IDLE);
        sessionManager.clearSessionMessages(this.session.getId());
        sessionManager.getChildSessions(this.session.getId())
                .forEach(child -> sessionManager.deleteSession(child.getId()));
        Platform.runLater(() -> Store.statusText.set("就绪"));
    }

    public void pauseGeneration() {
        if (isStreaming.get() && !isPaused.get()) {
            isPaused.set(true);
            EventBus.stop(this.session.getId());
            sessionManager.getChildSessions(this.session.getId())
                    .forEach(child -> EventBus.stop(child.getId()));
            sessionManager.updateState(this.session.getId(), SessionState.PAUSED);

            if (currentStreamingMessage != null) {
                currentStreamingMessage.setStreaming(false);
                currentStreamingMessage = null;
            }
            streamMessage = new StringBuilder();

            Platform.runLater(() -> Store.statusText.set("已暂停"));
        }
    }

    public void stopGeneration() {
        if (isStreaming.get()) {
            isStreaming.set(false);
            isPaused.set(false);
            EventBus.stop(this.session.getId());
            sessionManager.getChildSessions(this.session.getId())
                    .forEach(child -> EventBus.stop(child.getId()));
            sessionManager.updateState(this.session.getId(), SessionState.IDLE);

            if (currentStreamingMessage != null) {
                currentStreamingMessage.setStreaming(false);
                currentStreamingMessage.setFinishReason(ChatMessage.FinishReason.STOP);
                currentStreamingMessage = null;
            }
            streamMessage = new StringBuilder();

            Platform.runLater(() -> Store.statusText.set("已停止"));
        }
    }
}
