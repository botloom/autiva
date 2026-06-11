package cn.bitloom.agentic.hook;

import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.constant.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记忆整理 Hook，在模型调用后自动整理对话记忆。
 */
@Slf4j
public class MemoryConsolidateHook implements AgentHook {

    private static final int MEMORY_CONSOLIDATE_THRESHOLD = 10;
    private static final String CONSOLIDATE_PROMPT = """
            你是一个记忆整理助手。请从以下对话中提取关键事实和信息，以简洁的要点形式输出。
            只提取重要的事实、决策、偏好和关键信息，忽略日常寒暄和工具调用的技术细节。
            每个要点一行，使用 - 开头。
            """;

    private static final String MERGE_PROMPT = """
            你是一个记忆合并助手。请将以下"日流水账"中的新事实合并到"现有长期记忆"中。
            规则：
            1. 去重：如果新事实与已有记忆重复，保留更详细的版本
            2. 更新：如果新事实与已有记忆矛盾，用新事实替换旧记忆
            3. 精简：保持记忆简洁，删除过时或不再相关的内容
            4. 格式：使用 Markdown 格式，按主题分组
            直接输出合并后的长期记忆内容，不要添加额外说明。
            """;

    private final ChatClient chatClient;
    private final Map<String, Integer> conversationCursors = new ConcurrentHashMap<>();

    public MemoryConsolidateHook(ModelFactory modelFactory) {
        this.chatClient = ChatClient.builder(modelFactory.model(ModelTypeEnum.DEEPSEEK)).build();
    }

    @Override
    public int order() {
        return 100; // 在 ChatMemory Advisor 之后执行
    }

    @Override
    public void afterModelCall(ChatClientRequest request, ChatClientResponse response) {
        try {
            String conversationId = extractConversationId(response);
            if (conversationId == null) {
                return;
            }

            // 从请求中获取对话消息
            List<Message> messages = request.prompt().getInstructions();
            int cursor = conversationCursors.getOrDefault(conversationId, 0);
            int unprocessedCount = messages.size() - cursor;
            if (unprocessedCount < MEMORY_CONSOLIDATE_THRESHOLD) {
                return;
            }

            log.debug("[MemoryConsolidateHook] 开始记忆整理: conversationId={}, cursor={}, unprocessed={}",
                    conversationId, cursor, unprocessedCount);

            // 1. 收集未处理的消息
            List<Message> unprocessedMessages = messages.subList(cursor, messages.size());

            // 2. 提取用户和助手的文本内容
            String conversationText = extractConversationText(unprocessedMessages);
            if (conversationText.isBlank()) {
                return;
            }

            // 3. 调用 LLM 总结为关键事实
            String newFacts = consolidateFacts(conversationText);
            if (newFacts == null || newFacts.isBlank()) {
                return;
            }

            // 4. 追加到日流水账 memory/YYYY-MM-DD.md
            appendToDailyJournal(newFacts);

            // 5. 合并到 MEMORY.md
            mergeToLongTermMemory(newFacts);

            // 6. 更新 cursor
            conversationCursors.put(conversationId, messages.size());

            log.info("[MemoryConsolidateHook] 记忆整理完成: conversationId={}", conversationId);
        } catch (Exception e) {
            log.warn("[MemoryConsolidateHook] 记忆整理失败", e);
        }
    }

    private String extractConversationId(ChatClientResponse response) {
        Object conversationId = response.context().get(ChatMemory.CONVERSATION_ID);
        return conversationId != null ? conversationId.toString() : null;
    }

    private String extractConversationText(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message msg : messages) {
            if (msg.getMessageType() == MessageType.USER || msg.getMessageType() == MessageType.ASSISTANT) {
                String text = msg.getText();
                if (text != null && !text.isBlank()) {
                    String role = msg.getMessageType() == MessageType.USER ? "用户" : "助手";
                    sb.append(role).append(": ").append(text.stripTrailing()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private String consolidateFacts(String conversationText) {
        try {
            return chatClient.prompt()
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "memory-consolidate"))
                    .user(CONSOLIDATE_PROMPT + "\n\n对话内容：\n" + conversationText)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("[MemoryConsolidateHook] LLM 记忆总结失败", e);
            return null;
        }
    }

    private void appendToDailyJournal(String newFacts) throws IOException {
        Path memoryDir = AppConstants.Base.MEMORY_DIR;
        if (!Files.exists(memoryDir)) {
            Files.createDirectories(memoryDir);
        }

        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        Path dailyFile = memoryDir.resolve(today + ".md");

        String content;
        if (Files.exists(dailyFile)) {
            content = Files.readString(dailyFile, StandardCharsets.UTF_8) + "\n\n" + newFacts;
        } else {
            content = "# " + today + " 记忆流水账\n\n" + newFacts;
        }

        Files.writeString(dailyFile, content, StandardCharsets.UTF_8);
        log.debug("[MemoryConsolidateHook] 已写入日流水账: {}", dailyFile);
    }

    private void mergeToLongTermMemory(String newFacts) throws IOException {
        Path memoryFile = AppConstants.Base.MEMORY_MD;

        String existingMemory = "";
        if (Files.exists(memoryFile)) {
            existingMemory = Files.readString(memoryFile, StandardCharsets.UTF_8);
        }

        try {
            String mergedMemory = chatClient.prompt()
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "memory-consolidate"))
                    .user(MERGE_PROMPT + "\n\n## 现有长期记忆\n" + existingMemory
                            + "\n\n## 日流水账新事实\n" + newFacts)
                    .call()
                    .content();

            if (mergedMemory != null && !mergedMemory.isBlank()) {
                Files.writeString(memoryFile, mergedMemory, StandardCharsets.UTF_8);
                log.debug("[MemoryConsolidateHook] 已更新 MEMORY.md");
            }
        } catch (Exception e) {
            log.error("[MemoryConsolidateHook] 合并长期记忆失败", e);
        }
    }
}
