package cn.bitloom.agentic.memory;

import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一记忆管理服务。
 * <p>
 * 所有记忆工具通过它操作，Session 处理 MemoryEvent 时也调用它。
 * 按 agentId 解析路径，不再硬编码 "default"。
 * <p>
 * 记忆架构为两层：
 * 1. 热记忆（memory.md）— 自动注入上下文，智能体主动读写
 * 2. 上下文桥接（session.summary）— 压缩后的早期对话摘要
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryManager {

    private static final int MAX_RESULT_LENGTH = 300;
    private static final int DEFAULT_SEARCH_LIMIT = 5;

    private static final String COMPACTION_PROMPT = """
            你是一个对话压缩助手。请将以下对话历史压缩为简洁的摘要，保留：
            1. 关键决策和结论
            2. 重要的上下文信息
            3. 用户偏好和需求
            4. 未完成的任务和待办事项
            忽略日常寒暄和工具调用的技术细节。用简洁的中文输出摘要。
            """;

    private static final String MERGE_SUMMARY_PROMPT = """
            你是一个摘要合并助手。请将"已有摘要"和"新摘要"合并为一份更完整的摘要。
            规则：
            1. 去重：如果信息重复，保留更详细的版本
            2. 更新：如果新信息与已有摘要矛盾，用新信息替换
            3. 精简：保持摘要简洁，删除过时内容
            4. 格式：使用简洁的中文，按主题分组
            直接输出合并后的摘要，不要添加额外说明。
            """;

    private final ModelFactory modelFactory;

    // ===== 路径解析（按 agentId，不硬编码 "default"）=====

    private Path memoryFile(String agentId) {
        return AppConstants.MainAgent.memoryFile(agentId);
    }

    /**
     * 从 sessionId 解析出 agentId
     * sessionId 格式：{agentId}-{type}-{source}-{userId}-{timestamp}
     */
    public String resolveAgentId(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return AppConstants.Agents.DEFAULT_AGENT_NAME;
        }
        return sessionId.split("-")[0];
    }

    // ===== 写入操作（供记忆工具调用）=====

    /**
     * 追加记忆到 memory.md
     * @param agentId 智能体ID
     * @param content 记忆内容
     */
    public void save(String agentId, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        try {
            Path file = memoryFile(agentId);
            Files.createDirectories(file.getParent());
            String existing = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
            String appended = existing.isBlank() ? content : existing + "\n\n" + content;
            Files.writeString(file, appended, StandardCharsets.UTF_8);
            log.info("[MemoryManager] 已追加到 memory.md: agentId={}", agentId);
        } catch (IOException e) {
            log.error("[MemoryManager] save 失败: agentId={}", agentId, e);
            throw new RuntimeException("写入记忆失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新 memory.md 的指定区块
     * @param agentId 智能体ID
     * @param section 区块名称（用户画像/关键偏好/近期事件/例行提醒）
     * @param content 新的区块内容
     */
    public void update(String agentId, String section, String content) {
        if (section == null || section.isBlank()) {
            return;
        }
        try {
            Path file = memoryFile(agentId);
            Files.createDirectories(file.getParent());
            String existing = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
            String updated = replaceSection(existing, section, content);
            Files.writeString(file, updated, StandardCharsets.UTF_8);
            log.info("[MemoryManager] 已更新 memory.md 区块: agentId={}, section={}", agentId, section);
        } catch (IOException e) {
            log.error("[MemoryManager] update 失败: agentId={}, section={}", agentId, section, e);
            throw new RuntimeException("更新记忆失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除记忆条目
     * @param agentId 智能体ID
     * @param section 区块名称（清空该区块内容）
     */
    public void delete(String agentId, String section) {
        if (section == null || section.isBlank()) {
            return;
        }
        try {
            Path file = memoryFile(agentId);
            if (Files.exists(file)) {
                String existing = Files.readString(file, StandardCharsets.UTF_8);
                String updated = replaceSection(existing, section, "*（已清空）*");
                Files.writeString(file, updated, StandardCharsets.UTF_8);
                log.info("[MemoryManager] 已清空 memory.md 区块: agentId={}, section={}", agentId, section);
            }
        } catch (IOException e) {
            log.error("[MemoryManager] delete 失败: agentId={}, section={}", agentId, section, e);
            throw new RuntimeException("删除记忆失败: " + e.getMessage(), e);
        }
    }

    // ===== 搜索操作（供记忆工具调用，逻辑从 MemorySearchService 迁移）=====

    /**
     * 搜索记忆文件
     * @param agentId 智能体ID
     * @param query 搜索关键词
     * @param limit 最大结果数
     * @return 格式化的搜索结果
     */
    public String search(String agentId, String query, int limit) {
        if (query == null || query.isBlank()) {
            return "搜索关键词不能为空";
        }
        int actualLimit = limit <= 0 ? DEFAULT_SEARCH_LIMIT : limit;
        List<SearchResult> results = doSearch(agentId, query, actualLimit);
        if (results.isEmpty()) {
            return "未找到匹配的记忆";
        }
        StringBuilder sb = new StringBuilder();
        for (SearchResult result : results) {
            sb.append("- **").append(result.fileName).append("**");
            if (result.description != null && !result.description.isBlank()) {
                sb.append(" — ").append(result.description);
            }
            sb.append("\n");
            if (result.snippet != null && !result.snippet.isBlank()) {
                sb.append("  ").append(truncate(result.snippet, 200)).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 异步上下文压缩
     * @param agentId 智能体ID
     * @param sessionId 会话ID
     * @param messages 待压缩的消息列表
     * @param existingSummary 已有摘要
     * @return 新的摘要
     */
    public String compact(String agentId, String sessionId,
                          List<Message> messages, String existingSummary) {
        try {
            // 1. 保存原始消息到 transcripts（审计）
            saveTranscripts(agentId, sessionId, messages);

            // 2. 调用 LLM 生成摘要
            String conversationText = extractConversationText(messages);
            if (conversationText.isBlank()) {
                return existingSummary;
            }
            String newSummary = callLlm(COMPACTION_PROMPT + "\n\n对话内容：\n" + conversationText);
            if (newSummary == null || newSummary.isBlank()) {
                return existingSummary;
            }

            // 3. 合并已有摘要
            if (existingSummary != null && !existingSummary.isBlank()) {
                newSummary = callLlm(MERGE_SUMMARY_PROMPT
                        + "\n\n## 已有摘要\n" + existingSummary
                        + "\n\n## 新摘要\n" + newSummary);
            }
            log.info("[MemoryManager] 压缩完成: agentId={}, sessionId={}", agentId, sessionId);
            return newSummary;
        } catch (Exception e) {
            log.error("[MemoryManager] compact 失败: agentId={}, sessionId={}", agentId, sessionId, e);
            return existingSummary;
        }
    }

    // ===== 私有辅助方法 =====

    /**
     * 替换 memory.md 中指定 ## 区块的内容
     */
    private String replaceSection(String content, String sectionName, String newContent) {
        Pattern pattern = Pattern.compile(
                "(##\\s*" + Pattern.quote(sectionName) + "\\s*\\n)([\\s\\S]*?)(?=\\n##\\s|$)");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.replaceFirst(matcher.group(1) + newContent);
        }
        // 区块不存在，追加到末尾
        String appended = content.isBlank() ? "" : content + "\n\n";
        return appended + "## " + sectionName + "\n" + newContent;
    }

    private List<SearchResult> doSearch(String agentId, String query, int limit) {
        List<SearchResult> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase();

        // 搜索 memory.md
        Path memFile = memoryFile(agentId);
        if (Files.exists(memFile)) {
            searchInFile(memFile, "memory.md", lowerQuery, results, limit);
        }

        return results.subList(0, Math.min(results.size(), limit));
    }

    private void searchInFile(Path file, String fileName, String lowerQuery,
                              List<SearchResult> results, int limit) {
        if (results.size() >= limit) return;
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String lowerContent = content.toLowerCase();

            if (lowerContent.contains(lowerQuery) || fileName.toLowerCase().contains(lowerQuery)) {
                SearchResult result = new SearchResult();
                result.fileName = fileName;
                result.description = extractDescription(content);
                result.snippet = extractSnippet(content, lowerQuery);
                results.add(result);
            }
        } catch (Exception e) {
            log.debug("[MemoryManager] 搜索记忆文件失败: {}", file, e);
        }
    }

    private String extractDescription(String content) {
        int descIdx = content.indexOf("description:");
        if (descIdx == -1) return null;
        int start = descIdx + "description:".length();
        int end = content.indexOf('\n', start);
        if (end == -1) end = Math.min(content.length(), start + 150);
        return content.substring(start, end).trim();
    }

    private String extractSnippet(String content, String query) {
        int idx = content.toLowerCase().indexOf(query);
        if (idx == -1) return null;
        int start = Math.max(0, idx - 50);
        int end = Math.min(content.length(), idx + query.length() + 150);
        String snippet = content.substring(start, end).replaceAll("\\s+", " ").trim();
        return truncate(snippet, MAX_RESULT_LENGTH);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
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

    private String callLlm(String prompt) {
        try {
            return ChatClient.builder(modelFactory.model(ModelTypeEnum.DEEPSEEK)).build()
                    .prompt()
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "memory-manager"))
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("[MemoryManager] LLM 调用失败", e);
            return null;
        }
    }

    private void saveTranscripts(String agentId, String sessionId, List<Message> messages) {
        try {
            Path transcriptsDir = AppConstants.Base.WORKSPACE_DIR.resolve(agentId).resolve("transcripts");
            Files.createDirectories(transcriptsDir);
            String filename = "transcript_" + sessionId + "_" + System.currentTimeMillis() + ".jsonl";
            Path transcriptFile = transcriptsDir.resolve(filename);
            StringBuilder sb = new StringBuilder();
            for (Message msg : messages) {
                sb.append(JsonUtils.toJson(msg)).append("\n");
            }
            Files.writeString(transcriptFile, sb.toString(), StandardCharsets.UTF_8);
            log.debug("[MemoryManager] 已保存 transcripts: {}", transcriptFile);
        } catch (IOException e) {
            log.warn("[MemoryManager] 保存 transcripts 失败: sessionId={}", sessionId, e);
        }
    }

    private static class SearchResult {
        String fileName;
        String description;
        String snippet;
    }
}
