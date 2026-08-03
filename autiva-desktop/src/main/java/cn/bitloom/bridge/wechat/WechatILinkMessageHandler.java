package cn.bitloom.bridge.wechat;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentDefinitionManager;
import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.agent.advisor.AutoMemoryToolsAdvisor;
import cn.bitloom.agentic.agent.advisor.SessionMemoryAdvisor;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.session.CreateSessionRequest;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.agentic.session.MessageFilter;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.session.SessionTypeEnum;
import cn.bitloom.agentic.session.compaction.RecursiveSummarizationCompactionStrategy;
import cn.bitloom.agentic.session.compaction.TokenCountTrigger;
import cn.bitloom.agentic.tool.Toolkit;
import cn.bitloom.agentic.tool.session.ConversationSearchTool;
import cn.bitloom.agentic.tool.session.CrossSessionSearchTool;
import cn.bitloom.bridge.wechat.ilink.model.MessageItem;
import cn.bitloom.bridge.wechat.ilink.model.WeixinMessage;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.store.Store;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WechatILinkMessageHandler {

    private static final String SOURCE = "wechat";
    private static final String DEFAULT_AGENT_ID = "work";
    private final FileSystemSessionManager fileSystemSessionManager;
    private final WechatILinkClient wechatILinkClient;
    private final AgentDefinitionManager definitionManager;
    private final ModelFactory modelFactory;
    private final Toolkit toolkit;
    private final Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    public WechatILinkMessageHandler(@Lazy FileSystemSessionManager fileSystemSessionManager,
                                     @Lazy WechatILinkClient wechatILinkClient,
                                     @Lazy AgentDefinitionManager definitionManager,
                                     @Lazy ModelFactory modelFactory,
                                     @Lazy Toolkit toolkit) {
        this.fileSystemSessionManager = fileSystemSessionManager;
        this.wechatILinkClient = wechatILinkClient;
        this.definitionManager = definitionManager;
        this.modelFactory = modelFactory;
        this.toolkit = toolkit;
    }

    /**
     * 处理微信消息：获取/创建 session，直接调用 Agent.runStream，回复通过 wechatILinkClient 发送。
     */
    public void handleMessage(WeixinMessage message) {
        String userId = message.getFromUserId();
        String text = extractText(message);
        if (text == null || text.isBlank()) {
            return;
        }

        Session session = sessionMap.computeIfAbsent(userId, this::bindSession);
        MessageEvent inputEvent = MessageEvent.userMessage(session.id(), text.trim());

        fileSystemSessionManager.withLock(session.id(), () -> {
            try {
                Agent agent = buildAgent(session, DEFAULT_AGENT_ID);
                RuntimeContext ctx = RuntimeContext.builder()
                        .sessionId(session.id())
                        .userId(userId)
                        .build();
                agent.runStream(inputEvent, ctx)
                        .doOnNext(event -> {
                            if (event instanceof MessageEvent me
                                    && me.isAssistantMessage()
                                    && me.getText() != null
                                    && !me.getText().isBlank()) {
                                wechatILinkClient.sendText(userId, me.getText().trim());
                            }
                        })
                        .doOnError(e -> log.error("Wechat agent run error: userId={}", userId, e))
                        .blockLast();
            } catch (Exception e) {
                log.error("Wechat handleMessage error: userId={}", userId, e);
            }
            return null;
        });
    }

    /**
     * 为微信用户绑定一个 session
     */
    private Session bindSession(String userId) {
        String sessionId = "work-" + SessionTypeEnum.DM + "-" + SOURCE + "-" + userId + "-" + System.currentTimeMillis();
        CreateSessionRequest request = CreateSessionRequest.builder()
                .id(sessionId)
                .userId(userId)
                .build();
        Session session = fileSystemSessionManager.create(request);
        log.info("[Wechat] 绑定 session: userId={}, sessionId={}", userId, sessionId);
        return session;
    }

    /**
     * 构建 Agent（各调用方各自实现，不新建 AgentFactory）。
     */
    private Agent buildAgent(Session session, String agentId) {
        AgentDefinition definition = definitionManager.getOrLoadMainDefinition(agentId);
        ModelTypeEnum modelType = Store.selectedModel.get() != null ? Store.selectedModel.get() : ModelTypeEnum.DEEPSEEK;
        ChatModel chatModel = modelFactory.model(modelType);
        String uid = session.userId() != null ? session.userId() : "default-user";

        List<Advisor> advisors = new ArrayList<>();

        SessionMemoryAdvisor sessionMemoryAdvisor = SessionMemoryAdvisor.builder(fileSystemSessionManager)
                .defaultUserId(uid)
                .messageFilter(MessageFilter.byMessageType(MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL)
                        .and(MessageFilter.skipEmptyMessages()))
                .compactionTrigger(TokenCountTrigger.builder()
                        .threshold(100000)
                        .tokenCountEstimator(new JTokkitTokenCountEstimator())
                        .build())
                .compactionStrategy(RecursiveSummarizationCompactionStrategy.builder(
                                ChatClient.builder(chatModel).build())
                        .build())
                .build();
        advisors.add(sessionMemoryAdvisor);

        Path memoriesDir = AppConstants.Memory.workMemoryDir();
        AutoMemoryToolsAdvisor autoMemoryToolsAdvisor = AutoMemoryToolsAdvisor.builder()
                .memoriesRootDirectory(memoriesDir.toString())
                .build();
        advisors.add(autoMemoryToolsAdvisor);

        List<ToolCallback> allTools = new ArrayList<>(toolkit.buildToolCallbacks(definition));
        allTools.addAll(Arrays.asList(MethodToolCallbackProvider.builder()
                .toolObjects(ConversationSearchTool.builder(fileSystemSessionManager).build())
                .build()
                .getToolCallbacks()));
        allTools.addAll(Arrays.asList(MethodToolCallbackProvider.builder()
                .toolObjects(CrossSessionSearchTool.builder(fileSystemSessionManager, uid).build())
                .build()
                .getToolCallbacks()));

        Agent agent = Agent.builder()
                .name(agentId)
                .definition(definition)
                .model(chatModel)
                .systemPrompt(definition.content())
                .tools(allTools)
                .hooks(List.of())
                .advisors(advisors)
                .build();
        log.info("构建微信智能体: agentId={}", agentId);
        return agent;
    }

    private String extractText(WeixinMessage message) {
        if (message.getItemList() == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (MessageItem item : message.getItemList()) {
            if (item.getTextItem() != null && item.getTextItem().getText() != null) {
                sb.append(item.getTextItem().getText());
            }
        }
        return sb.toString();
    }
}
