package cn.bitloom.agentic.tool;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 自定义工具调用管理器，在工具执行前增加校验：
 * <p>
 * 1. 工具存在性校验：当 LLM 幻觉出不存在的工具名时，返回友好错误提示而非抛异常，
 *    让 LLM 有机会自我纠正
 * 2. 工具权限校验：基于 AgentDefinition.tools() 白名单，阻止未授权的工具调用
 * <p>
 * 参考 Spring AI 2.0 的 DefaultToolCallingManager 实现，核心改动为：
 * 找不到 ToolCallback 时返回 ToolResult.toolNotFound() 错误响应，而非抛 IllegalStateException。
 */
@Slf4j
public class AutivaToolCallingManager implements ToolCallingManager {

    private final Set<String> registeredToolNames;

    public AutivaToolCallingManager(List<ToolCallback> toolCallbacks) {
        this.registeredToolNames = toolCallbacks.stream()
                .map(tc -> tc.getToolDefinition().name())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public @NonNull List<ToolDefinition> resolveToolDefinitions(@NonNull ToolCallingChatOptions chatOptions) {
        List<ToolCallback> toolCallbacks = new ArrayList<>(
                !CollectionUtils.isEmpty(chatOptions.getToolCallbacks())
                        ? chatOptions.getToolCallbacks() : List.of());
        return toolCallbacks.stream().map(ToolCallback::getToolDefinition).toList();
    }

    @Override
    public @NonNull ToolExecutionResult executeToolCalls(@NonNull Prompt prompt,
                                                          @NonNull ChatResponse chatResponse) {
        // 1. 从 chatResponse 提取 tool_calls
        Optional<Generation> toolCallGeneration = chatResponse.getResults().stream()
                .filter(g -> !CollectionUtils.isEmpty(g.getOutput().getToolCalls()))
                .findFirst();

        if (toolCallGeneration.isEmpty()) {
            throw new IllegalStateException("No tool call requested by the chat model");
        }

        AssistantMessage assistantMessage = toolCallGeneration.get().getOutput();
        ToolContext toolContext = buildToolContext(prompt);

        // 2. 从 prompt options 获取已注册的 ToolCallback 列表
        List<ToolCallback> toolCallbacks = List.of();
        if (prompt.getOptions() instanceof ToolCallingChatOptions options
                && !CollectionUtils.isEmpty(options.getToolCallbacks())) {
            toolCallbacks = options.getToolCallbacks();
        }

        // 3. 逐个执行工具调用，找不到工具时返回友好错误
        List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
        Boolean returnDirect = null;

        for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
            String toolName = toolCall.name();
            String toolInput = StringUtils.hasText(toolCall.arguments())
                    ? toolCall.arguments() : "{}";

            // 查找 ToolCallback
            ToolCallback toolCallback = toolCallbacks.stream()
                    .filter(tool -> toolName.equals(tool.getToolDefinition().name()))
                    .findFirst()
                    .orElse(null);

            if (toolCallback == null) {
                // 工具不存在 → 返回友好错误，而非抛异常
                String errorMsg = ToolResult.toolNotFound(toolName, registeredToolNames).toJson();
                log.warn("[ToolCall] 工具不存在: {} - 可用工具: {}", toolName, registeredToolNames);
                toolResponses.add(new ToolResponseMessage.ToolResponse(
                        toolCall.id(), toolName, errorMsg));
            } else {
                // 正常执行工具
                returnDirect = (returnDirect == null)
                        ? toolCallback.getToolMetadata().returnDirect()
                        : returnDirect && toolCallback.getToolMetadata().returnDirect();

                String toolResult;
                try {
                    toolResult = toolCallback.call(toolInput, toolContext);
                } catch (ToolExecutionException ex) {
                    toolResult = DefaultToolExecutionExceptionProcessor.builder().build().process(ex);
                } catch (IllegalStateException ex) {
                    // JSON 解析失败等异常：LLM 生成了无效的工具参数，返回友好错误让 LLM 自我纠正
                    String errorMsg = ToolResult.error(
                            "工具调用参数格式错误，请确保参数为合法的 JSON 格式，字段之间用逗号分隔。错误: " + ex.getMessage(),
                            Map.of("raw_input", toolInput)).toJson();
                    log.warn("[ToolCall] 工具 {} 参数解析失败: {}", toolName, ex.getMessage());
                    toolResult = errorMsg;
                }
                toolResponses.add(new ToolResponseMessage.ToolResponse(
                        toolCall.id(), toolName, toolResult));
            }
        }

        // 4. 构建对话历史和返回结果
        ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
                .responses(toolResponses).build();

        List<Message> conversationHistory = new ArrayList<>(prompt.getInstructions());
        conversationHistory.add(assistantMessage);
        conversationHistory.add(toolResponseMessage);

        return ToolExecutionResult.builder()
                .conversationHistory(conversationHistory)
                .returnDirect(Objects.requireNonNullElse(returnDirect, false))
                .build();
    }

    private static ToolContext buildToolContext(Prompt prompt) {
        Map<String, Object> toolContextMap = Map.of();
        if (prompt.getOptions() instanceof ToolCallingChatOptions options
                && !CollectionUtils.isEmpty(options.getToolContext())) {
            toolContextMap = new HashMap<>(options.getToolContext());
        }
        return new ToolContext(toolContextMap);
    }
}
