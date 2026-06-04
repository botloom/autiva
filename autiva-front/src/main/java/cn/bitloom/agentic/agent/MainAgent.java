package cn.bitloom.agentic.agent;

import cn.bitloom.agentic.agent.subagent.doctor.DoctorSubagentType;
import cn.bitloom.agentic.agent.subagent.generic.GenericSubagentReferences;
import cn.bitloom.agentic.agent.subagent.generic.GenericSubagentType;
import cn.bitloom.agentic.event.EventType;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.evolve.EvolutionEngine;
import cn.bitloom.agentic.memory.JournalManager;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.session.MessageChannel;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.task.repository.DefaultTaskRepository;
import cn.bitloom.agentic.tool.command.CommandTools;
import cn.bitloom.agentic.tool.core.*;
import cn.bitloom.agentic.tool.cron.CronTool;
import cn.bitloom.agentic.tool.manage.EvolveApplyTool;
import cn.bitloom.agentic.tool.manage.EvolveQueryTool;
import cn.bitloom.agentic.tool.memory.AutoMemoryTools;
import cn.bitloom.agentic.tool.serach.BochaSearchProvider;
import cn.bitloom.agentic.tool.serach.WebSearchTool;
import cn.bitloom.agentic.util.GuiQuestionHandler;
import cn.bitloom.agentic.util.GuiTodoEventHandler;
import cn.bitloom.constant.AppConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class MainAgent extends AbstractAgent {

    @Resource
    private EvolutionEngine evolutionEngine;

    @Resource
    private JournalManager journalManager;

    @Override
    protected AgentIdentityEnum getIdentity() {
        return AgentIdentityEnum.MAIN;
    }

    @Override
    protected Set<EventType> getHandledEventTypes() {
        return Set.of(EventType.MESSAGE, EventType.MEMORY_CONSOLIDATE, EventType.JOURNAL);
    }

    @Override
    protected Flux<Void> handleAgentEvent(EventType type, MessageEvent event) {
        return switch (type) {
            case MEMORY_CONSOLIDATE -> handleMemoryConsolidate(event);
            case JOURNAL -> handleJournal(event);
            default -> Flux.empty();
        };
    }

    private Flux<Void> handleMemoryConsolidate(MessageEvent event) {
        String sessionId = event.getSessionId();
        Session session = sessionManager.getById(sessionId);
        if (session == null) {
            log.warn("[MainAgent] 记忆整理：会话不存在, sessionId={}", sessionId);
            return Flux.empty();
        }

        int cursor = session.getMemoryCursor() != null ? session.getMemoryCursor() : 0;
        List<Message> userMessages = session.getChannelMessages(MessageChannel.USER);
        if (cursor >= userMessages.size()) {
            log.debug("[MainAgent] 记忆整理：无新消息需要整理, sessionId={}, cursor={}", sessionId, cursor);
            return Flux.empty();
        }

        List<Message> unprocessed = userMessages.subList(cursor, userMessages.size());

        log.info("[MainAgent] 记忆整理：开始处理, sessionId={}, cursor={}, unprocessedCount={}",
                sessionId, cursor, unprocessed.size());

        UserMessage consolidateMessage = UserMessage.builder()
                .text("[系统触发] 请整理以下会话片段的记忆，提取关键信息并保存到记忆文件中。")
                .metadata(Map.of("trigger", "memory_consolidate", "cursor", cursor))
                .build();

        String channelAwareConversationId = sessionId + "#" + MessageChannel.MEMORY.name();

        return this.model(session.getModel())
                .prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, channelAwareConversationId)
                        .param("model", session.getModel()))
                .toolContext(Map.of("sessionId", sessionId, "model", session.getModel()))
                .toolCallbacks(this.getTools())
                .messages(consolidateMessage)
                .stream()
                .chatResponse()
                .doOnComplete(() -> {
                    sessionManager.updateCursor(sessionId, "memoryCursor", userMessages.size());
                    log.info("[MainAgent] 记忆整理：完成, sessionId={}, newCursor={}", sessionId, userMessages.size());
                })
                .doOnError(e -> log.error("[MainAgent] 记忆整理失败: sessionId={}", sessionId, e))
                .then().flux();
    }

    private Flux<Void> handleJournal(MessageEvent event) {
        String sessionId = event.getSessionId();
        Session session = sessionManager.getById(sessionId);
        if (session == null) {
            log.warn("[MainAgent] 日记处理：会话不存在, sessionId={}", sessionId);
            return Flux.empty();
        }

        String summary = event.getMessage() != null ? event.getMessage().getText() : "";
        if (summary == null || summary.isBlank()) {
            return Flux.empty();
        }

        log.info("[MainAgent] 日记处理：开始, sessionId={}", sessionId);

        journalManager.appendFromSession(sessionId, summary);
        sessionManager.updateCursor(sessionId, "journalCursor", session.getJournalCursor() + 1);

        return Flux.empty();
    }

    @Override
    protected String getDefaultSystemPrompt() {
        return this.agentManager.buildSystemPrompt(getIdentity());
    }

    @Override
    protected List<ToolCallback> getDefaultTools() {
        WebFetchTool webFetchTool = WebFetchTool.builder().build();
        WebSearchTool webSearchTool = WebSearchTool.builder(new BochaSearchProvider(configManager.getBochaApiKey())).build();
        CommandTools commandTools = CommandTools.builder().build();
        AskUserQuestionTool askUserQuestionTool = AskUserQuestionTool.builder()
                .questionHandler(new GuiQuestionHandler(this.toolUIBridge))
                .build();
        TodoWriteTool todoWriteTool = TodoWriteTool.builder()
                .todoEventHandler(new GuiTodoEventHandler(this.toolUIBridge))
                .build();
        CronTool cronTool = CronTool.builder(this.cronManager).build();
        EvolveQueryTool evolveQueryTool = EvolveQueryTool.builder()
                .evolutionEngine(evolutionEngine)
                .build();
        EvolveApplyTool evolveApplyTool = EvolveApplyTool.builder()
                .evolutionEngine(evolutionEngine)
                .build();
        AutoMemoryTools autoMemoryTools = AutoMemoryTools.builder()
                .memoriesDir(AppConstants.Base.WORKSPACE_DIR.resolve(getIdentity().name()).resolve("memories"))
                .build();

        DefaultTaskRepository defaultTaskRepository = new DefaultTaskRepository();
        ToolCallback taskToolCallBack = TaskTool.builder()
                .taskRepository(defaultTaskRepository)
                .sessionManager(this.sessionManager)
                .toolUIBridge(this.toolUIBridge)
                .subagentTypes(
                        GenericSubagentType.builder()
                                .chatClientBuilder(
                                        ModelTypeEnum.DEEPSEEK,
                                        chatClientBuilderFactory.model(ModelTypeEnum.DEEPSEEK).clone()
                                )
                                .bochaApiKey(configManager.getBochaApiKey())
                                .skillManager(this.skillManager)
                                .build(),
                        DoctorSubagentType.builder()
                                .chatClientBuilder(
                                        ModelTypeEnum.DEEPSEEK,
                                        chatClientBuilderFactory.model(ModelTypeEnum.DEEPSEEK).clone()
                                )
                                .skillManager(this.skillManager)
                                .agentManager(this.agentManager)
                                .configManager(this.configManager)
                                .toolUIBridge(this.toolUIBridge)
                                .build()
                )
                .subagentReferences(GenericSubagentReferences.fromSubagentDirectories(AppConstants.Base.WORKSPACE_DIR))
                .build();
        ToolCallback taskOutputToolCallback = TaskOutputTool.builder()
                .taskRepository(defaultTaskRepository)
                .build();

        List<ToolCallback> toolCallbacks = new ArrayList<>(List.of(ToolCallbacks.from(webFetchTool, askUserQuestionTool, todoWriteTool, cronTool, webSearchTool, commandTools, evolveQueryTool, evolveApplyTool, autoMemoryTools)));
        toolCallbacks.add(taskToolCallBack);
        toolCallbacks.add(taskOutputToolCallback);
        return toolCallbacks;
    }

    @Override
    protected List<ToolCallback> getTools() {
        ArrayList<ToolCallback> toolCallbacks = new ArrayList<>();
        ToolCallback skillToolCallback = this.skillManager.buildToolCallback();
        if (skillToolCallback != null) {
            toolCallbacks.add(skillToolCallback);
        }
        toolCallbacks.addAll(List.of(this.mcpToolCallbackProvider.getToolCallbacks()));
        return toolCallbacks;
    }

}
