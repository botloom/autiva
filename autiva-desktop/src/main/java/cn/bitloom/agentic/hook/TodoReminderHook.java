package cn.bitloom.agentic.hook;

import cn.bitloom.agentic.agent.RuntimeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 待办事项遗漏提醒 Hook。
 * <p>
 * 当 LLM 使用过 {@code TodoWrite} 工具后，若连续若干次 LLM 调用（停止原因为
 * {@code TOOL_CALLS}）都未再使用 {@code TodoWrite} 更新任务进度，则在下一次
 * 模型调用前注入一条 synthetic 用户消息，促使 LLM 主动更新待办事项状态。
 * <p>
 * 「连续 N 轮」按 LLM 调用次数累计，一轮对话内持续，直到某次调用使用了 TodoWrite
 * 清零，或一轮对话结束后整体重置。提醒通过 {@link #beforeModelCall} 注入为消息列表
 * 末尾的一条 synthetic 用户消息，标记 {@code metadata.synthetic = true}，不改变
 * UI 渲染（工具卡片由 {@code AutivaToolCallingManager} 独立发布，不经过本 Hook）。
 */
@Slf4j
public class TodoReminderHook implements IAgentHook {

    /** 连续多少次 LLM 调用未使用 TodoWrite 后触发提醒 */
    private static final int REMINDER_THRESHOLD = 3;

    private static final String TODO_TOOL_NAME = "TodoWrite";

    private static final String METADATA_SYNTHETIC = "synthetic";

    private static final String REMINDER_TEXT =
            "[系统提醒] 你已经连续 " + REMINDER_THRESHOLD
                    + " 轮工具调用未使用 TodoWrite 更新待办事项列表。"
                    + "请检查当前任务进度，并在需要时调用 TodoWrite 工具更新任务状态。";

    /** 提醒状态（每轮对话结束后重置） */
    private State state;

    @Override
    public String name() {
        return "TodoReminderHook";
    }

    @Override
    public int order() {
        return 20; // 在 PermissionHook(10) 之后执行，不干扰权限拦截
    }

    @Override
    public ChatClientRequest beforeModelCall(ChatClientRequest request) {
        if (state == null || !state.pendingReminder) {
            return request;
        }

        // 在下一次模型调用前注入提醒，一次注入后清除标记，避免重复注入
        state.pendingReminder = false;

        try {
            Prompt prompt = request.prompt();
            List<Message> messages = new ArrayList<>(prompt.getInstructions());
            messages.add(UserMessage.builder()
                    .text(REMINDER_TEXT)
                    .metadata(Map.of(METADATA_SYNTHETIC, Boolean.TRUE))
                    .build());

            log.info("[TodoReminderHook] 在下一次模型调用前注入 TodoWrite 提醒（synthetic 用户消息）: sessionId={}",
                    extractSessionId(request));
            return request.mutate()
                    .prompt(prompt.mutate().messages(messages).build())
                    .build();
        } catch (Exception e) {
            log.warn("[TodoReminderHook] 注入提醒失败", e);
            return request;
        }
    }

    @Override
    public void afterModelCall(ChatClientRequest request, ChatClientResponse response, String finishReason) {
        if (!"TOOL_CALLS".equals(finishReason)) {
            return;
        }
        if (response == null || response.chatResponse() == null
                || response.chatResponse().getResult() == null) {
            return;
        }

        boolean usedTodo = response.chatResponse().getResult().getOutput().getToolCalls().stream()
                .anyMatch(tc -> TODO_TOOL_NAME.equals(tc.name()));

        if (state == null) {
            state = new State();
        }
        if (usedTodo) {
            state.todoActive = true;
            state.streakWithoutTodo = 0;
            state.pendingReminder = false;
            return;
        }

        if (!state.todoActive) {
            return;
        }

        state.streakWithoutTodo++;
        if (state.streakWithoutTodo >= REMINDER_THRESHOLD) {
            state.pendingReminder = true;
            state.streakWithoutTodo = 0; // 提醒后重新计数，避免连续重复提醒
            log.debug("[TodoReminderHook] 连续 {} 次调用未使用 TodoWrite，标记待注入提醒: sessionId={}",
                    REMINDER_THRESHOLD, extractSessionId(request));
        }
    }

    @Override
    public void afterConversationRound(RuntimeContext ctx) {
        // 一轮对话结束，重置状态，计数从头开始
        state = null;
    }

    private String extractSessionId(ChatClientRequest request) {
        try {
            Object sid = request.context().get(ChatMemory.CONVERSATION_ID);
            if (sid instanceof String s) {
                return s;
            }
            Object ctx = request.context().get("runtimeContext");
            if (ctx instanceof RuntimeContext rc) {
                return rc.getSessionId();
            }
        } catch (Exception ignored) {
            // 忽略上下文访问异常
        }
        return null;
    }

    /** 提醒状态（每个 Hook 实例独立，无 session 概念） */
    private static final class State {
        /** 是否已使用过 TodoWrite */
        boolean todoActive = false;
        /** 连续未使用 TodoWrite 的调用次数 */
        int streakWithoutTodo = 0;
        /** 是否待在下一次模型调用前注入提醒 */
        boolean pendingReminder = false;
    }
}
