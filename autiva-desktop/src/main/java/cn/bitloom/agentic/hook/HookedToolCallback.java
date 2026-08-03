package cn.bitloom.agentic.hook;

import cn.bitloom.agentic.agent.advisor.HookAdvisor;
import cn.bitloom.agentic.tool.ToolResult;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * 用 {@link HookAdvisor} 包装 {@link ToolCallback}，在工具执行前后插入 Hook 拦截。
 * <p>
 * 这是 Hook 感知工具调用的唯一桥接点：
 * <ul>
 *   <li>beforeToolCall：Hook 链式执行，可修改输入或阻止调用</li>
 *   <li>afterToolCall：Hook 链式执行，可修改结果</li>
 * </ul>
 * 被阻止的工具不执行，返回错误结果给 LLM。
 * <p>
 * 在 Agent 构建时用它包装所有 ToolCallback，替代修改 ToolCallingManager 的方案。
 */
public class HookedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final HookAdvisor hookAdvisor;

    public HookedToolCallback(ToolCallback delegate, HookAdvisor hookAdvisor) {
        this.delegate = delegate;
        this.hookAdvisor = hookAdvisor;
    }

    @Override
    public @NonNull ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public @NonNull ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public @NonNull String call(@NonNull String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public @NonNull String call(@NonNull String toolInput, ToolContext toolContext) {
        String toolName = delegate.getToolDefinition().name();

        // beforeToolCall: hook 链式执行，可能修改 input 或阻止调用
        ToolCallDecision decision = hookAdvisor.beforeToolCall(toolName, toolInput, toolContext);
        if (!decision.proceed()) {
            return ToolResult.error("工具调用被阻止：" + decision.blockReason()).toJson();
        }
        String effectiveArgs = decision.input() != null ? decision.input() : toolInput;

        // 执行工具（异常不捕获，afterToolCall 不调用）
        String result = delegate.call(effectiveArgs, toolContext);

        // afterToolCall: hook 链式执行，可能修改 result
        return hookAdvisor.afterToolCall(toolName, result, toolContext);
    }
}
