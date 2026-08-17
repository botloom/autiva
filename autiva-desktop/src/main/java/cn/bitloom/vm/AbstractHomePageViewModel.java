package cn.bitloom.vm;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentDefinitionManager;
import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.agent.advisor.AutoMemoryToolsAdvisor;
import cn.bitloom.agentic.agent.advisor.MemoryRecallAdvisor;
import cn.bitloom.agentic.agent.advisor.SessionMemoryAdvisor;
import cn.bitloom.agentic.agent.advisor.SkillContextAdvisor;
import cn.bitloom.agentic.agent.advisor.SubagentContextAdvisor;
import cn.bitloom.agentic.event.AbstractEvent;
import cn.bitloom.agentic.event.CompactionEvent;
import cn.bitloom.agentic.event.DiffEvent;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.hook.IAgentHook;
import cn.bitloom.agentic.hook.MemoryExtractionHook;
import cn.bitloom.agentic.hook.PermissionHook;
import cn.bitloom.agentic.hook.TodoReminderHook;
import cn.bitloom.agentic.hook.ToolCallBudgetHook;
import cn.bitloom.agentic.hook.ToolResultOffloadHook;
import cn.bitloom.agentic.memory.FileSystemAgentMemoryStore;
import cn.bitloom.agentic.memory.MemoryConsolidator;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.session.*;
import cn.bitloom.agentic.session.compaction.RecursiveSummarizationCompactionStrategy;
import cn.bitloom.agentic.session.compaction.StagedCompactionStrategy;
import cn.bitloom.agentic.session.compaction.TokenCountTrigger;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.tool.Toolkit;
import cn.bitloom.agentic.tool.command.ShellSession;
import cn.bitloom.agentic.tool.plan.ExitPlanModeTool;
import cn.bitloom.agentic.tool.session.ConversationSearchTool;
import cn.bitloom.agentic.tool.session.CrossSessionSearchTool;
import cn.bitloom.constant.AgentMode;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.node.message.*;
import cn.bitloom.project.ProjectInfo;
import cn.bitloom.store.Store;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 首页 ViewModel 抽象基类。
 * <p>
 * 包含通用的会话管理、消息发送、agent 直接调用逻辑。
 * 子类（CoderHomePageViewModel / WorkHomePageViewModel）实现模式专有逻辑。
 * <p>
 * <b>多 session 并发模型</b>：一个 session 与一个 Agent 实例 1:1 绑定（缓存在
 * {@link #sessionAgents}），同一 session 内的所有消息复用同一 Agent。
 * 每个 session 拥有独立的 {@link SessionRuntimeState}（订阅、流式状态、消息缓存等），
 * 切换活动 session 时原 session 的后台任务不被中断，切回可恢复完整进度。
 * <p>
 * UI 绑定的 {@link #messages} 是单一稳定引用，切换 session 时通过 setAll 替换内容；
 * 非 active session 的事件只更新对应 state 的 savedMessages，不污染 UI。
 * per-session 锁保证同一 session 的串行处理。
 */
@Slf4j
public abstract class AbstractHomePageViewModel {

    /** Plan Mode 工具白名单：只读探索 + 交互（ExitPlanMode 由 buildAgent 单独追加） */
    private static final Set<String> PLAN_MODE_ALLOWED = Set.of(
            "Read", "Glob", "Grep", "WebFetch", "WebSearch",
            "TodoWrite", "AskUserQuestion",
            "ConversationSearch", "CrossSessionSearch");

    protected final FileSystemSessionManager sessionManager;
    protected final AgentDefinitionManager definitionManager;
    protected final ModelFactory modelFactory;
    protected final Toolkit toolkit;
    protected final SkillManager skillManager;
    protected final List<cn.bitloom.agentic.permission.strategy.ToolApprovalStrategy> approvalStrategies;
    protected final cn.bitloom.config.ConfigManager configManager;
    protected final cn.bitloom.agentic.goal.GoalManager goalManager;
    protected final cn.bitloom.bridge.desktop.ToolUIBridge toolUIBridge;

    /** UI 绑定的稳定消息列表引用，切换 session 时通过 setAll 替换内容 */
    @Getter
    private final ObservableList<MessageCard> messages = FXCollections.observableArrayList();

    protected Session session;

    /** Diff 事件处理器（Coder 模式注入，Work 模式为 null） */
    @Setter
    protected Consumer<DiffEvent> diffHandler;

    /** per-session Agent 缓存：sessionId → Agent 实例，session 级生命周期，销毁时移除 */
    private final Map<String, Agent> sessionAgents = new ConcurrentHashMap<>();

    /**
     * per-session 运行时状态：订阅、流式状态、流式消息卡片、待响应工具卡片、消息缓存。
     * 切换 session 时仅切换 active 引用，不取消非 active session 的订阅。
     */
    private final Map<String, SessionRuntimeState> sessionStates = new ConcurrentHashMap<>();

    /** 当前活动 session 的运行时状态（UI 显示的就是它的 messages） */
    protected SessionRuntimeState currentState = null;

    /**
     * 历史消息加载状态：prepareHistoricalMessages 期间为 true，加载完成自动置 false。
     * UI 绑定此属性：加载期间禁用发送按钮并显示加载提示。
     */
    private final BooleanProperty historyLoading = new SimpleBooleanProperty(false);

    public BooleanProperty historyLoadingProperty() {
        return historyLoading;
    }

    /**
     * Plan Mode（计划模式）：开启后构建的智能体仅保留只读探索工具，
     * 系统提示词注入计划模式指令，完成后经 ExitPlanMode 提交计划等待批准。
     * 切换后需 evictAgent 使下一次消息按新模式重建智能体。
     */
    private final BooleanProperty planMode = new SimpleBooleanProperty(false);

    public BooleanProperty planModeProperty() {
        return planMode;
    }

    public boolean isPlanMode() {
        return planMode.get();
    }

    /**
     * Goal Loop 活跃状态：目标设置后为 true，达成 / 无法达成 / 暂停 / 清除后为 false。
     * goal 按钮据此显示开关态。
     */
    private final BooleanProperty goalActive = new SimpleBooleanProperty(false);

    public BooleanProperty goalActiveProperty() {
        return goalActive;
    }

    /**
     * 工具卡片路由回调：TOOL_CALLS 事件创建的 ToolMessageCard 直接通过此回调路由到 EditorPanel，
     * 不再进入 messages 列表（消除"加入消息列表再过滤"反模式）。
     * 仅 active session 的事件会推送；切换 session 时由 Controller 清空 EditorPanel。
     */
    @Setter
    private Consumer<ToolMessageCard> toolCardHandler = _ -> {};

    /** session 切换回调：通知 Controller 清空 EditorPanel 工具卡片，重置 todo 等 */
    @Setter
    private Consumer<String> sessionActivatedHandler = _ -> {};

    /**
     * per-session 运行时状态。所有可变状态都放在这里，ViewModel 仅持有当前 active 引用。
     */
    protected static final class SessionRuntimeState {
        /** 当前 agent 流订阅（用于 pause 取消） */
        Disposable subscription;
        /** 取消信号（用于 pauseGeneration 中断 blockLast） */
        Sinks.Empty<Object> cancelSink;
        /** 当前流式 assistant 消息卡片 */
        AssistantMessageCard currentAssistantCard = null;
        /** 待响应的工具调用卡片，按 toolCallId 索引 */
        final Map<String, ToolMessageCard> pendingToolCards = new ConcurrentHashMap<>();
        /** 是否正在流式生成（per-session） */
        volatile boolean isStreaming = false;
        /** 是否暂停（per-session） */
        volatile boolean isPaused = false;
        /** 切换走时保存的 messages 副本（同一 MessageCard 引用），切回时整体恢复 */
        final List<MessageCard> savedMessages = new ArrayList<>();
    }

    protected AbstractHomePageViewModel(FileSystemSessionManager sessionManager,
                                        AgentDefinitionManager definitionManager,
                                        ModelFactory modelFactory,
                                        Toolkit toolkit,
                                        SkillManager skillManager,
                                        List<cn.bitloom.agentic.permission.strategy.ToolApprovalStrategy> approvalStrategies,
                                        cn.bitloom.config.ConfigManager configManager,
                                        cn.bitloom.agentic.tool.mcp.McpConnectionManager mcpConnectionManager,
                                        cn.bitloom.agentic.goal.GoalManager goalManager,
                                        cn.bitloom.bridge.desktop.ToolUIBridge toolUIBridge) {
        this.sessionManager = sessionManager;
        this.definitionManager = definitionManager;
        this.modelFactory = modelFactory;
        this.toolkit = toolkit;
        this.skillManager = skillManager;
        this.approvalStrategies = approvalStrategies;
        this.configManager = configManager;
        this.goalManager = goalManager;
        this.toolUIBridge = toolUIBridge;
        // MCP 连接变化（McpConnect/断开）→ evict 全部 per-session Agent 缓存，
        // 下一次 sendMessage 经 computeIfAbsent 重建，工具池即含最新 MCP 工具。
        // 正在流式处理中的 Agent 不受影响（引用仍被持有），新工具自下一轮对话生效。
        mcpConnectionManager.addChangeListener(sessionAgents::clear);
        // Goal Loop 自动续轮：GoalJudgeHook / 后台任务通知通过 GoalManager 触发，
        // 本 VM 仅处理自己管理过的 session（sessionStates 路由），非本 VM 的静默忽略。
        goalManager.registerContinuation(this::continueRound);
    }

    public void createNewSession() {
        // 保存当前 session 的 messages 到 state（不取消订阅，让后台任务继续运行）
        saveMessagesToCurrentState();
        // 切换到 null state（新建会话）
        currentState = null;
        this.session = null;
        Store.currentSessionId.set("");
        messages.clear();
        // 新会话初始为非流式状态
        Store.isStreaming.set(false);
        Store.isPaused.set(false);
        // 通知 Controller 清空 EditorPanel 工具卡片 / todo
        sessionActivatedHandler.accept(null);
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

        // 保存当前 session 的 messages 到 state（不取消订阅）
        saveMessagesToCurrentState();

        this.session = targetSession;
        Store.currentSessionId.set(sessionId);

        boolean stateExisted = sessionStates.containsKey(sessionId);
        currentState = sessionStates.computeIfAbsent(sessionId, k -> new SessionRuntimeState());

        if (stateExisted) {
            // 已有 state：恢复 savedMessages 到 UI，同步流式状态
            messages.setAll(currentState.savedMessages);
            Store.isStreaming.set(currentState.isStreaming);
            Store.isPaused.set(currentState.isPaused);
        } else {
            // 首次切换：清空 UI，同步非流式状态，后续 prepareHistoricalMessages 加载历史
            messages.clear();
            Store.isStreaming.set(false);
            Store.isPaused.set(false);
        }

        // 子类恢复模式专有状态（如 coder 从 metadata 恢复 currentProject）
        onSessionSwitched(targetSession);

        // 通知 Controller 清空 EditorPanel 工具卡片 / todo（非 active session 的工具不显示）
        sessionActivatedHandler.accept(sessionId);

        if (!stateExisted && hasHistoricalMessages()) {
            prepareHistoricalMessages();
        }
    }

    /**
     * 保存当前 messages 内容到 currentState.savedMessages（切换走时调用）。
     * MessageCard 引用保持不变，切回时可直接 setAll 恢复。
     */
    private void saveMessagesToCurrentState() {
        if (currentState != null) {
            currentState.savedMessages.clear();
            currentState.savedMessages.addAll(messages);
        }
    }

    public void switchAgent(String agentId) {
        // 在旧 viewModel 上清理模式专有状态（如 coder 清空 currentProject）
        onSwitchAgent(agentId);
        // 触发 HomePageRouter.switchMode，由它在切换后对新 viewModel 调用 createNewSession
        Store.currentAgent.set(agentId);
    }

    public void prepareHistoricalMessages() {
        // 从 events.jsonl 同步加载历史事件，直接渲染为完成态卡片。
        // 只处理 USER / ASSISTANT 消息，跳过 TOOL 消息（历史工具不显示）。
        // 加载期间 historyLoading=true，UI 据此禁用发送并显示加载提示。
        List<AbstractEvent> events = sessionManager.getEvents(this.session.id());
        if (events.isEmpty()) {
            return;
        }

        historyLoading.set(true);
        try {
            for (AbstractEvent event : events) {
                if (event instanceof CompactionEvent ce) {
                    CompactionCard card = new CompactionCard(ce.getArchivedCount(), ce.getActiveCount());
                    messages.add(card);
                    if (currentState != null) currentState.savedMessages.add(card);
                    continue;
                }
                if (!(event instanceof MessageEvent me)) {
                    continue;
                }
                // 跳过合成事件（压缩产生的 shadow-prompt 用户消息 + 摘要助手消息）：
                // 它们是框架生成的伪消息，不是用户真实对话。归档的旧历史中可能也残留
                // 多次压缩产生的旧合成消息，因此这里不区分 archived —— 只要 synthetic 一律跳过。
                // 压缩的提示由 CompactionEvent → CompactionCard 负责渲染。
                if (me.isSynthetic()) {
                    continue;
                }
                if (me.isUserMessage()) {
                    UserMessageCard card = new UserMessageCard(me.getText());
                    messages.add(card);
                    if (currentState != null) currentState.savedMessages.add(card);
                } else if (me.isAssistantMessage()) {
                    String text = me.getText();
                    if (text != null && !text.isBlank()) {
                        AssistantMessageCard card = new AssistantMessageCard(text, "STOP");
                        messages.add(card);
                        if (currentState != null) currentState.savedMessages.add(card);
                    }
                }
                // TOOL 消息跳过：历史工具调用不显示
            }
        } finally {
            historyLoading.set(false);
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
        if (mode == AgentMode.CODE) {
            ProjectInfo project = getCurrentProject();
            if (project == null) {
                throw new IllegalStateException("code 模式必须先选择项目");
            }
            return "code-" + project.name() + "-" + SessionTypeEnum.DM + "-" + "desktopApp" + "-" + Store.userId.get() + "-" + System.currentTimeMillis();
        }
        return "work-" + SessionTypeEnum.DM + "-" + "desktopApp" + "-" + Store.userId.get() + "-" + System.currentTimeMillis();
    }

    /**
     * 构建 systemPrompt（code 模式注入全局规则和项目规则；计划模式追加计划指令）。
     */
    protected String buildSystemPrompt(String agentId, AgentDefinition definition) {
        String systemPrompt = definition.content();
        if (AgentMode.fromAgentId(agentId) != AgentMode.CODE) {
            return systemPrompt + ShellSession.envBlock();
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
        // 计划模式：注入只读约束与计划提交要求
        if (planMode.get()) {
            systemPrompt += "\n\n# 计划模式\n"
                    + "你正处于计划模式：只允许只读探索（读文件、搜索、查网页），"
                    + "严禁创建、修改、删除文件或执行任何有副作用的命令。\n"
                    + "任务：针对用户需求充分调研代码库后，制定具体到文件级的实施计划，"
                    + "然后调用 ExitPlanMode 工具提交计划等待用户批准。\n"
                    + "计划必须包含：将创建/修改的文件与各自改动要点、实施步骤顺序、风险与回滚方式。\n"
                    + "用户给出反馈时，按反馈调整计划并重新提交；不要在计划模式下开始实施。";
        }
        // code 模式注入项目路径作为 Working directory，让 LLM 感知项目根目录
        return systemPrompt + ShellSession.envBlock(project != null ? project.path() : null);
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

        // 四步压缩管线（低成本优先：滑动窗口裁剪 → 旧工具结果占位符化 → 水位检查 → LLM 摘要）
        StagedCompactionStrategy stagedStrategy = StagedCompactionStrategy.builder(
                        RecursiveSummarizationCompactionStrategy.builder(
                                ChatClient.builder(chatModel).build())
                        .build())
                .tokenThreshold(100000)
                .build();
        SessionMemoryAdvisor sessionMemoryAdvisor = SessionMemoryAdvisor.builder(sessionManager)
                .defaultUserId(uid)
                .messageFilter(MessageFilter.byMessageType(MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL)
                        .and(MessageFilter.skipEmptyMessages()))
                .compactionTrigger(TokenCountTrigger.builder()
                        .threshold(100000)
                        .tokenCountEstimator(new JTokkitTokenCountEstimator())
                        .build())
                .compactionStrategy(stagedStrategy)
                .build();
        advisors.add(sessionMemoryAdvisor);

        Path memoriesDir = resolveMemoryDir(agentId, getCurrentProject());
        // 记忆自动化三件套共享同一 store：
        // (a) 选择式召回——仅首轮注入相关记忆背景；(b) 回合提取 Hook——见下方 hooks；
        // (c) 整理触发器——文件数 ≥ 阈值时注入 reminder 并由 Hook 异步整理
        FileSystemAgentMemoryStore memoryStore = new FileSystemAgentMemoryStore(memoriesDir);
        AutoMemoryToolsAdvisor autoMemoryToolsAdvisor = AutoMemoryToolsAdvisor.builder()
                .memoryStore(memoryStore)
                .memoriesRootDirectory(memoriesDir.toString())
                .memoryConsolidationTrigger(
                        MemoryConsolidator.triggerWhen(memoryStore, MemoryConsolidator.DEFAULT_THRESHOLD))
                .build();
        advisors.add(autoMemoryToolsAdvisor);

        advisors.add(MemoryRecallAdvisor.builder()
                .sessionManager(sessionManager)
                .memoryStore(memoryStore)
                .chatClient(ChatClient.builder(chatModel).build())
                .build());

        advisors.add(SkillContextAdvisor.builder().skillManager(skillManager).build());

        advisors.add(SubagentContextAdvisor.builder()
                .definitionManager(definitionManager)
                .definition(definition)
                .build());

        List<ToolCallback> allTools = new ArrayList<>(toolkit.buildToolCallbacks(definition));
        allTools.add(ConversationSearchTool.builder(sessionManager).build().toToolCallback());
        allTools.add(CrossSessionSearchTool.builder(sessionManager, uid).build().toToolCallback());

        // 计划模式：仅保留只读探索工具，追加 ExitPlanMode（计划提交出口）
        if (planMode.get()) {
            allTools = new ArrayList<>(allTools.stream()
                    .filter(tc -> PLAN_MODE_ALLOWED.contains(tc.getToolDefinition().name()))
                    .toList());
            allTools.add(ExitPlanModeTool.builder()
                    .listener(this::onPlanSubmitted)
                    .build()
                    .toToolCallback());
        }

        // 基础 Hook 集：预算保护 / 权限审批 / Todo 提醒 / 工具结果落盘（每次 new，避免状态串扰）
        List<IAgentHook> hooks = new ArrayList<>();
        hooks.add(new ToolCallBudgetHook(configManager.getMaxToolCalls()));
        hooks.add(new PermissionHook(approvalStrategies));
        hooks.add(new TodoReminderHook());
        hooks.add(new ToolResultOffloadHook());
        // 记忆自动化 (b)：回合结束异步提取长期记忆（仅主智能体，用户交互入口）
        hooks.add(MemoryExtractionHook.builder()
                .sessionManager(sessionManager)
                .memoryStore(memoryStore)
                .chatClient(ChatClient.builder(chatModel).build())
                .build());
        // Goal Loop（s17 目标闭环）：独立判断器复核目标达成，未达成自动续轮
        hooks.add(cn.bitloom.agentic.goal.GoalJudgeHook.builder()
                .goalManager(goalManager)
                .sessionManager(sessionManager)
                .chatClient(ChatClient.builder(chatModel).build())
                .listener(this::onGoalUpdated)
                .build());

        Agent agent = Agent.builder()
                .name(agentId)
                .definition(definition)
                .model(chatModel)
                .systemPrompt(buildSystemPrompt(agentId, definition))
                .tools(allTools)
                .hooks(hooks)
                .advisors(advisors)
                // reactive_compact：上下文超长被 API 拒绝时强制压缩（绕过触发器）后重试一次
                .reactiveCompactor(sid -> sessionManager.compact(sid, req -> true, stagedStrategy))
                .build();
        log.info("构建智能体: agentId={}", agentId);
        return agent;
    }

    // ===== 发送消息 =====

    /**
     * 确保当前 session 存在（首次交互时创建）。sendMessage 与 slash 命令共用。
     */
    protected Session ensureSession() {
        if (this.session == null) {
            String agentId = Store.currentAgent.get();
            String sessionId = buildSessionId(agentId);
            CreateSessionRequest.Builder builder = CreateSessionRequest.builder()
                    .id(sessionId)
                    .userId(Store.userId.get());
            ProjectInfo project = getCurrentProject();
            if (project != null) {
                builder.metadata("projectId", project.id());
                builder.metadata("projectName", project.name());
            }
            this.session = sessionManager.create(builder.build());
            Store.currentSessionId.set(this.session.id());
        }
        return this.session;
    }

    /** evict 指定 session 的 Agent 缓存（下一次 sendMessage 按当前状态重建，如计划模式切换） */
    protected void evictAgent(String sessionId) {
        sessionAgents.remove(sessionId);
    }

    /**
     * 智能体提交计划（ExitPlanModeTool 回调，工具线程）。
     * 默认直接放弃；子类 override 实现批准 UI 与批准后自动执行。
     */
    protected void onPlanSubmitted(String sessionId, String plan, CompletableFuture<String> future) {
        future.complete(ExitPlanModeTool.DECISION_ABANDONED);
    }

    /** agent 流结束回调（doOnComplete，FX 线程）。子类可用于批准后的自动执行轮等 */
    protected void onStreamCompleted(String sessionId) {
    }

    public void sendMessage(String text) {
        ensureSession();

        // 确保 state 存在（首次发消息或切回后首次发消息都会创建）
        final String sid = this.session.id();
        boolean stateExisted = sessionStates.containsKey(sid);
        currentState = sessionStates.computeIfAbsent(sid, k -> new SessionRuntimeState());
        if (!stateExisted) {
            // state 是新建的，把当前 UI messages 同步进去（防止切回后丢失已有历史消息）
            currentState.savedMessages.addAll(messages);
        }

        // 兜底：清理上一轮 pause 后异步 after() 竞态写入的孤儿 toolCalls
        // 此时距上次 pause 已隔用户操作时间，in-flight after 必已完成，能可靠检测
        sessionManager.finalizeInterruptedToolCalls(sid);

        // per-session 流式状态：写 state，同步到 Store（active session 时驱动 UI）
        currentState.isStreaming = true;
        currentState.isPaused = false;
        Store.isStreaming.set(true);
        Store.isPaused.set(false);
        currentState.cancelSink = Sinks.empty();

        // 子类实现消息上下文构建（coder 模式附加项目信息）
        String messageText = buildMessageWithContext(text);
        MessageEvent inputEvent = MessageEvent.userMessage(sid, messageText);
        final Session currentSession = this.session;
        final String agentId = Store.currentAgent.get();
        final SessionRuntimeState stateRef = currentState;

        // 在后台线程执行，避免阻塞 FX 线程；per-session 锁保证串行
        CompletableFuture.runAsync(() -> {
            sessionManager.withLock(sid, () -> {
                try {
                    // Agent 与 session 1:1 绑定，session 级缓存（首次构建，后续复用）
                    Agent agent = sessionAgents.computeIfAbsent(sid,
                            k -> buildAgent(currentSession, agentId));
                    RuntimeContext ctx = RuntimeContext.builder()
                            .sessionId(sid)
                            .userId(currentSession.userId())
                            .projectPath(resolveProjectPath(currentSession))
                            .put("lastUserMessage", messageText)
                            .build();
                    subscribeAgentStream(agent, inputEvent, ctx, sid, stateRef);
                } catch (Exception e) {
                    log.error("sendMessage error", e);
                    Platform.runLater(() -> {
                        stateRef.isStreaming = false;
                        stateRef.isPaused = false;
                        if (currentState == stateRef) {
                            Store.isStreaming.set(false);
                            Store.isPaused.set(false);
                        }
                    });
                }
                return null;
            });
        });

        // 触发侧边栏刷新（更新会话标题）
        Store.refreshHistory.set(!Store.refreshHistory.get());
    }

    /**
     * 订阅 Agent 事件流（sendMessage 与 Goal Loop 自动续轮共用）。
     */
    private void subscribeAgentStream(Agent agent, MessageEvent inputEvent, RuntimeContext ctx,
            String sid, SessionRuntimeState stateRef) {
        stateRef.subscription = agent.runStream(inputEvent, ctx)
                .takeUntilOther(stateRef.cancelSink.asMono())
                .doOnNext(event -> Platform.runLater(() -> processEvent(event, sid)))
                .doOnComplete(() -> Platform.runLater(() -> {
                    stateRef.isStreaming = false;
                    stateRef.isPaused = false;
                    // 仅当仍是 active session 时同步 Store，避免覆盖其他 session 的 UI 状态
                    if (currentState == stateRef) {
                        Store.isStreaming.set(false);
                        Store.isPaused.set(false);
                    }
                    onStreamCompleted(sid);
                }))
                .doOnError(e -> {
                    log.error("agent run error", e);
                    Platform.runLater(() -> {
                        stateRef.isStreaming = false;
                        stateRef.isPaused = false;
                        if (currentState == stateRef) {
                            Store.isStreaming.set(false);
                            Store.isPaused.set(false);
                        }
                    });
                })
                .subscribe();
    }

    /**
     * 自动续轮：以 synthetic 消息对 sessionId 发起下一次 runStream，无需用户输入。
     * Goal Loop（goal_feedback）与计划批准后的执行轮共用。
     */
    protected void continueRound(String sessionId, String message) {
        SessionRuntimeState stateRef = sessionStates.get(sessionId);
        if (stateRef == null) {
            return; // 非本 VM 管理的 session（coder/work 路由）
        }
        Session targetSession = sessionManager.getById(sessionId);
        if (targetSession == null) {
            return;
        }
        // 等待上一轮流式结束（judge 在 afterConversationRound 异步触发，与 doOnComplete 存在小概率竞态）
        long deadline = System.currentTimeMillis() + 10_000;
        while (stateRef.isStreaming && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (stateRef.isStreaming) {
            log.warn("目标续轮取消：上一轮仍在流式: session={}", sessionId);
            return;
        }

        stateRef.isStreaming = true;
        stateRef.isPaused = false;
        stateRef.cancelSink = Sinks.empty();
        Platform.runLater(() -> {
            if (currentState == stateRef) {
                Store.isStreaming.set(true);
                Store.isPaused.set(false);
            }
        });

        MessageEvent inputEvent = MessageEvent.userMessage(sessionId, message);
        inputEvent.setMetadata(Map.of(MessageEvent.METADATA_SYNTHETIC, Boolean.TRUE));

        CompletableFuture.runAsync(() -> {
            sessionManager.withLock(sessionId, () -> {
                try {
                    Agent agent = sessionAgents.get(sessionId);
                    if (agent == null) {
                        log.warn("目标续轮取消：Agent 缓存不存在: session={}", sessionId);
                        stateRef.isStreaming = false;
                        return null;
                    }
                    RuntimeContext ctx = RuntimeContext.builder()
                            .sessionId(sessionId)
                            .userId(targetSession.userId())
                            .projectPath(resolveProjectPath(targetSession))
                            .put("lastUserMessage", message)
                            .build();
                    subscribeAgentStream(agent, inputEvent, ctx, sessionId, stateRef);
                } catch (Exception e) {
                    log.error("continueGoalRound error", e);
                    stateRef.isStreaming = false;
                    Platform.runLater(() -> {
                        if (currentState == stateRef) {
                            Store.isStreaming.set(false);
                        }
                    });
                }
                return null;
            });
        });
    }

    /**
     * 解析 session 的 projectPath（Goal 续轮时 session 可能非 active，
     * 按 session.metadata 的 projectId 解析而非当前 UI 状态）。
     */
    protected String resolveProjectPath(Session session) {
        ProjectInfo project = getCurrentProject();
        return project != null ? project.path() : null;
    }

    /**
     * Goal 状态更新回调（GoalJudgeHook listener）：更新 GoalCard + 终态通知。
     * /goal 命令设置目标后复用此方法刷新卡片。
     */
    protected void onGoalUpdated(String sessionId, cn.bitloom.agentic.goal.GoalState state) {
        // 同步 goal 按钮开关态：active 进行中，其余终态关闭
        boolean active = cn.bitloom.agentic.goal.GoalState.STATUS_ACTIVE.equals(state.getStatus());
        goalActive.set(active);
        String goalJson = cn.bitloom.util.JsonUtils.toJson(Map.of(
                "goal", state.getGoal(),
                "status", state.getStatus(),
                "judgeCount", state.getJudgeCount(),
                "blockedCount", state.getBlockedCount(),
                "lastReason", state.getLastReason() != null ? state.getLastReason() : ""));
        if (toolUIBridge != null) {
            toolUIBridge.showGoal(goalJson);
            if (cn.bitloom.agentic.goal.GoalState.STATUS_ACHIEVED.equals(state.getStatus())) {
                toolUIBridge.showNotification("目标已达成（判定 " + state.getJudgeCount() + " 次）", sessionId);
            } else if (cn.bitloom.agentic.goal.GoalState.STATUS_IMPOSSIBLE.equals(state.getStatus())) {
                toolUIBridge.showNotification("目标无法达成：" + state.getLastReason(), sessionId);
            } else if (cn.bitloom.agentic.goal.GoalState.STATUS_BLOCKED.equals(state.getStatus())) {
                toolUIBridge.showNotification("目标续轮已暂停（连续 " + state.getBlockedCount()
                        + " 次未通过判定）：" + state.getLastReason(), sessionId);
            }
        }
    }

    // ===== 事件处理 =====

    /**
     * 处理事件流中的事件（可能是 MessageEvent / CompactionEvent / DiffEvent）。
     * 按 sessionId 路由到对应 state：active session 同时更新 UI messages 与 savedMessages，
     * 非 active session 只更新 savedMessages（不污染 UI）。
     */
    private void processEvent(AbstractEvent event, String sessionId) {
        SessionRuntimeState state = sessionStates.get(sessionId);
        if (state == null) {
            log.warn("事件对应的 state 已被移除: sid={}, event={}", sessionId, event.getEventType());
            return;
        }
        boolean isActive = (state == currentState);

        if (event instanceof MessageEvent messageEvent) {
            processMessageEvent(messageEvent, state, isActive);
        } else if (event instanceof CompactionEvent ce) {
            CompactionCard card = new CompactionCard(ce.getArchivedCount(), ce.getActiveCount());
            if (isActive) messages.add(card);
            state.savedMessages.add(card);
        } else if (event instanceof DiffEvent diffEvent) {
            // Diff 事件仅在 active session 推送（非 active 丢弃，避免污染 EditorPanel）
            if (isActive && diffHandler != null) {
                diffHandler.accept(diffEvent);
            }
        }
    }

    private void processMessageEvent(MessageEvent event, SessionRuntimeState state, boolean isActive) {
        if (event.isUserMessage()) {
            processUserEvent(event, state, isActive);
        } else if (event.isAssistantMessage()) {
            processAssistantEvent(event, state, isActive);
        } else if (event.isToolResponse()) {
            processToolEvent(event, state);
        } else {
            log.warn("未处理的事件类型: {}", event.getEventType());
        }
    }

    private void processUserEvent(MessageEvent e, SessionRuntimeState state, boolean isActive) {
        state.currentAssistantCard = null;
        // synthetic 消息（Goal 续轮 goal_feedback / 后台任务通知等系统注入）以通知样式渲染
        MessageCard card = e.isSynthetic()
                ? new cn.bitloom.node.message.NotificationCard(e.getText())
                : new UserMessageCard(e.getText());
        if (isActive) messages.add(card);
        state.savedMessages.add(card);
    }

    private void processAssistantEvent(MessageEvent e, SessionRuntimeState state, boolean isActive) {
        String finishReason = e.getFinishReason();
        String text = e.getText();

        if (finishReason == null || finishReason.isBlank() || "_UNKNOWN".equals(finishReason)) {
            // 流式 chunk：直接累积。per-session isPaused 控制是否累积
            if (state.isPaused) {
                return;
            }
            if (state.currentAssistantCard == null) {
                state.currentAssistantCard = new AssistantMessageCard();
                if (isActive) messages.add(state.currentAssistantCard);
                state.savedMessages.add(state.currentAssistantCard);
            }
            state.currentAssistantCard.appendContent(text);
        } else if ("STOP".equals(finishReason)) {
            // 结束流式
            state.isStreaming = false;
            state.isPaused = false;
            if (isActive) {
                Store.isStreaming.set(false);
                Store.isPaused.set(false);
            }

            if (state.currentAssistantCard != null) {
                state.currentAssistantCard.complete("STOP");
                if (state.currentAssistantCard.isValid()) {
                    if (isActive) messages.remove(state.currentAssistantCard);
                    state.savedMessages.remove(state.currentAssistantCard);
                }
                state.currentAssistantCard = null;
            } else if (text != null && !text.isBlank()) {
                // 非流式消息（历史消息或一次性输出）
                AssistantMessageCard card = new AssistantMessageCard(text, "STOP");
                if (isActive) messages.add(card);
                state.savedMessages.add(card);
            }
        } else if ("TOOL_CALLS".equals(finishReason)) {
            // 工具调用：结束当前流式消息
            if (state.currentAssistantCard != null) {
                state.currentAssistantCard.complete("TOOL_CALLS");
                if (state.currentAssistantCard.isValid()) {
                    if (isActive) messages.remove(state.currentAssistantCard);
                    state.savedMessages.remove(state.currentAssistantCard);
                }
                state.currentAssistantCard = null;
            }

            // 创建工具调用卡片，缓存到 state.pendingToolCards；
            // 仅 active session 推送到 EditorPanel（非 active session 的工具不显示）
            if (e.getToolCalls() != null) {
                for (MessageEvent.ToolCallInfo tc : e.getToolCalls()) {
                    ToolMessageCard card = new ToolMessageCard(tc.id(), tc.name(), tc.arguments());
                    state.pendingToolCards.put(tc.id(), card);
                    if (isActive) {
                        toolCardHandler.accept(card);
                    }
                }
            }
        }
    }

    private void processToolEvent(MessageEvent e, SessionRuntimeState state) {
        if (e.getResponses() != null && !e.getResponses().isEmpty()) {
            for (MessageEvent.ToolResponseInfo resp : e.getResponses()) {
                ToolMessageCard card = state.pendingToolCards.remove(resp.id());
                if (card != null) {
                    card.setResponse(resp.responseData());
                }
            }
        }
    }

    public void addUserMessage(String text) {
        UserMessageCard card = new UserMessageCard(text);
        messages.add(card);
        if (currentState != null) {
            currentState.savedMessages.add(card);
        }
    }

    /**
     * 向 UI messages 添加节点消息卡片（如 TaskCard），同步到 currentState.savedMessages。
     * 由 Controller 通过 toolUIBridge 回调调用，确保 active session 的节点消息在切换走时不丢失。
     */
    public void addNodeMessage(javafx.scene.Node node) {
        NodeMessageCard card = new NodeMessageCard(node);
        messages.add(card);
        if (currentState != null) {
            currentState.savedMessages.add(card);
        }
    }

    /**
     * 撤回用户消息：删除该条消息及其之后的所有消息（UI 与持久化事件历史）。
     * 仅作用于当前 active session。后台 session 的撤回不支持（UI 不可见）。
     *
     * @param card 触发撤回的用户消息卡片
     */
    public void withdrawMessage(UserMessageCard card) {
        if (card == null) return;
        int index = messages.indexOf(card);
        if (index < 0) return;

        // 停止当前 session 的流（仅 active session，不影响后台 session）
        if (currentState != null) {
            currentState.isStreaming = false;
            currentState.isPaused = false;
            cancelStateSubscription(currentState);
            currentState.currentAssistantCard = null;
        }
        Store.isStreaming.set(false);
        Store.isPaused.set(false);

        // 删除 UI 中的该条 + 之后所有消息
        messages.remove(index, messages.size());

        // 同步删除 currentState.savedMessages 中对应位置之后的所有消息
        if (currentState != null) {
            int savedIndex = currentState.savedMessages.indexOf(card);
            if (savedIndex >= 0) {
                currentState.savedMessages.subList(savedIndex, currentState.savedMessages.size()).clear();
            }
        }

        // 截断持久化事件历史，确保重启/重新加载后上下文一致
        if (this.session != null) {
            sessionManager.truncateEventsFrom(this.session.id(), card.getContent());
        }
    }

    public void clear() {
        // 清空当前 session 的 UI 与 state
        messages.clear();
        if (currentState != null) {
            currentState.savedMessages.clear();
            currentState.pendingToolCards.clear();
            currentState.currentAssistantCard = null;
        }
        Store.isStreaming.set(false);
        Store.isPaused.set(false);
        if (this.session != null) {
            // 取消该 session 的订阅并移除缓存（session 整体销毁）
            SessionRuntimeState state = sessionStates.remove(this.session.id());
            if (state != null) cancelStateSubscription(state);
            sessionAgents.remove(this.session.id());
            currentState = null;
            sessionManager.remove(this.session.id());
            sessionManager.persistSession(this.session);
        }
    }

    public void pauseGeneration() {
        // 仅暂停当前 active session（用户点 stop 按钮）
        if (currentState == null) return;
        if (!currentState.isStreaming || currentState.isPaused) return;

        currentState.isStreaming = false;
        currentState.isPaused = true;
        Store.isStreaming.set(false);
        Store.isPaused.set(true);
        cancelStateSubscription(currentState);

        // 中途停止时善后事件文件，避免下次调用 LLM 时历史不成对导致报错：
        //   1. 保存已流式生成的 assistant 文本为 STOP 事件，避免上一轮内容丢失
        //   2. 为末尾不成对的 assistant(toolCalls) 补虚拟 ToolResponse（若存在）
        //      — pause 时调用是尽力而为；竞态残留的孤儿由 sendMessage 开头兜底再补
        if (this.session != null) {
            String sid = this.session.id();

            if (currentState.currentAssistantCard != null) {
                String partial = currentState.currentAssistantCard.getContent();
                if (partial != null && !partial.isBlank()) {
                    sessionManager.appendEvent(MessageEvent.assistantStop(sid, partial));
                }
                currentState.currentAssistantCard.setStreaming(false);
                currentState.currentAssistantCard = null;
            }

            sessionManager.finalizeInterruptedToolCalls(sid);
        } else if (currentState.currentAssistantCard != null) {
            currentState.currentAssistantCard.setStreaming(false);
            currentState.currentAssistantCard = null;
        }
    }

    /**
     * 取消指定 state 的订阅与取消信号（不删除 state 本身，便于切回恢复）。
     */
    private void cancelStateSubscription(SessionRuntimeState state) {
        if (state.cancelSink != null) {
            state.cancelSink.tryEmitEmpty();
            state.cancelSink = null;
        }
        if (state.subscription != null && !state.subscription.isDisposed()) {
            state.subscription.dispose();
        }
        state.subscription = null;
    }

    /**
     * 释放资源（模式切换时调用）：取消所有 session 的订阅、清空所有缓存。
     * 子类可 override 扩展清理逻辑，但必须 super.dispose()。
     */
    public void dispose() {
        for (SessionRuntimeState state : sessionStates.values()) {
            cancelStateSubscription(state);
        }
        sessionStates.clear();
        sessionAgents.clear();
        currentState = null;
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
