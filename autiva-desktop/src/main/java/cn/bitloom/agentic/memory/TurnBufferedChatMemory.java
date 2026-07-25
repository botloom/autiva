package cn.bitloom.agentic.memory;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 支持轮次缓冲的 ChatMemory 接口，扩展 Spring AI ChatMemory。
 * <p>
 * 在 ChatMemory 基础上增加本轮对话缓冲、批量持久化和文件历史查询能力，
 * 供 SessionRunner 在每轮对话开始/结束时管理消息生命周期。
 * 对齐 netInsight 的 TurnBufferedChatMemory 设计，存储介质改为文件系统。
 *
 * @see FileSystemChatMemory
 */
public interface TurnBufferedChatMemory extends ChatMemory {

    /**
     * 设置当前轮次 messageId（由 SessionRunner 在每轮开始时调用）。
     *
     * @param messageId 当前轮次的消息 ID
     */
    void setCurrentMessageId(String messageId);

    /**
     * 本轮对话结束时批量持久化到文件系统（events.jsonl）。
     * 由 SessionRunner 在 doOnComplete/onErrorResume/BLOCK 返回后调用。
     */
    void flush();

    /**
     * 只查文件历史消息（不含本轮缓冲区），供压缩使用。
     *
     * @return 文件历史消息列表（游标之后）
     */
    List<Message> getHistoryFromFile();

    /**
     * 文件历史消息总数（供压缩推进游标用）。
     *
     * @return 消息总数
     */
    int countFileMessages();
}
