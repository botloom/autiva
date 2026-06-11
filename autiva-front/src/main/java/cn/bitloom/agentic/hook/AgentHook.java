package cn.bitloom.agentic.hook;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;

/**
 * 智能体 Hook 高级 API。
 * <p>
 * 开发者面向此接口编程，实现具体的横切关注点。
 * 底层通过 HookAdvisor 桥接到 Spring AI Advisor 机制。
 */
public interface AgentHook {

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

    /** 工具调用前回调 */
    default void beforeToolCall(String toolName, String input) {
    }

    /** 工具调用后回调 */
    default void afterToolCall(String toolName, String result) {
    }
}
