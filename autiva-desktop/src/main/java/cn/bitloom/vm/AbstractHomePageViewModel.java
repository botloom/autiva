package cn.bitloom.vm;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentDefinitionManager;
import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.agent.advisor.AutoMemoryToolsAdvisor;
import cn.bitloom.agentic.agent.advisor.SessionMemoryAdvisor;
import cn.bitloom.agentic.agent.advisor.SkillContextAdvisor;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.event.AbstractEvent;
import cn.bitloom.agentic.event.DiffEvent;
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
import cn.bitloom.constant.AppConstants;
import cn.bitloom.constant.AgentMode;
import cn.bitloom.project.ProjectInfo;
import cn.bitloom.node.message.AssistantMessageCard;
import cn.bitloom.node.message.MessageCard;
import cn.bitloom.node.message.ToolMessageCard;
import cn.bitloom.node.message.UserMessageCard;
import cn.bitloom.store.Store;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 首页 ViewModel 抽象基类。
 * <p>
 * 包含通用的会话管理、消息发送、agent 直接调用逻辑。
 * 子类（CoderHomePageViewModel / WorkHomePageViewModel）实现模式专有逻辑。
 * <p>
 * 直接调用 Agent.runStream，事件流通过 Flux.create 汇聚，
 * 工具事件通过 EventPublisher 推入同一 sink。
 * per-session 锁保证同一 session 的串行处理。
 */
@Slf4j
public abstract class AbstractHomePageViewModel {

    protected final FileSystemSessionManager sessionManager;
    protected final AgentDefinitionManager definitionManager;
    protected final ModelFactory modelFactory;
    protected final Toolkit toolkit;
    protected final SkillManager skillManager;

    @Getter
    private final ObservableList<MessageCard> messages = FXCollections.observableArrayList();

    protected Session session;
    protected AssistantMessageCard currentAssistantCard = null;

    /** 当前 agent 流订阅（用于 pause 取消） */
    protected Disposable currentSubscription;
    /** 取消信号（用于 pauseGeneration 中断 blockLast） */
    protected Sinks.Empty<Object> cancelSink;
    /** Diff 事件处理器（Coder 模式注入，Work 模式为 null） */
    @Setter
    protected Consumer<DiffEvent> diffHandler;

    /**
     * 历史消息加载标志：prepareHistoricalMessages 期间为 true，用于跳过工具消息（历史工具不显示）
     */
    protected boolean isLoadingHistory = false;

    /**
     * 待响应的工具调用卡片，按 toolCallId 索引。
     * TOOL_CALLS 事件创建卡片并缓存，tool response 事件按 id 取出并追加结果。
     */
    private final Map<String, ToolMessageCard> pendingToolCards = new ConcurrentHashMap<>();

    protected AbstractHomePageViewModel(FileSystemSessionManager sessionManager,
                                        AgentDefinitionManager definitionManager,
                                        ModelFactory modelFactory,
                                        Toolkit toolkit,
                                        SkillManager skillManager) {
        this.sessionManager = sessionManager;
        this.definitionManager = definitionManager;
        this.modelFactory = modelFactory;
        this.toolkit = toolkit;
        this.skillManager = skillManager;
    }

    public void createNewSession() {
        this.session = null;
        Store.currentSessionId.set("");
        messages.clear();
        currentAssistantCard = null;
        Store.isStreaming.set(false);
        Store.isPaused.set(false);
        cancelCurrentSubscription();
    }

    public void switchToSession(String sessionId) {
        if (this.session != null && sessionId.equals(this.session.id())) {
            return;
        }

        Session targetSession = sessionManager.getById(sessionId);
        if (targetSession == null) {
            log.warn("切换到不存在的session: {}", sessionId);
            return;
        }

        this.session = targetSession;
        Store.currentSessionId.set(sessionId);

        messages.clear();
        currentAssistantCard = null;
        Store.isStreaming.set(false);
        Store.isPaused.set(false);

        // 子类恢复模式专有状态（如 coder 从 metadata 恢复 currentProject）
        onSessionSwitched(targetSession);

        if (hasHistoricalMessages()) {
            prepareHistoricalMessages();
        }
    }

    public void switchAgent(String agentId) {
        // 在旧 viewModel 上清理模式专有状态（如 coder 清空 currentProject）
        onSwitchAgent(agentId);
        // 触发 HomePageRouter.switchMode，由它在切换后对新 viewModel 调用 createNewSession
        Store.currentAgent.set(agentId);
    }

    public void prepareHistoricalMessages() {
        // 从 events.jsonl 加载所有未压缩的历史事件（压缩字段已弃用，默认从头加载）
        List<AbstractEvent> events = sessionManager.getEvents(this.session.id());
        if (events.isEmpty()) {
            return;
        }

        // 历史加载期间跳过工具消息（历史工具不显示）
        isLoadingHistory = true;
        try {
            // 分批渲染避免阻塞 FX 线程
            int batchSize = 20;
            for (int i = 0; i < events.size(); i += batchSize) {
                int end = Math.min(i + batchSize, events.size());
                List<AbstractEvent> batch = new ArrayList<>(events.subList(i, end));
                boolean isLastBatch = end == events.size();
                if (i == 0) {
                    // 首批同步处理
                    for (AbstractEvent event : batch) {
                        if (event instanceof MessageEvent me) {
                            processMessageEvent(me);
                        }
                    }
                    if (isLastBatch) {
                        isLoadingHistory = false;
                    }
                } else {
                    Platform.runLater(() -> {
                        for (AbstractEvent event : batch) {
                            if (event instanceof MessageEvent me) {
                                processMessageEvent(me);
                            }
                        }
                        if (isLastBatch) {
                            isLoadingHistory = false;
                        }
                    });
                }
            }
        } catch (Exception e) {
            isLoadingHistory = false;
            throw e;
        }
    }

    public boolean hasHistoricalMessages() {
        return this.session != null
                && !sessionManager.getEvents(this.session.id()).isEmpty();
    }

    // ===== Agent 构建 =====

    /**
     * 获取当前项目（code 模式由子类提供，work 模式返回 null）。
     */
    protected ProjectInfo getCurrentProject() {
        return null;
    }

    /**
     * 根据 mode 和 project 决定 memory 根目录。
     */
    protected Path resolveMemoryDir(String agentId, ProjectInfo project) {
        AgentMode mode = AgentMode.fromAgentId(agentId);
        if (mode == AgentMode.WORK) {
            return AppConstants.Memory.workMemoryDir();
        }
        // code 模式：project 必须存在（code 模式必须选择项目）
        if (project == null) {
            throw new IllegalStateException("code 模式必须先选择项目");
        }
        return AppConstants.Memory.projectMemoryDir(project.name());
    }

    /**
     * 生成 sessionId（code 模式编码 projectName）。
     */
    protected String buildSessionId(String agentId) {
        AgentMode mode = AgentMode.fromAgentId(agentId);
        if (mode == AgentMode.CODER) {
            ProjectInfo project = getCurrentProject();
            if (project == null) {
                throw new IllegalStateException("code 模式必须先选择项目");
            }
            return "code-" + project.name() + "-" + SessionTypeEnum.DM + "-" + "desktopApp" + "-" + Store.userId.get() + "-" + System.currentTimeMillis();
        }
        return "work-" + SessionTypeEnum.DM + "-" + "desktopApp" + "-" + Store.userId.get() + "-" + System.currentTimeMillis();
    }

    /**
     * 构建 systemPrompt（code 模式注入全局规则和项目规则）。
     */
    protected String buildSystemPrompt(String agentId, AgentDefinition definition) {
        String systemPrompt = definition.content();
        if (AgentMode.fromAgentId(agentId) != AgentMode.CODER) {
            return systemPrompt;
        }
        try {
            Path globalRules = AppConstants.Rules.codeGlobalRulesFile();
            if (Files.exists(globalRules)) {
                systemPrompt += "\n\n# 全局规则\n" + Files.readString(globalRules);
            }
        } catch (Exception e) {
            log.warn("读取全局规则失败: {}", AppConstants.Rules.codeGlobalRulesFile(), e);
        }
        ProjectInfo project = getCurrentProject();
        if (project != null) {
            try {
                Path projectRules = Path.of(project.path()).resolve("AUTIVA.md");
                if (Files.exists(projectRules)) {
                    systemPrompt += "\n\n# 项目规则\n" + Files.readString(projectRules);
                }
            } catch (Exception e) {
                log.warn("读取项目规则失败: {}", project.path(), e);
            }
        }
        return systemPrompt;
    }

    /**
     * 构建 Agent。各调用方各自实现，不新建 AgentFactory。
     */
    protected Agent buildAgent(Session session, String agentId) {
        AgentDefinition definition = definitionManager.getOrLoadMainDefinition(agentId);
        ModelTypeEnum modelType = Store.selectedModel.get() != null ? Store.selectedModel.get() : ModelTypeEnum.DEEPSEEK;
        ChatModel chatModel = modelFactory.model(modelType);
        String uid = session.userId() != null ? session.userId() : "default-user";

        List<Advisor> advisors = new ArrayList<>();

        SessionMemoryAdvisor sessionMemoryAdvisor = SessionMemoryAdvisor.builder(sessionManager)
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

        Path memoriesDir = resolveMemoryDir(agentId, getCurrentProject());
        AutoMemoryToolsAdvisor autoMemoryToolsAdvisor = AutoMemoryToolsAdvisor.builder()
                .memoriesRootDirectory(memoriesDir.toString())
                .build();
        advisors.add(autoMemoryToolsAdvisor);

        advisors.add(SkillContextAdvisor.builder().skillManager(skillManager).build());

        List<ToolCallback> allTools = new ArrayList<>(toolkit.buildToolCallbacks(definition));
        allTools.add(ConversationSearchTool.builder(sessionManager).build().toToolCallback());
        allTools.add(CrossSessionSearchTool.builder(sessionManager, uid).build().toToolCallback());

        Agent agent = Agent.builder()
                .name(agentId)
                .definition(definition)
                .model(chatModel)
                .systemPrompt(buildSystemPrompt(agentId, definition))
                .tools(allTools)
                .hooks(List.of())
                .advisors(advisors)
                .build();
        log.info("构建智能体: agentId={}", agentId);
        return agent;
    }

    // ===== 发送消息 =====

    public void sendMessage(String text) {
        if (this.session == null) {
            String agentId = Store.currentAgent.get();
            String sessionId = buildSessionId(agentId);
            CreateSessionRequest.Builder builder = CreateSessionRequest.builder()
                    .id(sessionId)
                    .userId(Store.userId.get() != null ? Store.userId.get() : "default-user");
            ProjectInfo project = getCurrentProject();
            if (project != null) {
                builder.metadata("projectId", project.id());
                builder.metadata("projectName", project.name());
            }
            this.session = sessionManager.create(builder.build());
            Store.currentSessionId.set(this.session.id());
        }

        Store.isStreaming.set(true);
        Store.isPaused.set(false);
        cancelSink = Sinks.empty();

        // 子类实现消息上下文构建（coder 模式附加项目信息）
        String messageText = buildMessageWithContext(text);
        MessageEvent inputEvent = MessageEvent.userMessage(this.session.id(), messageText);
        final Session currentSession = this.session;
        final String agentId = Store.currentAgent.get();

        // 在后台线程执行，避免阻塞 FX 线程；per-session 锁保证串行
        CompletableFuture.runAsync(() -> {
            sessionManager.withLock(currentSession.id(), () -> {
                try {
                    Agent agent = buildAgent(currentSession, agentId);
                    RuntimeContext ctx = RuntimeContext.builder()
                            .sessionId(currentSession.id())
                            .userId(currentSession.userId())
                            .put("lastUserMessage", messageText)
                            .build();
                    currentSubscription = agent.runStream(inputEvent, ctx)
                            .takeUntilOther(cancelSink.asMono())
                            .doOnNext(event -> Platform.runLater(() -> processEvent(event)))
                            .doOnComplete(() -> Platform.runLater(() -> {
                                Store.isStreaming.set(false);
                                Store.isPaused.set(false);
                                safeFlush(currentSession.id());
                            }))
                            .doOnError(e -> {
                                log.error("agent run error", e);
                                Platform.runLater(() -> {
                                    Store.isStreaming.set(false);
                                    Store.isPaused.set(false);
                                });
                            })
                            .subscribe();
                } catch (Exception e) {
                    log.error("sendMessage error", e);
                    Platform.runLater(() -> {
                        Store.isStreaming.set(false);
                        Store.isPaused.set(false);
                    });
                }
                return null;
            });
        });

        // 触发侧边栏刷新（更新会话标题）
        Store.refreshHistory.set(!Store.refreshHistory.get());
    }

    private void safeFlush(String sessionId) {
        try {
            sessionManager.flush(sessionId);
        } catch (Exception ex) {
            log.error("[ViewModel] flush 失败: sessionId={}", sessionId, ex);
        }
    }

    // ===== 事件处理 =====

    /**
     * 处理事件流中的事件（可能是 MessageEvent 或 DiffEvent）。
     */
    private void processEvent(AbstractEvent event) {
        if (event instanceof MessageEvent messageEvent) {
            processMessageEvent(messageEvent);
        } else if (event instanceof DiffEvent diffEvent) {
            if (diffHandler != null) {
                diffHandler.accept(diffEvent);
            }
        }
    }

    private void processMessageEvent(MessageEvent event) {
        if (event.isUserMessage()) {
            processUserEvent(event);
        } else if (event.isAssistantMessage()) {
            processAssistantEvent(event);
        } else if (event.isToolResponse()) {
            processToolEvent(event);
        } else {
            log.warn("未处理的事件类型: {}", event.getEventType());
        }
    }

    private void processUserEvent(MessageEvent e) {
        currentAssistantCard = null;
        messages.add(new UserMessageCard(e.getText()));
    }

    private void processAssistantEvent(MessageEvent e) {
        String finishReason = e.getFinishReason();
        String text = e.getText();

        if (finishReason == null || finishReason.isBlank()) {
            // 流式 chunk：直接累积
            if (Store.isPaused.get()) {
                return;
            }
            if (currentAssistantCard == null) {
                currentAssistantCard = new AssistantMessageCard();
                messages.add(currentAssistantCard);
            }
            currentAssistantCard.appendContent(text);
        } else if ("STOP".equals(finishReason)) {
            // 结束流式
            Store.isStreaming.set(false);
            Store.isPaused.set(false);

            if (currentAssistantCard != null) {
                currentAssistantCard.complete("STOP");
                if (currentAssistantCard.isValid()) {
                    messages.remove(currentAssistantCard);
                }
                currentAssistantCard = null;
            } else if (text != null && !text.isBlank()) {
                // 非流式消息（历史消息或一次性输出）
                messages.add(new AssistantMessageCard(text, "STOP"));
            }
        } else if ("TOOL_CALLS".equals(finishReason)) {
            // 工具调用：结束当前流式消息
            if (currentAssistantCard != null) {
                currentAssistantCard.complete("TOOL_CALLS");
                if (currentAssistantCard.isValid()) {
                    messages.remove(currentAssistantCard);
                }
                currentAssistantCard = null;
            }

            // 创建工具调用卡片（历史加载期间跳过：历史工具不显示）
            if (e.getToolCalls() != null && !isLoadingHistory) {
                for (MessageEvent.ToolCallInfo tc : e.getToolCalls()) {
                    ToolMessageCard card = new ToolMessageCard(tc.id(), tc.name(), tc.arguments());
                    pendingToolCards.put(tc.id(), card);
                    messages.add(card);
                }
            }
        }
    }

    private void processToolEvent(MessageEvent e) {
        // 历史加载期间跳过工具响应消息（历史工具不显示）
        if (isLoadingHistory) {
            return;
        }
        if (e.getResponses() != null && !e.getResponses().isEmpty()) {
            for (MessageEvent.ToolResponseInfo resp : e.getResponses()) {
                ToolMessageCard card = pendingToolCards.remove(resp.id());
                if (card != null) {
                    card.setResponse(resp.responseData());
                }
            }
        }
    }

    public void addUserMessage(String text) {
        messages.add(new UserMessageCard(text));
    }

    public void clear() {
        messages.clear();
        currentAssistantCard = null;
        pendingToolCards.clear();
        Store.isStreaming.set(false);
        Store.isPaused.set(false);
        if (this.session != null) {
            sessionManager.remove(this.session.id());
            sessionManager.persistSession(this.session);
        }
    }

    public void pauseGeneration() {
        if (Store.isStreaming.get() && !Store.isPaused.get()) {
            Store.isPaused.set(true);
            cancelCurrentSubscription();

            if (currentAssistantCard != null) {
                currentAssistantCard.setStreaming(false);
                currentAssistantCard = null;
            }
        }
    }

    /**
     * 取消当前订阅和取消信号。
     */
    private void cancelCurrentSubscription() {
        if (cancelSink != null) {
            cancelSink.tryEmitEmpty();
            cancelSink = null;
        }
        if (currentSubscription != null && !currentSubscription.isDisposed()) {
            currentSubscription.dispose();
        }
        currentSubscription = null;
    }

    /**
     * 释放资源（模式切换时调用，取消事件订阅）。
     * 子类可 override 扩展清理逻辑，但必须 super.dispose()。
     */
    public void dispose() {
        cancelCurrentSubscription();
        this.currentAssistantCard = null;
    }

    // ===== 抽象方法：子类实现模式专有逻辑 =====

    /**
     * 构建带上下文的消息文本。
     * coder 模式附加项目信息前缀，work 模式直接返回原文。
     */
    protected abstract String buildMessageWithContext(String text);

    /**
     * 模式切换时的专有逻辑。
     * coder 模式：非 coder 时清空 currentProject。
     * work 模式：空实现。
     */
    protected abstract void onSwitchAgent(String agentId);

    /**
     * 会话切换后的专有逻辑。
     * coder 模式：从 session.metadata 恢复 currentProject。
     * work 模式：默认空实现。
     */
    protected void onSessionSwitched(Session session) {
        // 默认空实现，子类可重写
    }
}
