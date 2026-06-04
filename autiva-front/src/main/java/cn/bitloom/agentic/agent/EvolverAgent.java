package cn.bitloom.agentic.agent;

import cn.bitloom.agentic.event.EventType;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.evolve.EvolutionEngine;
import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.gene.GeneStore;
import cn.bitloom.agentic.session.MessageChannel;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.tool.core.AskUserQuestionTool;
import cn.bitloom.agentic.tool.core.TodoWriteTool;
import cn.bitloom.agentic.tool.core.WebFetchTool;
import cn.bitloom.agentic.tool.manage.EvolveConfigTool;
import cn.bitloom.agentic.tool.manage.EvolveCycleTool;
import cn.bitloom.agentic.tool.manage.EvolveGeneManageTool;
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
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class EvolverAgent extends AbstractAgent {

    @Resource
    private EvolutionEngine evolutionEngine;

    @Resource
    private GeneStore geneStore;

    @Resource
    private EvolveConfig evolveConfig;

    @Override
    protected AgentIdentityEnum getIdentity() {
        return AgentIdentityEnum.EVOLVER;
    }

    @Override
    protected Set<EventType> getHandledEventTypes() {
        return Set.of(EventType.MESSAGE, EventType.EVOLVE);
    }

    @Override
    protected Flux<Void> handleAgentEvent(EventType type, MessageEvent event) {
        if (type == EventType.EVOLVE) {
            return handleEvolve(event);
        }
        return Flux.empty();
    }

    private Flux<Void> handleEvolve(MessageEvent event) {
        String sessionId = event.getSessionId();
        Session session = sessionManager.getById(sessionId);
        if (session == null) {
            log.warn("[EvolverAgent] 进化处理：会话不存在, sessionId={}", sessionId);
            return Flux.empty();
        }

        String intent = event.getMessage() != null ? event.getMessage().getText() : "定期进化检查";
        log.info("[EvolverAgent] 进化处理：开始, sessionId={}, intent={}", sessionId, intent);

        UserMessage evolveMessage = UserMessage.builder()
                .text("[系统触发] " + intent)
                .metadata(Map.of("trigger", "evolve", "intent", intent))
                .build();

        String channelAwareConversationId = sessionId + "#" + MessageChannel.EVOLVE.name();

        return this.model(session.getModel())
                .prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, channelAwareConversationId)
                        .param("model", session.getModel()))
                .toolContext(Map.of("sessionId", sessionId, "model", session.getModel()))
                .toolCallbacks(this.getTools())
                .messages(evolveMessage)
                .stream()
                .chatResponse()
                .doOnComplete(() -> log.info("[EvolverAgent] 进化处理：完成, sessionId={}", sessionId))
                .doOnError(e -> log.error("[EvolverAgent] 进化处理失败: sessionId={}", sessionId, e))
                .then().flux();
    }

    @Override
    protected String getDefaultSystemPrompt() {
        return this.agentManager.buildSystemPrompt(getIdentity());
    }

    @Override
    protected List<ToolCallback> getTools() {
        return List.of(this.mcpToolCallbackProvider.getToolCallbacks());
    }

    @Override
    protected List<ToolCallback> getDefaultTools() {

        WebFetchTool webFetchTool = WebFetchTool.builder().build();
        WebSearchTool webSearchTool = WebSearchTool.builder(new BochaSearchProvider(configManager.getBochaApiKey())).build();
        AskUserQuestionTool askUserQuestionTool = AskUserQuestionTool.builder()
                .questionHandler(new GuiQuestionHandler(this.toolUIBridge))
                .build();
        TodoWriteTool todoWriteTool = TodoWriteTool.builder()
                .todoEventHandler(new GuiTodoEventHandler(this.toolUIBridge))
                .build();
        EvolveQueryTool evolveQueryTool = EvolveQueryTool.builder()
                .evolutionEngine(evolutionEngine)
                .build();
        EvolveCycleTool evolveCycleTool = EvolveCycleTool.builder()
                .evolutionEngine(evolutionEngine)
                .sessionManager(this.sessionManager)
                .build();
        EvolveGeneManageTool evolveGeneManageTool = EvolveGeneManageTool.builder()
                .geneStore(geneStore)
                .build();
        EvolveConfigTool evolveConfigTool = EvolveConfigTool.builder()
                .evolveConfig(evolveConfig)
                .build();
        AutoMemoryTools autoMemoryTools = AutoMemoryTools.builder()
                .memoriesDir(AppConstants.Base.WORKSPACE_DIR.resolve(getIdentity().name()).resolve("memories"))
                .build();

        return List.of(ToolCallbacks.from(webFetchTool, askUserQuestionTool, todoWriteTool, webSearchTool, evolveQueryTool, autoMemoryTools, evolveCycleTool, evolveGeneManageTool, evolveConfigTool));
    }

}
