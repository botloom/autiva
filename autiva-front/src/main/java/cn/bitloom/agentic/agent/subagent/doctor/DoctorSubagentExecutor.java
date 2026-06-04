package cn.bitloom.agentic.agent.subagent.doctor;

import cn.bitloom.agentic.agent.subagent.SubagentDefinition;
import cn.bitloom.agentic.agent.subagent.SubagentExecutor;
import cn.bitloom.agentic.agent.subagent.TaskCall;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.model.ModelTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public class DoctorSubagentExecutor implements SubagentExecutor {

    private final Map<ModelTypeEnum, ChatClient.Builder> chatClientBuilderMap;
    private final List<ToolCallback> tools;

    public DoctorSubagentExecutor(Map<ModelTypeEnum, ChatClient.Builder> chatClientBuilderMap, List<ToolCallback> tools) {
        this.chatClientBuilderMap = chatClientBuilderMap;
        this.tools = tools;
    }

    @Override
    public String getKind() {
        return DoctorSubagentDefinition.IDENTITY.name();
    }

    @Override
    public String execute(TaskCall taskCall, Map<String, Object> context, SubagentDefinition subagent, Consumer<String> onChunk) {

        var doctorSubagent = (DoctorSubagentDefinition) subagent;
        var taskChatClient = this.createTaskChatClient((ModelTypeEnum) context.get("model"));

        String sessionId = (String) context.get("sessionId");

        if (onChunk != null) {
            StringBuilder fullResult = new StringBuilder();
            AtomicBoolean stopped = new AtomicBoolean(false);

            taskChatClient.prompt()
                    .system(doctorSubagent.content())
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
                            log.error("Doctor子智能体流式执行失败 - Status: {}, Body: {}", webEx.getStatusCode(), webEx.getResponseBodyAsString(), e);
                            onChunk.accept("\n[错误] " + webEx.getStatusCode() + ": " + webEx.getResponseBodyAsString());
                        } else {
                            log.error("Doctor子智能体流式执行失败", e);
                            onChunk.accept("\n[错误] " + e.getMessage());
                        }
                    })
                    .blockLast();

            return "agent_id: " + sessionId + "\n\n" + fullResult;
        }

        String result = taskChatClient.prompt()
                .system(doctorSubagent.content())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .user(taskCall.prompt())
                .call()
                .content();

        return "agent_id: " + sessionId + "\n\n" + result;
    }

    private ChatClient createTaskChatClient(ModelTypeEnum model) {
        ChatClient.Builder builder = this.chatClientBuilderMap.get(model).clone();
        if (this.tools != null && !this.tools.isEmpty()) {
            builder.defaultToolCallbacks(this.tools);
        }
        return builder.build();
    }
}
