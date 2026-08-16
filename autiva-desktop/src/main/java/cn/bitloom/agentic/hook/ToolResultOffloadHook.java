package cn.bitloom.agentic.hook;

import cn.bitloom.constant.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 工具结果超长落盘 Hook。
 * <p>
 * 当工具返回的结果超过 {@link #TOOL_RESULT_DISK_THRESHOLD} 字符时，将完整结果写入 session
 * 目录下的 {@code tool-results/} 文件，并返回一条摘要消息（含前
 * {@link #TOOL_RESULT_PREVIEW_LENGTH} 字符预览 + 落盘文件路径）。以零 LLM 成本降低上下文占用。
 * <p>
 * 通过 {@link IAgentHook#afterToolCall} 拦截工具返回值，属于结果后处理的横切关注点，
 * 因此实现为 Hook 而非侵入 {@code AutivaToolCallingManager} 的工具编排逻辑。
 */
@Slf4j
public class ToolResultOffloadHook implements IAgentHook {

    /** 工具结果超过该字符长度时落盘，上下文只保留摘要 + 文件路径。 */
    private static final int TOOL_RESULT_DISK_THRESHOLD = 20000;

    /** 落盘摘要中保留的工具结果预览字符数。 */
    private static final int TOOL_RESULT_PREVIEW_LENGTH = 2000;

    @Override
    public String name() {
        return "ToolResultOffloadHook";
    }

    @Override
    public int order() {
        return 30; // 在 PermissionHook(10) / TodoReminderHook(20) 之后，最后处理结果
    }

    @Override
    public String afterToolCall(String toolName, String result, ToolContext context) {
        if (result == null || result.length() <= TOOL_RESULT_DISK_THRESHOLD) {
            return result;
        }

        String sessionId = extractString(context, "sessionId");
        if (sessionId == null) {
            log.debug("[ToolResultOffloadHook] 工具 {} 结果超长但无 sessionId，跳过落盘", toolName);
            return result;
        }

        try {
            Path dir = AppConstants.Session.sessionDir(sessionId).resolve("tool-results");
            Files.createDirectories(dir);
            String fileName = toolName + "-" + UUID.randomUUID() + ".txt";
            Path file = dir.resolve(fileName);
            Files.writeString(file, result, StandardCharsets.UTF_8);

            String preview = result.substring(0, TOOL_RESULT_PREVIEW_LENGTH);
            String summary = String.format(
                    "[工具结果已落盘，长度 %d 字符，完整内容见文件: %s]\n前 %d 字符预览:\n%s\n...",
                    result.length(), file, TOOL_RESULT_PREVIEW_LENGTH, preview);
            log.info("[ToolResultOffloadHook] 工具 {} 结果超长({} 字符)已落盘: {}", toolName, result.length(), file);
            return summary;
        } catch (IOException e) {
            log.warn("[ToolResultOffloadHook] 工具 {} 结果落盘失败，保留原文", toolName, e);
            return result;
        }
    }

    /**
     * 从 {@link ToolContext} 中提取字符串值。
     */
    private String extractString(ToolContext context, String key) {
        if (context == null || context.getContext() == null) {
            return null;
        }
        Object value = context.getContext().get(key);
        return value instanceof String s ? s : null;
    }
}
