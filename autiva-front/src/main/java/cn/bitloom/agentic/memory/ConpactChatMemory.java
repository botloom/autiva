package cn.bitloom.agentic.memory;

import cn.bitloom.agentic.session.Session;
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
        // 清理前一条孤儿工具调用消息：若上一条消息是含 tool_calls 的 AssistantMessage，
        // 且本次追加的不是 ToolResponseMessage，说明工具调用已无响应（异常中断），需要移除。
        // DeepSeek 等模型严格要求 AssistantMessage(tool_calls) 后必须紧跟 ToolResponseMessage。
        if (!messages.isEmpty()
                && messages.stream().noneMatch(msg -> msg instanceof ToolResponseMessage)) {
            var session = sessionManager.getById(conversationId);
            if (session != null) {
                var sessionMessages = session.getMessages();
                if (!sessionMessages.isEmpty()) {
                    var lastMsg = sessionMessages.get(sessionMessages.size() - 1);
                    if (lastMsg instanceof AssistantMessage lastAssistant && !lastAssistant.getToolCalls().isEmpty()) {
                        sessionMessages.remove(sessionMessages.size() - 1);
                    }
                }
            }
        }
        sessionManager.appendMessage(conversationId, messages);
    }

    @NonNull
    @Override
    public List<Message> get(@NonNull String conversationId) {
        Session session = sessionManager.getById(conversationId);
        if (session == null) {
            return List.of();
        }
        // 延迟加载：如果消息未加载，先从磁盘加载
        if (session.getMessages().isEmpty()) {
            sessionManager.loadMessages(conversationId);
        }
        return session.getMessages();
    }

    @Override
    public void clear(@NonNull String conversationId) {
        var session = sessionManager.getById(conversationId);
        if (session != null) {
            session.getMessages().clear();
        }
    }
}
