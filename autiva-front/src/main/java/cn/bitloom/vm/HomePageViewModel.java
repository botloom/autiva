package cn.bitloom.vm;

import cn.bitloom.agentic.session.*;
import cn.bitloom.node.ChatMessage;
import cn.bitloom.store.Store;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.*;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class HomePageViewModel {

    private final SessionManager sessionManager;

    @Getter
    private final ObservableList<ChatMessage> messages = FXCollections.observableArrayList();

    private Session session;
    private StringBuilder streamMessage = new StringBuilder();
    private ChatMessage currentStreamingMessage = null;
    private Disposable outBoxSubscription;
    private int historicalMessageOffset = 0;
    private static final int MAX_INITIAL_MESSAGES = 50;

    private void subscribeOutBox() {
        if (this.outBoxSubscription != null) {
            this.outBoxSubscription.dispose();
        }
        this.outBoxSubscription = this.session.getMessageBus().outBoxSubscribe()
                .subscribe(
                        message -> Platform.runLater(() -> this.processMessage(message)),
                        error -> {
                            log.error("Failed to send message", error);
                            Platform.runLater(() -> Store.statusText.set("发送失败: " + error.getMessage()));
                        },
                        () -> {
                            log.info("Message processing completed");
                            Platform.runLater(() -> {
                                if (!Store.isPaused.get()) {
                                    Store.statusText.set("就绪");
                                }
                            });
                        }
                );
    }

    public void createNewSession() {
        this.session = null;
        Store.currentSessionId.set("");
        messages.clear();
        streamMessage = new StringBuilder();
        currentStreamingMessage = null;
        historicalMessageOffset = 0;
        Store.isStreaming.set(false);
        Store.isPaused.set(false);
        if (this.outBoxSubscription != null) {
            this.outBoxSubscription.dispose();
            this.outBoxSubscription = null;
        }
    }

    public void switchToSession(String sessionId) {
        if (this.session != null && sessionId.equals(this.session.getId())) {
            return;
        }

        Session targetSession = sessionManager.getById(sessionId);
        if (targetSession == null) {
            log.warn("切换到不存在的session: {}", sessionId);
            return;
        }

        // 激活会话（设置 EventBus、注入 Agent、加载历史消息、恢复上下文）
        sessionManager.start(sessionId);

        this.session = targetSession;
        Store.currentSessionId.set(sessionId);
        Store.selectedModel.set(targetSession.getModel());
        Store.currentAgent.set(targetSession.getAgentId());

        subscribeOutBox();

        messages.clear();
        streamMessage = new StringBuilder();
        currentStreamingMessage = null;
        historicalMessageOffset = 0;
        Store.isStreaming.set(false);
        Store.isPaused.set(false);

        if (hasHistoricalMessages()) {
            prepareHistoricalMessages();
        }
    }

    /**
     * 切换智能体：切换到初始态，更新当前 agent
     */
    public void switchAgent(String agentId) {
        Store.currentAgent.set(agentId);
        createNewSession(); // 切换到初始态
    }

    public void prepareHistoricalMessages() {
        List<Message> historicalMessages = this.session.getMessages();
        if (historicalMessages.isEmpty()) {
            return;
        }

        // 只加载最近 MAX_INITIAL_MESSAGES 条消息
        int startIndex = Math.max(0, historicalMessages.size() - MAX_INITIAL_MESSAGES);
        historicalMessageOffset = startIndex;
        List<Message> recentMessages = historicalMessages.subList(startIndex, historicalMessages.size());

        // 分批加载历史消息，每批 20 条，分帧渲染避免 UI 冻结
        int batchSize = 20;
        for (int i = 0; i < recentMessages.size(); i += batchSize) {
            int end = Math.min(i + batchSize, recentMessages.size());
            List<Message> batch = recentMessages.subList(i, end);
            if (i == 0) {
                // 第一批立即渲染，减少延迟感
                for (Message msg : batch) {
                    processMessage(msg);
                }
            } else {
                // 后续批次分帧渲染
                List<Message> batchCopy = new ArrayList<>(batch);
                Platform.runLater(() -> {
                    for (Message msg : batchCopy) {
                        processMessage(msg);
                    }
                });
            }
        }
    }

    public List<Message> loadMoreMessages(int count) {
        if (this.session == null || historicalMessageOffset <= 0) return List.of();
        List<Message> allMessages = this.session.getMessages();
        // 边界保护：确保 offset 不超过实际消息数量
        historicalMessageOffset = Math.min(historicalMessageOffset, allMessages.size());
        if (historicalMessageOffset <= 0) return List.of();
        int newOffset = Math.max(0, historicalMessageOffset - count);
        List<Message> olderMessages = new ArrayList<>(allMessages.subList(newOffset, historicalMessageOffset));
        historicalMessageOffset = newOffset;
        return olderMessages;
    }

    public boolean hasMoreMessages() {
        return historicalMessageOffset > 0;
    }

    public boolean hasHistoricalMessages() {
        return this.session != null && !this.session.getMessages().isEmpty();
    }

    private void processMessage(Message msg) {
        // 优先使用类型判断，避免 JSON 转换开销
        if (msg instanceof UserMessage userMsg) {
            streamMessage = new StringBuilder();
            currentStreamingMessage = null;
            ChatMessage chatMsg = new ChatMessage(ChatMessage.Type.USER);
            chatMsg.setContent(userMsg.getText());
            messages.add(chatMsg);
        } else if (msg instanceof AssistantMessage assistantMsg) {
            this.processAssistantMessage(assistantMsg);
        } else if (msg instanceof ToolResponseMessage toolMsg) {
            this.processToolMessage(toolMsg);
        } else {
            // 降级到 JSON 转换（兼容其他类型）
            this.processMessageFallback(msg);
        }
    }

    private void processAssistantMessage(AssistantMessage msg) {
        Map<String, Object> metadata = msg.getMetadata();
        String finishReason = null;
        finishReason = (String) metadata.get("finishReason");
        String text = msg.getText();

        if (finishReason == null || finishReason.isBlank()) {
            if (Store.isPaused.get()) {
                return;
            }
            streamMessage.append(text != null ? text : "");
            String accumulated = streamMessage.toString();
            if (accumulated.isBlank()) {
                return;
            }
            if (currentStreamingMessage == null) {
                currentStreamingMessage = new ChatMessage(ChatMessage.Type.ASSISTANT);
                currentStreamingMessage.setStreaming(true);
                messages.add(currentStreamingMessage);
            }
            currentStreamingMessage.setContent(accumulated);
        } else if ("STOP".equals(finishReason)) {
            Store.isStreaming.set(false);
            Store.isPaused.set(false);
            sessionManager.updateState(this.session.getId(), SessionState.IDLE);
            if (currentStreamingMessage != null) {
                if (currentStreamingMessage.getContent() != null && !currentStreamingMessage.getContent().isBlank()) {
                    currentStreamingMessage.setStreaming(false);
                    currentStreamingMessage.setFinishReason(ChatMessage.FinishReason.STOP);
                } else {
                    messages.remove(currentStreamingMessage);
                }
                currentStreamingMessage = null;
            } else if (text != null && !text.isBlank()) {
                ChatMessage chatMsg = new ChatMessage(ChatMessage.Type.ASSISTANT);
                chatMsg.setContent(text);
                chatMsg.setFinishReason(ChatMessage.FinishReason.STOP);
                messages.add(chatMsg);
            }
            streamMessage = new StringBuilder();
        } else if ("TOOL_CALLS".equals(finishReason)) {
            if (currentStreamingMessage != null) {
                if (currentStreamingMessage.getContent() != null && !currentStreamingMessage.getContent().isBlank()) {
                    currentStreamingMessage.setStreaming(false);
                    currentStreamingMessage.setFinishReason(ChatMessage.FinishReason.TOOL_CALLS);
                } else {
                    messages.remove(currentStreamingMessage);
                }
                currentStreamingMessage = null;
            }

            List<AssistantMessage.ToolCall> toolCalls = msg.getToolCalls();
            for (AssistantMessage.ToolCall tc : toolCalls) {
                ChatMessage toolCallMsg = new ChatMessage(ChatMessage.Type.TOOL);
                toolCallMsg.getToolCalls().add(new ChatMessage.ToolCallInfo(
                        tc.name(), tc.arguments()));
                messages.add(toolCallMsg);
            }
            streamMessage = new StringBuilder();
        }
    }

    private void processToolMessage(ToolResponseMessage msg) {
        List<ToolResponseMessage.ToolResponse> responses = msg.getResponses();
        if (!responses.isEmpty()) {
            ChatMessage toolRespMsg = new ChatMessage(ChatMessage.Type.TOOL);
            for (ToolResponseMessage.ToolResponse resp : responses) {
                toolRespMsg.getResponses().add(new ChatMessage.ToolResponseInfo(
                        resp.name(), resp.responseData()));
            }
            messages.add(toolRespMsg);
        }
    }

    private void processMessageFallback(Message msg) {
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
            this.processAssistantMessageFallback(jsonObject);
        } else if (MessageType.TOOL.name().equals(messageType)) {
            this.processToolMessageFallback(jsonObject);
        }
    }

    private void processAssistantMessageFallback(JSONObject jsonObject) {
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
            if (Store.isPaused.get()) {
                return;
            }
            streamMessage.append(text != null ? text : "");
            String accumulated = streamMessage.toString();
            if (accumulated.isBlank()) {
                return;
            }
            if (currentStreamingMessage == null) {
                currentStreamingMessage = new ChatMessage(ChatMessage.Type.ASSISTANT);
                currentStreamingMessage.setStreaming(true);
                messages.add(currentStreamingMessage);
            }
            currentStreamingMessage.setContent(accumulated);
        } else if ("STOP".equals(finishReason)) {
            Store.isStreaming.set(false);
            Store.isPaused.set(false);
            sessionManager.updateState(this.session.getId(), SessionState.IDLE);
            if (currentStreamingMessage != null) {
                if (currentStreamingMessage.getContent() != null && !currentStreamingMessage.getContent().isBlank()) {
                    currentStreamingMessage.setStreaming(false);
                    currentStreamingMessage.setFinishReason(ChatMessage.FinishReason.STOP);
                } else {
                    messages.remove(currentStreamingMessage);
                }
                currentStreamingMessage = null;
            } else if (text != null && !text.isBlank()) {
                ChatMessage chatMsg = new ChatMessage(ChatMessage.Type.ASSISTANT);
                chatMsg.setContent(text);
                chatMsg.setFinishReason(ChatMessage.FinishReason.STOP);
                messages.add(chatMsg);
            }
            streamMessage = new StringBuilder();
        } else if ("TOOL_CALLS".equals(finishReason)) {
            if (currentStreamingMessage != null) {
                if (currentStreamingMessage.getContent() != null && !currentStreamingMessage.getContent().isBlank()) {
                    currentStreamingMessage.setStreaming(false);
                    currentStreamingMessage.setFinishReason(ChatMessage.FinishReason.TOOL_CALLS);
                } else {
                    messages.remove(currentStreamingMessage);
                }
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

    private void processToolMessageFallback(JSONObject jsonObject) {
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
        if (this.session == null) {
            this.session = sessionManager.create(
                    Store.currentAgent.get(), Store.source.get(), SessionTypeEnum.DM,
                    SessionRespTypeEnum.STREAM, Store.selectedModel.get());
            Store.currentSessionId.set(this.session.getId());
            subscribeOutBox();
        }
        Store.statusText.set("正在处理...");
        Store.isStreaming.set(true);
        Store.isPaused.set(false);
        sessionManager.updateState(this.session.getId(), SessionState.GENERATING);
        sessionManager.publishMessage(this.session.getId(), message);
    }

    public void clear() {
        messages.clear();
        streamMessage = new StringBuilder();
        currentStreamingMessage = null;
        historicalMessageOffset = 0;
        Store.isStreaming.set(false);
        Store.isPaused.set(false);
        if (this.session != null) {
            sessionManager.updateState(this.session.getId(), SessionState.IDLE);
            sessionManager.clearSessionMessages(this.session.getId());
            sessionManager.getChildSessions(this.session.getId())
                    .forEach(child -> sessionManager.deleteSession(child.getId()));
        }
        Store.statusText.set("就绪");
    }

    public void pauseGeneration() {
        if (Store.isStreaming.get() && !Store.isPaused.get()) {
            Store.isPaused.set(true);
            if (this.session != null) {
                sessionManager.stopSession(this.session.getId());
                sessionManager.getChildSessions(this.session.getId())
                        .forEach(child -> sessionManager.stopSession(child.getId()));
                sessionManager.updateState(this.session.getId(), SessionState.PAUSED);
            }

            if (currentStreamingMessage != null) {
                currentStreamingMessage.setStreaming(false);
                currentStreamingMessage = null;
            }
            streamMessage = new StringBuilder();

            Store.statusText.set("已暂停");
        }
    }

}
