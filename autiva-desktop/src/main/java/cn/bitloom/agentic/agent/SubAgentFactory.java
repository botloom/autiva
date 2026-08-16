package cn.bitloom.agentic.agent;

import cn.bitloom.agentic.agent.advisor.AutoMemoryToolsAdvisor;
import cn.bitloom.agentic.agent.advisor.MemoryRecallAdvisor;
import cn.bitloom.agentic.agent.advisor.SessionMemoryAdvisor;
import cn.bitloom.agentic.agent.advisor.SkillContextAdvisor;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.memory.FileSystemAgentMemoryStore;
import cn.bitloom.agentic.memory.MemoryConsolidator;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.session.EventFilter;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.agentic.session.MessageFilter;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.session.compaction.RecursiveSummarizationCompactionStrategy;
import cn.bitloom.agentic.session.compaction.StagedCompactionStrategy;
import cn.bitloom.agentic.session.compaction.TokenCountTrigger;
import cn.bitloom.agentic.tool.Toolkit;
import cn.bitloom.agentic.tool.command.ShellSession;
import cn.bitloom.agentic.tool.session.ConversationSearchTool;
import cn.bitloom.agentic.tool.session.CrossSessionSearchTool;
import cn.bitloom.agentic.hook.IAgentHook;
import cn.bitloom.agentic.hook.PermissionHook;
import cn.bitloom.agentic.hook.TodoReminderHook;
import cn.bitloom.agentic.hook.ToolCallBudgetHook;
import cn.bitloom.agentic.hook.ToolResultOffloadHook;
import cn.bitloom.agentic.permission.strategy.ToolApprovalStrategy;
import cn.bitloom.config.ConfigManager;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.exception.AgentException;
import cn.bitloom.store.Store;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 子智能体构建工厂 — TaskTool（一次性委派）/ TeammateRuntime（持久队友）/
 * WorkflowContext（编排原语）共用的 Agent 构建逻辑。
 *
 * <p>统一模式：复用父 Session + branch 事件隔离（EventFilter.forBranch）+
 * 四步压缩管线 + reactive_compact + 记忆自动化 Advisor。
 */
@Component
public class SubAgentFactory {

    private final FileSystemSessionManager sessionManager;
    private final AgentDefinitionManager definitionManager;
    private final ModelFactory modelFactory;
    private final ObjectProvider<Toolkit> toolkitProvider;
    private final SkillManager skillManager;
    private final List<ToolApprovalStrategy> approvalStrategies;
    private final ConfigManager configManager;

    public SubAgentFactory(FileSystemSessionManager sessionManager,
            AgentDefinitionManager definitionManager,
            ModelFactory modelFactory,
            ObjectProvider<Toolkit> toolkitProvider,
            SkillManager skillManager,
            List<ToolApprovalStrategy> approvalStrategies,
            ConfigManager configManager) {
        this.sessionManager = sessionManager;
        this.definitionManager = definitionManager;
        this.modelFactory = modelFactory;
        this.toolkitProvider = toolkitProvider;
        this.skillManager = skillManager;
        this.approvalStrategies = approvalStrategies;
        this.configManager = configManager;
    }

    /**
     * 构建子智能体 Agent。
     *
     * @param parentSession       父会话（事件写入目标）
     * @param agentId             AgentDefinition 名
     * @param branch              事件隔离 branch（如 subagent.xxx / teammate.xxx / workflow.xxx）
     * @param projectPath         工具上下文 projectPath（可 null）
     * @param systemPromptOverride 自定义系统提示（null = definition.content() + envBlock）
     * @param extraTools          追加工具（团队协作工具等；空 = 仅白名单 + 会话搜索）
     */
    public Agent build(Session parentSession, String agentId, String branch, String projectPath,
            String systemPromptOverride, List<ToolCallback> extraTools) {
        AgentDefinition definition = definitionManager.getDefinition(agentId);
        if (definition == null) {
            throw AgentException.subagentNotFound("子智能体定义不存在: " + agentId
                    + "，可用定义: " + definitionManager.getSubagentDefinitions().stream()
                            .map(AgentDefinition::name).toList());
        }
        ModelTypeEnum modelType = Store.selectedModel.get() != null ? Store.selectedModel.get()
                : ModelTypeEnum.DEEPSEEK;
        ChatModel chatModel = modelFactory.model(modelType);
        String uid = parentSession.userId() != null ? parentSession.userId() : "default-user";

        List<Advisor> advisors = new ArrayList<>();

        // 四步压缩管线（低成本优先：滑动窗口裁剪 → 旧工具结果占位符化 → 水位检查 → LLM 摘要）
        StagedCompactionStrategy stagedStrategy = StagedCompactionStrategy.builder(
                RecursiveSummarizationCompactionStrategy.builder(ChatClient.builder(chatModel).build()).build())
                .tokenThreshold(100000)
                .build();

        // EventFilter.forBranch(branch): 子智能体仅能看到自己 branch 的事件 + root 事件
        SessionMemoryAdvisor sessionMemoryAdvisor = SessionMemoryAdvisor.builder(sessionManager)
                .defaultUserId(uid)
                .eventFilter(EventFilter.forBranch(branch))
                .messageFilter(MessageFilter.byMessageType(MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL)
                        .and(MessageFilter.skipEmptyMessages()))
                .compactionTrigger(TokenCountTrigger.builder()
                        .threshold(100000)
                        .tokenCountEstimator(new JTokkitTokenCountEstimator())
                        .build())
                .compactionStrategy(stagedStrategy)
                .build();
        advisors.add(sessionMemoryAdvisor);

        Path memoriesDir = resolveMemoriesDir(parentSession.id());
        FileSystemAgentMemoryStore memoryStore = new FileSystemAgentMemoryStore(memoriesDir);
        advisors.add(AutoMemoryToolsAdvisor.builder()
                .memoryStore(memoryStore)
                .memoriesRootDirectory(memoriesDir.toString())
                .memoryConsolidationTrigger(
                        MemoryConsolidator.triggerWhen(memoryStore, MemoryConsolidator.DEFAULT_THRESHOLD))
                .build());
        advisors.add(MemoryRecallAdvisor.builder()
                .sessionManager(sessionManager)
                .memoryStore(memoryStore)
                .chatClient(ChatClient.builder(chatModel).build())
                .build());
        advisors.add(SkillContextAdvisor.builder().skillManager(skillManager).build());

        List<ToolCallback> allTools = new ArrayList<>(toolkitProvider.getObject().buildToolCallbacks(definition));
        allTools.add(ConversationSearchTool.builder(sessionManager).build().toToolCallback());
        allTools.add(CrossSessionSearchTool.builder(sessionManager, uid).build().toToolCallback());
        if (extraTools != null) {
            allTools.addAll(extraTools);
        }

        Agent agent = Agent.builder()
                .name(agentId)
                .definition(definition)
                .model(chatModel)
                .systemPrompt(systemPromptOverride != null ? systemPromptOverride
                        : definition.content() + ShellSession.envBlock())
                .tools(allTools)
                .hooks(buildBaseHooks())
                .advisors(advisors)
                // reactive_compact：上下文超长被 API 拒绝时强制压缩（绕过触发器）后重试一次
                .reactiveCompactor(sid -> sessionManager.compact(sid, req -> true, stagedStrategy))
                .build();
        return agent;
    }

    private Path resolveMemoriesDir(String parentSessionId) {
        String[] parts = parentSessionId.split("-", 3);
        String mode = parts[0];
        if ("code".equals(mode)) {
            String projectName = parts.length > 1 ? parts[1] : null;
            if (projectName == null || projectName.isBlank()) {
                throw new IllegalStateException("code 模式 sessionId 必须包含 projectName: " + parentSessionId);
            }
            return AppConstants.Memory.projectMemoryDir(projectName);
        }
        return AppConstants.Memory.workMemoryDir();
    }

    /**
     * 基础 Hook 集：预算保护 / 权限审批 / Todo 提醒 / 工具结果落盘。
     * 每次构建 Agent 都 new 新实例（内部持有 per-session 可变状态，避免多智能体共享串扰）。
     */
    private List<IAgentHook> buildBaseHooks() {
        List<IAgentHook> hooks = new ArrayList<>();
        hooks.add(new ToolCallBudgetHook(configManager.getMaxToolCalls()));
        hooks.add(new PermissionHook(approvalStrategies));
        hooks.add(new TodoReminderHook());
        hooks.add(new ToolResultOffloadHook());
        return hooks;
    }
}
