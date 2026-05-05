package cn.bitloom.agentic.memory;

import cn.bitloom.agentic.session.SessionManager;
import cn.bitloom.constant.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConpactChatMemory implements ChatMemory {

    private static final int KEEP_RECENT = 3;
    private static final int TOKEN_THRESHOLD = 8000;

    private final SessionManager sessionManager;

    @Override
    public void add(@NonNull String conversationId, @NonNull List<Message> messages) {
        sessionManager.appendMessage(conversationId, messages);
    }

    @NonNull
    @Override
    public List<Message> get(@NonNull String conversationId) {
        var session = sessionManager.getById(conversationId);
        if (session == null) {
            return List.of();
        }
        List<Message> messages = session.getMessages();

        if (messages.isEmpty()) {
            return messages;
        }

        List<Message> compacted = this.microCompact(messages);

        int tokenCount = estimateTokenCount(compacted);
        if (tokenCount > TOKEN_THRESHOLD) {
            compacted = this.autoCompact(conversationId, compacted);
        }

        return compacted;
    }

    @Override
    public void clear(@NonNull String conversationId) {
        var session = sessionManager.getById(conversationId);
        if (session != null) {
            session.getMessages().clear();
        }
    }

    private List<Message> microCompact(List<Message> messages) {
        long toolMessageCount = messages.stream()
                .filter(message -> message.getMessageType().equals(MessageType.TOOL))
                .count();

        if (toolMessageCount <= KEEP_RECENT) {
            return messages;
        }

        List<Message> result = new ArrayList<>();
        int currentIndex = 0;
        int keepStartIndex = Math.toIntExact(toolMessageCount - KEEP_RECENT);

        for (Message msg : messages) {
            Message compactedMsg;
            if (!msg.getMessageType().equals(MessageType.TOOL) || currentIndex >= keepStartIndex) {
                compactedMsg = msg;
            } else {
                String content = msg.getText();
                String toolName = extractToolName(content);
                String compactedContent = "[Previous tool result from: " + toolName + "]";
                compactedMsg = new UserMessage(compactedContent);
            }

            result.add(compactedMsg);

            if (msg.getMessageType().equals(MessageType.TOOL)) {
                currentIndex++;
            }
        }

        return result;
    }

    private List<Message> autoCompact(String conversationId, List<Message> messages) {
        try {
            Path transcriptDir = AppConstants.Base.LOGS_DIR.resolve("transcripts");
            Files.createDirectories(transcriptDir);

            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            Path transcriptPath = transcriptDir.resolve("transcript_" + conversationId.replace(":", "_") + "_" + timestamp + ".jsonl");

            List<String> lines = messages.stream()
                    .map(this::messageToJson)
                    .collect(Collectors.toList());
            Files.write(transcriptPath, lines);

            log.info("保存对话记录到: {}, conversationId: {}", transcriptPath, conversationId);

            String summary = generateSummary(messages);

            List<Message> compacted = new ArrayList<>();
            compacted.add(new UserMessage("[Compressed History]\n\n" + summary));
            compacted.add(new AssistantMessage("Understood. Continuing."));

            return compacted;

        } catch (Exception e) {
            log.error("自动压缩消息失败, conversationId: {}", conversationId, e);
            return messages;
        }
    }

    private String extractToolName(String content) {
        int start = content.indexOf("\"name\":");
        if (start == -1) {
            return "unknown";
        }
        start += 7;
        int end = content.indexOf("\"", start);
        if (end == -1) {
            return "unknown";
        }
        return content.substring(start, end);
    }

    private String generateSummary(List<Message> messages) {
        StringBuilder summary = new StringBuilder();
        summary.append("Previous conversation summary:\n");

        int userMsgCount = 0;
        int assistantMsgCount = 0;
        List<String> topics = new ArrayList<>();

        for (Message msg : messages) {
            if (msg.getMessageType() == MessageType.USER) {
                userMsgCount++;
                String text = msg.getText();
                if (text != null && text.length() < 100 && !text.startsWith("[")) {
                    topics.add(text.substring(0, Math.min(text.length(), 50)));
                }
            } else if (msg.getMessageType() == MessageType.ASSISTANT) {
                assistantMsgCount++;
            }
        }

        summary.append("- User messages: ").append(userMsgCount).append("\n");
        summary.append("- Assistant messages: ").append(assistantMsgCount).append("\n");
        summary.append("- Total messages: ").append(messages.size()).append("\n");

        if (!topics.isEmpty()) {
            summary.append("- Topics discussed: ").append(String.join(", ", topics.subList(0, Math.min(topics.size(), 5)))).append("\n");
        }

        summary.append("\n(Complete transcript saved to disk for reference)");

        return summary.toString();
    }

    private String messageToJson(Message msg) {
        return "{" +
                "\"type\":\"" + msg.getMessageType().name().toLowerCase() + "\"," +
                "\"content\":\"" + escapeJson(msg.getText()) + "\"," +
                "\"metadata\":" + mapToJson(msg.getMetadata()) +
                "}";
    }

    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String mapToJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        return metadata.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":\"" + escapeJson(String.valueOf(e.getValue())) + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    private int estimateTokenCount(List<Message> messages) {
        int count = 0;
        for (Message msg : messages) {
            String text = msg.getText();
            if (text != null) {
                count += text.length() / 4;
            }
        }
        return count;
    }
}
