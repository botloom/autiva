package cn.bitloom.agentic.agent.subagent.doctor;

import cn.bitloom.agentic.agent.AgentManager;
import cn.bitloom.agentic.agent.subagent.SubagentType;
import cn.bitloom.config.ConfigManager;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.tool.core.AskUserQuestionTool;
import cn.bitloom.agentic.tool.core.TodoWriteTool;
import cn.bitloom.agentic.tool.manage.AppConfigTool;
import cn.bitloom.agentic.tool.manage.McpConfigTool;
import cn.bitloom.agentic.tool.manage.MemoryManageTool;
import cn.bitloom.agentic.tool.manage.SkillConfigTool;
import cn.bitloom.agentic.tool.manage.SubagentConfigTool;
import cn.bitloom.agentic.util.GuiQuestionHandler;
import cn.bitloom.store.ToolUIBridge;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DoctorSubagentType {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final Map<ModelTypeEnum, ChatClient.Builder> chatClientBuilderMap = new ConcurrentHashMap<>();
        private SkillManager skillManager;
        private AgentManager agentManager;
        private ConfigManager configManager;
        private ToolUIBridge toolUIBridge;

        public Builder chatClientBuilder(ModelTypeEnum modelId, ChatClient.Builder chatClientBuilder) {
            this.chatClientBuilderMap.put(modelId, chatClientBuilder);
            return this;
        }

        public Builder chatClientBuilders(Map<ModelTypeEnum, ChatClient.Builder> chatClientBuilderMap) {
            this.chatClientBuilderMap.putAll(chatClientBuilderMap);
            return this;
        }

        public Builder skillManager(SkillManager skillManager) {
            this.skillManager = skillManager;
            return this;
        }

        public Builder agentManager(AgentManager agentManager) {
            this.agentManager = agentManager;
            return this;
        }

        public Builder configManager(ConfigManager configManager) {
            this.configManager = configManager;
            return this;
        }

        public Builder toolUIBridge(ToolUIBridge toolUIBridge) {
            this.toolUIBridge = toolUIBridge;
            return this;
        }

        public SubagentType build() {
            return new SubagentType(
                    new DoctorSubagentResolver(),
                    new DoctorSubagentExecutor(this.chatClientBuilderMap, this.buildDoctorTools())
            );
        }

        private List<ToolCallback> buildDoctorTools() {
            List<Object> toolObjects = new ArrayList<>(List.of(
                    TodoWriteTool.builder().build(),
                    AskUserQuestionTool.builder()
                            .questionHandler(new GuiQuestionHandler(this.toolUIBridge))
                            .build(),
                    SkillConfigTool.builder().skillManager(this.skillManager).build(),
                    McpConfigTool.builder().configManager(this.configManager).build(),
                    MemoryManageTool.builder().agentManager(this.agentManager).build(),
                    SubagentConfigTool.builder().agentManager(this.agentManager).build(),
                    AppConfigTool.builder().configManager(this.configManager).build()
            ));

            return new ArrayList<>(List.of(MethodToolCallbackProvider.builder()
                    .toolObjects(toolObjects.toArray())
                    .build()
                    .getToolCallbacks()));
        }
    }
}
