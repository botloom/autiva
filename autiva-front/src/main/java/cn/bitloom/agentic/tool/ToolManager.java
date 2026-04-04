package cn.bitloom.agentic.tool;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The type Tool call manager.
 *
 * @author ningyu
 */
@Component
public class ToolManager {

    @Resource
    private List<ITool> toolSets;
    private final Map<String, ToolCallback> toolCallbackMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        this.toolSets.forEach(toolSet -> Arrays.stream(ToolCallbacks.from(toolSet))
                .forEach(toolCallback -> this.toolCallbackMap.put(toolCallback.getToolDefinition().name(), toolCallback)));
    }

    /**
     * Gets tool calling chat options.
     *
     * @param toolList the tool set
     * @return the tool calling chat options
     */
    public ToolCallingChatOptions getToolCallOption(List<String> toolList) {
        List<ToolCallback> toolCallbackList = toolList.stream()
                .map(this.toolCallbackMap::get)
                .filter(Objects::nonNull)
                .toList();
        return ToolCallingChatOptions.builder()
                .toolCallbacks(toolCallbackList)
                .build();
    }

    /**
     * Gets tool callbacks.
     *
     * @param toolSet the tool set
     * @return the tool callbacks
     */
    public List<ToolCallback> getToolCallbacks(Set<String> toolSet) {
        return toolSet.stream()
                .map(this.toolCallbackMap::get)
                .toList();
    }

    /**
     * Get tool definitions list.
     *
     * @return the list
     */
    public List<ToolDefinition> getToolDefinitions() {
        return this.toolCallbackMap.values().stream()
                .map(ToolCallback::getToolDefinition)
                .toList();
    }

    /**
     * Call string.
     *
     * @param toolName the tool name
     * @param toolArg  the tool arg
     * @return the string
     */
    public String call(String toolName, String toolArg) {
        ToolCallback toolCallback = this.toolCallbackMap.computeIfAbsent(toolName, this.toolCallbackMap::get);
        return toolCallback.call(toolArg);
    }

    /**
     * Call string.
     *
     * @param toolCall the tool call
     * @return the string
     */
    public String call(AssistantMessage.ToolCall toolCall) {
        ToolCallback toolCallback = this.toolCallbackMap.computeIfAbsent(toolCall.name(), this.toolCallbackMap::get);
        return toolCallback.call(toolCall.arguments());
    }

}
