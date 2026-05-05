package cn.bitloom.agentic.agent.subagent.generic;

import cn.bitloom.agentic.agent.subagent.SubagentDefinition;
import cn.bitloom.agentic.agent.subagent.SubagentExecutor;
import cn.bitloom.agentic.agent.subagent.TaskCall;
import cn.bitloom.agentic.session.SessionManager;
import cn.bitloom.agentic.skill.SkillManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class GenericSubagentExecutor implements SubagentExecutor {

    private static final Logger logger = LoggerFactory.getLogger(GenericSubagentExecutor.class);

    private final Map<String, ChatClient.Builder> chatClientBuilderMap;
    private final List<ToolCallback> tools;
    private final SkillManager skillManager;
    private final List<String> skillsDirectories;
    private final ChatMemory chatMemory;
    private final SessionManager sessionManager;

    public GenericSubagentExecutor(Map<String, ChatClient.Builder> chatClientBuilderMap, List<ToolCallback> tools,
                                SkillManager skillManager, List<String> skillsDirectories, ChatMemory chatMemory,
                                SessionManager sessionManager) {

        Assert.notEmpty(chatClientBuilderMap, "chatClientBuilderMap不能为空");
        Assert.isTrue(chatClientBuilderMap.containsKey("default"),
                "chatClientBuilderMap必须包含一个键为'default'的默认ChatClient.Builder");
        Assert.notNull(skillManager, "skillManager不能为null");
        Assert.notNull(skillsDirectories, "skillsDirectories不能为null");
        Assert.notNull(chatMemory, "chatMemory不能为null");

        this.chatClientBuilderMap = chatClientBuilderMap;
        this.tools = tools;
        this.skillManager = skillManager;
        this.skillsDirectories = skillsDirectories;
        this.chatMemory = chatMemory;
        this.sessionManager = sessionManager;
    }

    @Override
    public String getKind() {
        return GenericSubagentDefinition.KIND;
    }

    @Override
    public String execute(TaskCall taskCall, SubagentDefinition subagent) {
        return execute(taskCall, subagent, null);
    }

    @Override
    public String execute(TaskCall taskCall, SubagentDefinition subagent, Consumer<String> onChunk) {

        var genericSubagent = (GenericSubagentDefinition) subagent;
        var taskChatClient = this.createTaskChatClient(genericSubagent);

        String preloadedSkillsSystemSuffix = "";

        if (!CollectionUtils.isEmpty(genericSubagent.skills()) && !CollectionUtils.isEmpty(this.skillsDirectories)) {

            var skills = this.skillManager.loadDirectories(this.skillsDirectories);

            preloadedSkillsSystemSuffix = "\n"
                    + skills.stream().filter(s -> genericSubagent.skills().contains(s.name())).map(skill -> "%s\n\n%s".formatted(skill.toXml(),
                    skill.content())).collect(Collectors.joining("\n\n"));
        }

        String conversationId;
        String agentId;

        if (StringUtils.hasText(taskCall.childSessionId())) {
            conversationId = taskCall.childSessionId();
            agentId = taskCall.childSessionId();
        } else if (StringUtils.hasText(taskCall.resume())) {
            agentId = taskCall.resume();
            conversationId = agentId;
        } else if (StringUtils.hasText(taskCall.sessionId())) {
            agentId = taskCall.sessionId() + "_" + taskCall.subagent_type();
            conversationId = agentId;
        } else {
            agentId = UUID.randomUUID().toString();
            conversationId = agentId;
        }

        String finalConversationId = conversationId;

        if (onChunk != null) {
            StringBuilder fullResult = new StringBuilder();

            taskChatClient.prompt()
                    .system(genericSubagent.content() + preloadedSkillsSystemSuffix)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, finalConversationId))
                    .user(taskCall.prompt())
                    .stream()
                    .chatResponse()
                    .doOnNext(chatResponse -> {
                        var result = chatResponse.getResult();
                        if (result != null && result.getOutput() != null) {
                            AssistantMessage output = result.getOutput();
                            String text = output.getText();
                            if (text != null && !text.isEmpty()) {
                                fullResult.append(text);
                                onChunk.accept(text);
                            }

                            if (this.sessionManager != null && StringUtils.hasText(finalConversationId)) {
                                List<Message> messagesToAppend = new ArrayList<>();
                                messagesToAppend.add(output);
                                try {
                                    this.sessionManager.appendMessage(finalConversationId, messagesToAppend);
                                } catch (Exception e) {
                                    logger.warn("子智能体消息推送到EventBus失败", e);
                                }
                            }
                        }
                    })
                    .doOnComplete(() -> onChunk.accept("\n[完成] agent_id: " + agentId))
                    .doOnError(e -> {
                        logger.error("子智能体流式执行失败", e);
                        onChunk.accept("\n[错误] " + e.getMessage());
                    })
                    .blockLast();

            return "agent_id: " + agentId + "\n\n" + fullResult;
        }

        String result = taskChatClient.prompt()
                .system(genericSubagent.content() + preloadedSkillsSystemSuffix)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, finalConversationId))
                .user(taskCall.prompt())
                .call()
                .content();

        return "agent_id: " + agentId + "\n\n" + result;
    }

    private ChatClient createTaskChatClient(GenericSubagentDefinition genericSubagent) {

        var builder = this.doFindChatClientBuilder(genericSubagent).clone();

        if (!CollectionUtils.isEmpty(this.tools)) {

            List<ToolCallback> subagentTools = new ArrayList<>(this.tools);

            if (!CollectionUtils.isEmpty(genericSubagent.tools())) {
                subagentTools = this.tools.stream()
                        .filter(tc -> genericSubagent.tools().contains(tc.getToolDefinition().name()))
                        .toList();
            }

            if (!CollectionUtils.isEmpty(genericSubagent.disallowedTools())) {
                subagentTools = subagentTools.stream()
                        .filter(tc -> !genericSubagent.disallowedTools().contains(tc.getToolDefinition().name()))
                        .toList();
            }

            builder.defaultToolCallbacks(subagentTools);
        }

        if (!genericSubagent.permissionMode().equals("default")) {
            logger.warn("任务permissionMode尚不支持。permissionMode = {}", genericSubagent.permissionMode());
        }

        return builder.defaultAdvisors(
                MessageChatMemoryAdvisor.builder(this.chatMemory).build(),
                ToolCallAdvisor.builder().build()
        ).build();
    }

    private static final Map<String, String> MODEL_NAME_MAPPER = Map.of(
            "deepseek", "deepseek-chat",
            "glm", "glm-4-flash",
            "opus", "claude-opus-4-64k",
            "haiku", "claude-haiku-4-5-20251001",
            "sonnet", "claude-sonnet-4-5-20250929"
    );

    protected ChatClient.Builder doFindChatClientBuilder(GenericSubagentDefinition genericSubagent) {

        if (StringUtils.hasText(genericSubagent.getModel())) {
            var providerName = "default";

            var modelRef = genericSubagent.getModel();
            var modelName = modelRef.trim();

            if (modelRef.contains(":")) {
                var parts = modelRef.split(":");
                if (StringUtils.hasText(parts[0])) {
                    providerName = parts[0].trim();
                }
                if (StringUtils.hasText(parts[1])) {
                    modelName = parts[1].trim();
                }
            }

            if (this.chatClientBuilderMap.containsKey(providerName)) {
                var builder = this.chatClientBuilderMap.get(providerName);
                if (StringUtils.hasText(modelName)) {
                    if (MODEL_NAME_MAPPER.containsKey(modelName)) {
                        modelName = MODEL_NAME_MAPPER.get(modelName);
                    }
                    builder = builder.clone().defaultOptions(ChatOptions.builder().model(modelName).build());
                }
                return builder;
            }
        }

        return this.chatClientBuilderMap.get("default");
    }

}
