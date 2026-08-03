package cn.bitloom.agentic.hook;

import cn.bitloom.agentic.agent.RuntimeContext;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.model.ToolContext;

/**
 * 智能体 Hook 高级 API。
 * <p>
 * 开发者面向此接口编程，实现具体的横切关注点。
 * 底层通过 HookAdvisor 桥接到 Spring AI Advisor 机制（模型调用），
 * 通过 HookedToolCallback 桥接到 ToolCallback 装饰器机制（工具调用）。
 */
public interface IAgentHook {

    /** Hook 名称，默认取类名 */
    default String name() {
        return this.getClass().getSimpleName();
    }

    /** 执行顺序，数值越小越先执行 */
    default int order() {
        return 0;
    }

    /** 模型调用前拦截，可修改请求 */
    default ChatClientRequest beforeModelCall(ChatClientRequest request) {
        return request;
    }

    /** 模型调用后回调 */
    default void afterModelCall(ChatClientRequest request, ChatClientResponse response) {
    }

    /**
     * 工具调用前拦截，可修改输入或阻止调用。
     *
     * @param toolName 工具名称
     * @param input    原始输入参数（JSON 字符串）
     * @param context  工具上下文（可获取 sessionId、agentId、requestHeaders 等）
     * @return 工具调用决策：proceed(input) 继续（可带修改后的 input），block(reason) 阻止
     */
    default ToolCallDecision beforeToolCall(String toolName, String input, ToolContext context) {
        return ToolCallDecision.proceed(input);
    }

    /**
     * 工具调用后回调，可修改结果。
     *
     * @param toolName 工具名称
     * @param result   原始结果（JSON 字符串，通常是 ToolResult.toJson()）
     * @param context  工具上下文
     * @return 修改后的结果（返回 null 表示不修改，保持原 result）
     */
    default String afterToolCall(String toolName, String result, ToolContext context) {
        return result;
    }

    /**
     * 流式响应文本过滤。每个流式 chunk 的文本在发送给前端前调用此方法。
     * <p>
     * 适用于敏感词过滤、文本脱敏等场景。跨 chunk 的滑动窗口过滤由调用方
     * （HookAdvisor）维护，本方法每次接收一个 chunk 的纯文本。
     *
     * @param text 当前 chunk 的文本内容（可能为 null）
     * @return 过滤后的文本（返回 null 表示保持原文不变）
     */
    default String filterStreamChunk(String text) {
        return null;
    }

    /**
     * 每轮对话开始前调用（每个用户消息只触发一次，在所有 Advisor 执行之前）。
     * <p>
     * 区别于 {@link #beforeModelCall}：beforeModelCall 在工具调用循环中可能多次触发，
     * 而此方法保证每轮对话只调用一次。
     */
    default void beforeConversationRound(RuntimeContext ctx) {
    }

    /**
     * 每轮对话结束后调用（每个用户消息只触发一次，在最终响应生成后）。
     * <p>
     * 区别于 {@link #afterModelCall}：afterModelCall 在工具调用循环中可能多次触发，
     * 而此方法保证每轮对话只调用一次。
     *
     * @param ctx 运行时上下文（携带 sessionId 等信息，流式模式下可安全获取，不依赖 ThreadLocal）
     */
    default void afterConversationRound(RuntimeContext ctx) {
    }
}