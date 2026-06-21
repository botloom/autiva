package cn.bitloom.agentic.tool;

import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

public class ToolCallManager implements ToolCallingManager {
    @Override
    public @NonNull List<ToolDefinition> resolveToolDefinitions(@NonNull ToolCallingChatOptions chatOptions) {
        return List.of();
    }

    @Override
    public @NonNull ToolExecutionResult executeToolCalls(@NonNull Prompt prompt, @NonNull ChatResponse chatResponse) {
        return null;
    }
}
