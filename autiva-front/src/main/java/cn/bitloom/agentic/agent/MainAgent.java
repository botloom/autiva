package cn.bitloom.agentic.agent;

import cn.bitloom.agentic.advisor.LoggingAdvisor;
import cn.bitloom.agentic.agent.subagent.code.CodeSubagentType;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.session.SessionManager;
import cn.bitloom.agentic.session.SessionRespTypeEnum;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.task.TaskManager;
import cn.bitloom.agentic.tool.*;
import cn.bitloom.agentic.util.GuiQuestionHandler;
import cn.bitloom.agentic.util.GuiTodoEventHandler;
import cn.bitloom.config.ConfigManager;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.cron.CronManager;
import cn.bitloom.store.ToolUIBridge;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class MainAgent {

    private final EnumMap<ModelTypeEnum, ChatClient> chatClientMap = new EnumMap<>(ModelTypeEnum.class);

    @Resource
    private ChatClient.Builder deepSeekChatClientBuilder;
    @Resource
    private ChatClient.Builder zhiPuChatClientBuilder;
    private final SkillManager skillManager;
    private final ChatMemory chatMemory;
    private final LoggingAdvisor loggingAdvisor;
    private final ToolCallingManager toolCallingManager;
    private final ConfigManager configManager;
    private final SessionManager sessionManager;
    private final AgentManager agentManager;
    private final ToolUIBridge toolUIBridge;
    private final TaskManager taskManager;
    private final CronManager cronManager;

    @PostConstruct
    public void init() {
        StTemplateRenderer stTemplateRenderer = StTemplateRenderer.builder()
                .startDelimiterToken('$')
                .endDelimiterToken('$')
                .build();
        String systemPrompt = this.getSystemPrompt();

        FileSystemTools fileSystemTools = FileSystemTools.builder().build();
        ShellTools shellTools = ShellTools.builder().build();
        WebFetchTool webFetchTool = WebFetchTool.builder(deepSeekChatClientBuilder.clone().build()).build();
        AskUserQuestionTool askUserQuestionTool = AskUserQuestionTool.builder()
                .questionHandler(new GuiQuestionHandler(this.toolUIBridge))
                .build();
        TodoWriteTool todoWriteTool = TodoWriteTool.builder()
                .todoEventHandler(new GuiTodoEventHandler(this.toolUIBridge))
                .build();
        CronTool cronTool = CronTool.builder(this.cronManager).build();

        this.taskManager.registerSubagentTypes(CodeSubagentType.builder()
                .chatClientBuilders(
                        Map.of(
                                ModelTypeEnum.DEEPSEEK.name(), deepSeekChatClientBuilder.clone(),
                                "default", zhiPuChatClientBuilder.clone()
                        )
                )
                .skillManager(this.skillManager)
                .build());
        Consumer<ChatClient.AdvisorSpec> advisorSpecConsumer = a -> a.advisors(
                MessageChatMemoryAdvisor.builder(this.chatMemory).build(),
                this.loggingAdvisor,
                ToolCallAdvisor.builder()
                        .toolCallingManager(toolCallingManager)
                        .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 300)
                        .conversationHistoryEnabled(true)
                        .disableMemory()
                        .build()
        );

        this.chatClientMap.put(
                ModelTypeEnum.DEEPSEEK,
                deepSeekChatClientBuilder
                        .defaultTemplateRenderer(stTemplateRenderer)
                        .defaultSystem(systemPrompt)
                        .defaultTools(
                                fileSystemTools,
                                shellTools,
                                webFetchTool,
                                askUserQuestionTool,
                                todoWriteTool,
                                cronTool
                        )
                        .defaultToolCallbacks(this.taskManager.buildToolCallbacks())
                        .defaultToolCallbacks(this.skillManager.buildToolCallback())
                        .defaultAdvisors(advisorSpecConsumer)
                        .build()
        );
        this.chatClientMap.put(
                ModelTypeEnum.GLM,
                zhiPuChatClientBuilder
                        .defaultTemplateRenderer(stTemplateRenderer)
                        .defaultSystem(systemPrompt)
                        .defaultTools(
                                fileSystemTools,
                                shellTools,
                                webFetchTool,
                                askUserQuestionTool,
                                todoWriteTool,
                                cronTool
                        )
                        .defaultToolCallbacks(this.taskManager.buildToolCallbacks())
                        .defaultToolCallbacks(this.skillManager.buildToolCallback())
                        .defaultAdvisors(advisorSpecConsumer)
                        .build()
        );
        this.run();
    }

    public void run() {
        EventBus.inBoxSubscribe()
                .concatMap(event -> {
                    Session session = sessionManager.getById(event.getSessionId());
                    if (session.getRespType().equals(SessionRespTypeEnum.STREAM)) {
                        return this.model(session.getModel())
                                .prompt()
                                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, event.getSessionId()))
                                .toolContext(Map.of("sessionId", event.getSessionId()))
                                .messages(event.getMessage())
                                .stream()
                                .chatResponse()
                                .publishOn(Schedulers.boundedElastic())
                                .doOnNext(message -> EventBus.outBoxPublish(event.getSessionId(), message.getResult().getOutput()));
                    } else {
                        ChatResponse chatResponse = this.model(session.getModel())
                                .prompt()
                                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, event.getSessionId()))
                                .toolContext(Map.of("sessionId", event.getSessionId()))
                                .messages(event.getMessage())
                                .call()
                                .chatResponse();
                        if (chatResponse != null) {
                            EventBus.outBoxPublish(event.getSessionId(), chatResponse.getResult().getOutput());
                        }
                        return Flux.empty();
                    }
                })
                .subscribe();
    }

    private AgentIdentityEnum getIdentity() {
        return AgentIdentityEnum.MAIN;
    }

    private String getSystemPrompt() {
        StringBuilder sb = new StringBuilder();

        String workspaceContext = this.agentManager.getDescription(this.getIdentity().name());
        if (!workspaceContext.isBlank()) {
            sb.append(workspaceContext);
        }

        sb.append("""
                
                # 运行环境
                
                - 工作目录: %s
                - 当前时间: $time$
                - 智能体: %s
                """.formatted(
                AppConstants.Base.WORKSPACE_DIR.resolve(this.getIdentity().name()),
                this.getIdentity()
        ));

        String skillDesc = this.skillManager.getDescription();
        if (!skillDesc.isBlank()) {
            sb.append("\n# 可用技能\n\n").append(skillDesc).append("\n");
        }

        return sb.toString();
    }

    private ChatClient model(ModelTypeEnum model) {
        return this.chatClientMap.getOrDefault(model, this.chatClientMap.get(ModelTypeEnum.GLM));
    }

}
