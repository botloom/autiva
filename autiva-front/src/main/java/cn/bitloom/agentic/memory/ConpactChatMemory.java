package cn.bitloom.agentic.memory;

import cn.bitloom.agentic.session.MessageChannel;
import cn.bitloom.agentic.session.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConpactChatMemory implements ChatMemory {

    private final SessionManager sessionManager;

    @Override
    public void add(@NonNull String conversationId, @NonNull List<Message> messages) {
        ParsedConversationId parsed = parseConversationId(conversationId);
        // 清理前一条孤儿工具调用消息：若上一条消息是含 tool_calls 的 AssistantMessage，
        // 且本次追加的不是 ToolResponseMessage，说明工具调用已无响应（异常中断），需要移除。
        // DeepSeek 等模型严格要求 AssistantMessage(tool_calls) 后必须紧跟 ToolResponseMessage。
        if (!messages.isEmpty()
                && messages.stream().noneMatch(msg -> msg instanceof ToolResponseMessage)) {
            var session = sessionManager.getById(parsed.sessionId);
            if (session != null) {
                var channelMessages = session.getChannelMessages(parsed.channel);
                if (!channelMessages.isEmpty()) {
                    var lastMsg = channelMessages.get(channelMessages.size() - 1);
                    if (lastMsg instanceof AssistantMessage lastAssistant
                            && lastAssistant.getToolCalls() != null
                            && !lastAssistant.getToolCalls().isEmpty()) {
                        channelMessages.remove(channelMessages.size() - 1);
                    }
                }
            }
        }
        sessionManager.appendMessage(parsed.sessionId, parsed.channel, messages);
    }

    @NonNull
    @Override
    public List<Message> get(@NonNull String conversationId) {
        ParsedConversationId parsed = parseConversationId(conversationId);
        var session = sessionManager.getById(parsed.sessionId);
        if (session == null) {
            return List.of();
        }
        return session.getChannelMessages(parsed.channel);
    }

    @Override
    public void clear(@NonNull String conversationId) {
        ParsedConversationId parsed = parseConversationId(conversationId);
        var session = sessionManager.getById(parsed.sessionId);
        if (session != null) {
            session.getChannelMessages(parsed.channel).clear();
        }
    }

    private ParsedConversationId parseConversationId(String conversationId) {
        int separatorIndex = conversationId.indexOf('#');
        if (separatorIndex >= 0) {
            String sessionId = conversationId.substring(0, separatorIndex);
            String channelName = conversationId.substring(separatorIndex + 1);
            try {
                MessageChannel channel = MessageChannel.valueOf(channelName);
                return new ParsedConversationId(sessionId, channel);
            } catch (IllegalArgumentException e) {
                log.warn("[ConpactChatMemory] 未知的消息通道: {}, 使用默认USER通道", channelName);
                return new ParsedConversationId(sessionId, MessageChannel.USER);
            }
        }
        return new ParsedConversationId(conversationId, MessageChannel.USER);
    }

    private record ParsedConversationId(String sessionId, MessageChannel channel) {
    }
}
