package cn.bitloom.agentic.agent.subagent.generic;

import cn.bitloom.agentic.agent.subagent.SubagentDefinition;
import cn.bitloom.agentic.agent.subagent.SubagentExecutor;
import cn.bitloom.agentic.agent.subagent.TaskCall;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.skill.SkillManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public class GenericSubagentExecutor implements SubagentExecutor {

    private final Map<ModelTypeEnum, ChatClient.Builder> chatClientBuilderMap;
    private final List<ToolCallback> tools;
    private final SkillManager skillManager;
    private final List<String> skillsDirectories;

    public GenericSubagentExecutor(Map<ModelTypeEnum, ChatClient.Builder> chatClientBuilderMap, List<ToolCallback> tools, SkillManager skillManager, List<String> skillsDirectories) {
        this.chatClientBuilderMap = chatClientBuilderMap;
        this.tools = tools;
        this.skillManager = skillManager;
        this.skillsDirectories = skillsDirectories;
    }

    @Override
    public String getKind() {
        return GenericSubagentDefinition.IDENTITY.name();
    }

    @Override
    public String execute(TaskCall taskCall, Map<String, Object> context, SubagentDefinition subagent, Consumer<String> onChunk) {

        var genericSubagent = (GenericSubagentDefinition) subagent;
        var taskChatClient = this.createTaskChatClient(genericSubagent, (ModelTypeEnum) context.get("model"));

        String preloadedSkillsSystemSuffix = "";

        if (!CollectionUtils.isEmpty(genericSubagent.skills()) && !CollectionUtils.isEmpty(this.skillsDirectories)) {

            var skills = this.skillManager.loadDirectories(this.skillsDirectories);

            preloadedSkillsSystemSuffix = "\n"
                    + skills.stream().filter(s -> genericSubagent.skills().contains(s.name())).map(skill -> "%s\n\n%s".formatted(skill.toXml(),
                    skill.content())).collect(Collectors.joining("\n\n"));
        }

        String sessionId = (String) context.get("sessionId");

        if (onChunk != null) {
            StringBuilder fullResult = new StringBuilder();
            AtomicBoolean stopped = new AtomicBoolean(false);

            taskChatClient.prompt()
                    .system(genericSubagent.content() + preloadedSkillsSystemSuffix)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .user(taskCall.prompt())
                    .stream()
                    .chatResponse()
                    .takeUntil(chatResponse -> EventBus.isStop(sessionId))
                    .doOnNext(chatResponse -> {
                        var result = chatResponse.getResult();
                        AssistantMessage output = result.getOutput();
                        String text = output.getText();
                        if (text != null && !text.isEmpty()) {
                            fullResult.append(text);
                            onChunk.accept(text);
                        }
                    })
                    .doOnComplete(() -> {
                        if (EventBus.isStop(sessionId)) {
                            stopped.set(true);
                        }
                        if (stopped.get()) {
                            onChunk.accept("\n[已停止]");
                        } else {
                            onChunk.accept("\n[完成] agent_id: " + sessionId);
                        }
                        EventBus.clearStopFlag(sessionId);
                    })
                    .doOnError(e -> {
                        if (e instanceof WebClientResponseException webEx) {
                            log.error("子智能体流式执行失败 - Status: {}, Body: {}", webEx.getStatusCode(), webEx.getResponseBodyAsString(), e);
                            onChunk.accept("\n[错误] " + webEx.getStatusCode() + ": " + webEx.getResponseBodyAsString());
                        } else {
                            log.error("子智能体流式执行失败", e);
                            onChunk.accept("\n[错误] " + e.getMessage());
                        }
                    })
                    .blockLast();

            return "agent_id: " + sessionId + "\n\n" + fullResult;
        }

        String result = taskChatClient.prompt()
                .system(genericSubagent.content() + preloadedSkillsSystemSuffix)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .user(taskCall.prompt())
                .call()
                .content();

        return "agent_id: " + sessionId + "\n\n" + result;
    }

    private ChatClient createTaskChatClient(GenericSubagentDefinition genericSubagent, ModelTypeEnum model) {

        ChatClient.Builder builder = this.chatClientBuilderMap.get(model).clone();

        //todo 暂时关闭
//        if (StringUtils.hasText(genericSubagent.getModel())) {
//            builder = this.chatClientBuilderMap.get(genericSubagent.getModel());
//        } else {
//            builder = this.chatClientBuilderMap.get(model);
//        }

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
            log.warn("任务permissionMode尚不支持。permissionMode = {}", genericSubagent.permissionMode());
        }

        return builder.build();
    }

}
