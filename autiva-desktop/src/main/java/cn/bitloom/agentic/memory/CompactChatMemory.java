package cn.bitloom.agentic.memory;

import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.agentic.session.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompactChatMemory implements ChatMemory {

    private final FileSystemSessionManager fileSystemSessionManager;

    @Override
    public void add(@NonNull String sessionId, @NonNull List<Message> messages) {
        if (messages.isEmpty()) {
            return;
        }
        /*
         * 清理前一条孤儿工具调用消息：若上一条消息是含 tool_calls 的 AssistantMessage，
         * 且本次追加的不是 ToolResponseMessage，说明工具调用已无响应（异常中断），需要移除。
         8 DeepSeek 等模型严格要求 AssistantMessage(tool_calls) 后必须紧跟 ToolResponseMessage。
         */
        Session session = fileSystemSessionManager.getById(sessionId);
        if (Objects.isNull(session)) {
            return;
        }
        if (messages.stream().noneMatch(msg -> msg instanceof ToolResponseMessage)) {
            List<Message> sessionMessages = session.getMessages();
            if (!sessionMessages.isEmpty()) {
                Message lastMsg = sessionMessages.getLast();
                if (lastMsg instanceof AssistantMessage lastAssistant && !lastAssistant.getToolCalls().isEmpty()) {
                    sessionMessages.removeLast();
                }
            }
        }

        fileSystemSessionManager.store(sessionId, messages);
        // 不再估算字符长度，currentContextLength 由 UsageAdvisor 从真实 token 计数更新
    }

    @NonNull
    @Override
    public List<Message> get(@NonNull String sessionId) {
        Session session = fileSystemSessionManager.getById(sessionId);
        if (Objects.isNull(session)) {
            return List.of();
        }
        List<Message> messages = session.getMessages();
        int memoryCursor = session.getMemoryCursor();
        return messages.subList(memoryCursor, messages.size());
    }

    @Override
    public void clear(@NonNull String sessionId) {
        fileSystemSessionManager.clear(sessionId);
    }
}
