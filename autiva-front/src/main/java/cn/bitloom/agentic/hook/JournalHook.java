package cn.bitloom.agentic.hook;

import cn.bitloom.agentic.memory.JournalManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

import java.util.List;

/**
 * 日记 Hook，在模型调用后自动记录对话摘要到日志。
 */
@Slf4j
public class JournalHook implements AgentHook {

    private static final String HEARTBEAT_OK = "HEARTBEAT_OK";

    private final JournalManager journalManager;

    public JournalHook(JournalManager journalManager) {
        this.journalManager = journalManager;
    }

    @Override
    public int order() {
        return 101; // 在 MemoryConsolidateHook 之后执行
    }

    @Override
    public void afterModelCall(ChatClientRequest request, ChatClientResponse response) {
        try {
            String conversationId = (String) response.context()
                    .get("chat_memory_conversation_id");
            if (conversationId == null) {
                return;
            }

            // 从 response 中获取助手消息
            var chatResponse = response.chatResponse();
            if (chatResponse == null) {
                return;
            }

            StringBuilder summary = new StringBuilder();
            var output = chatResponse.getResult().getOutput();
            String text = output.getText();
            if (text != null && !text.isBlank()) {
                String processed = stripHeartbeatOk(text);
                if (!processed.isBlank()) {
                    summary.append(processed.stripTrailing()).append("\n");
                }
            }

            // 也从请求消息中提取助手历史
            List<Message> messages = request.prompt().getInstructions();
            for (Message msg : messages) {
                if (msg.getMessageType() == MessageType.ASSISTANT) {
                    String msgText = msg.getText();
                    if (msgText != null && !msgText.isBlank()) {
                        String processed = stripHeartbeatOk(msgText);
                        if (!processed.isBlank()) {
                            summary.append(processed.stripTrailing()).append("\n");
                        }
                    }
                }
            }

            if (!summary.isEmpty()) {
                String truncated = summary.length() > 1000
                        ? summary.substring(0, 1000) + "..."
                        : summary.toString();
                journalManager.appendFromSession(conversationId, truncated);
            }
        } catch (Exception e) {
            log.error("[JournalHook] 日记写入失败", e);
        }
    }

    private String stripHeartbeatOk(String text) {
        String stripped = text.strip();
        if (stripped.startsWith(HEARTBEAT_OK)) {
            return stripped.substring(HEARTBEAT_OK.length()).strip();
        }
        if (stripped.endsWith(HEARTBEAT_OK)) {
            return stripped.substring(0, stripped.length() - HEARTBEAT_OK.length()).strip();
        }
        return text;
    }
}
