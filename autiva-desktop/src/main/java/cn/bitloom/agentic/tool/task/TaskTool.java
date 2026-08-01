package cn.bitloom.agentic.tool.task;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentDefinitionManager;
import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.agent.advisor.AutoMemoryToolsAdvisor;
import cn.bitloom.agentic.agent.advisor.SessionMemoryAdvisor;
import cn.bitloom.agentic.agent.advisor.SkillContextAdvisor;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.event.AbstractEvent;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.session.EventFilter;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.agentic.session.MessageFilter;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.session.compaction.RecursiveSummarizationCompactionStrategy;
import cn.bitloom.agentic.session.compaction.TokenCountTrigger;
import cn.bitloom.agentic.task.repository.TaskRepository;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.tool.Toolkit;
import cn.bitloom.agentic.tool.session.ConversationSearchTool;
import cn.bitloom.agentic.tool.session.CrossSessionSearchTool;
import cn.bitloom.bridge.desktop.ToolUIBridge;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.exception.AgentException;
import cn.bitloom.store.Store;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import javafx.application.Platform;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 启动新的代理来自主处理复杂的多步骤任务。
 * <p>
 * Task工具启动专门的代理（子进程），它们自主处理复杂任务。
 * 每种代理类型都有特定的能力和可用的工具。
 * <p>
 * Branch 隔离机制：
 * - 子智能体复用父 Session，不创建独立 Session
 * - 通过 branch 字段隔离事件（branch 形如 "subagent.{name}"）
 * - SessionMemoryAdvisor 配合 EventFilter.forBranch(branch) 实现上下文隔离：
 *   子智能体仅可见自己 branch 的事件 + root 事件（主智能体历史）
 * - RuntimeContext 携带 branch，Agent.runStream 自动给事件打标
 * - 子智能体事件通过 ToolUIBridge 推送到 UI（taskId = branch）
 * - resume 时传入 branch 名以延续同名分支上下文
 */
@Slf4j
public class TaskTool extends AbstractTool<TaskTool.Input> {

    private static final String TASK_DESCRIPTION = """
            启动子智能体处理复杂多步骤任务。指定 subagent_type 选择类型（可用类型见上下文）。
            子智能体上下文独立 —— prompt 必须自包含，包含所有必要信息。
            用 run_in_background=true 异步执行，TaskOutput 取结果。
            """;

    /**
     * 输入参数 record
     */
    public record Input(
            @ToolParam(description = "任务的简短描述") String description,
            @ToolParam(description = "代理要执行的任务") String prompt,
            @ToolParam(description = "用于此任务的专门代理类型") String subagent_type,
            @ToolParam(description = "可选模型覆盖", required = false) String model,
            @ToolParam(description = "可选恢复代理ID", required = false) String resume,
            @ToolParam(description = "是否在后台运行", required = false) Boolean run_in_background) {
    }

    /**
     * 子智能体任务调用参数
     */
    public record TaskCall(String description, String prompt, String subagentName, String resume, Boolean runInBackground) {
    }

    private final TaskRepository taskRepository;
    private final ToolUIBridge toolUIBridge;
    private final FileSystemSessionManager sessionManager;
    private final AgentDefinitionManager definitionManager;
    private final ModelFactory modelFactory;
    private final Toolkit toolkit;
    private final SkillManager skillManager;

    private TaskTool(String description,
                     TaskRepository taskRepository,
                     ToolUIBridge toolUIBridge,
                     FileSystemSessionManager sessionManager,
                     AgentDefinitionManager definitionManager,
                     ModelFactory modelFactory,
                     Toolkit toolkit,
                     SkillManager skillManager) {
        super("Task", description, Input.class);
        this.taskRepository = taskRepository;
        this.toolUIBridge = toolUIBridge;
        this.sessionManager = sessionManager;
        this.definitionManager = definitionManager;
        this.modelFactory = modelFactory;
        this.toolkit = toolkit;
        this.skillManager = skillManager;
    }

    @Override
    public @NonNull ToolResult execute(Input input, ToolContext context) {
        String subagentName = input.subagent_type();

        TaskCall taskCall = new TaskCall(
                input.description(),
                input.prompt(),
                subagentName,
                input.resume(),
                input.run_in_background()
        );

        // 子智能体复用父 Session，通过 branch 隔离事件
        String parentSessionId = resolveParentSessionId(context);
        if (parentSessionId == null) {
            throw AgentException.subagentExecutionFailed(subagentName,
                    new IllegalStateException("无法解析父会话ID"));
        }
        Session parentSession = sessionManager.getById(parentSessionId);
        if (parentSession == null) {
            throw AgentException.subagentNotFound("父会话不存在: " + parentSessionId);
        }

        // branch 标识：resume 时复用传入分支名，否则按 subagent 名生成
        // 同一 subagent 多次调用共享同一 branch，便于上下文延续
        String branch = Objects.nonNull(taskCall.resume())
                ? taskCall.resume()
                : "subagent." + subagentName;

        // taskId 直接用 branch，供 ToolUIBridge 路由
        String taskId = branch;

        // TaskCard UI
        if (this.toolUIBridge != null) {
            ObjectNode taskJson = JsonUtils.createObject();
            taskJson.put("subagentName", subagentName);
            taskJson.put("description", taskCall.description());
            taskJson.put("taskId", taskId);
            this.toolUIBridge.createTaskCard(taskId, JsonUtils.toJson(taskJson));
        }

        // 后台任务
        if (Boolean.TRUE.equals(taskCall.runInBackground())) {
            var bgTask = this.taskRepository.putTask(taskId, () -> {
                try {
                    String result = executeSubagent(taskCall, parentSession, branch);
                    if (this.toolUIBridge != null) {
                        this.toolUIBridge.completeTaskCard(taskId, null);
                    }
                    return result;
                } catch (Exception e) {
                    if (this.toolUIBridge != null) {
                        this.toolUIBridge.failTaskCard(taskId, e.getMessage());
                    }
                    throw e;
                }
            });
            return ToolResult.success("后台任务已启动",
                    Map.of("task_id", bgTask.getTaskId()),
                    String.format("task_id: %s\n\n后台任务已启动，ID: %s\n使用TaskOutput工具并传入task_id='%s'来获取结果。",
                            bgTask.getTaskId(), bgTask.getTaskId(), bgTask.getTaskId()));
        }

        // 前台同步执行
        try {
            String result = executeSubagent(taskCall, parentSession, branch);
            if (this.toolUIBridge != null) {
                this.toolUIBridge.completeTaskCard(taskId, null);
            }
            return ToolResult.success("任务已完成", Map.of("subagentName", subagentName), result);
        } catch (Exception e) {
            if (this.toolUIBridge != null) {
                this.toolUIBridge.failTaskCard(taskId, e.getMessage());
            }
            throw AgentException.subagentExecutionFailed(subagentName, e);
        }
    }

    /**
     * 从 ToolContext 解析父会话ID
     */
    private String resolveParentSessionId(ToolContext context) {
        if (context != null) {
            Object sessionId = context.getContext().get("sessionId");
            if (sessionId instanceof String id) {
                return id;
            }
        }
        return null;
    }

    /**
     * 根据父 session ID 解析 memory 目录（继承父 session 的 mode 和 projectName）。
     */
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
     * 执行子智能体任务（复用父 Session，通过 branch 隔离事件）。
     * <p>
     * 流程：
     * 1. 构建带 branch 过滤的 Agent（SessionMemoryAdvisor 仅检索本 branch + root 事件）
     * 2. RuntimeContext 携带 branch，Agent.runStream 自动给事件打标
     * 3. 通过 ToolUIBridge 推送事件到 UI（taskId = branch）
     * 4. 阻塞等待流完成（blockLast），累积 assistant 文本作为返回结果
     * <p>
     * 不再创建子 Session，事件直接持久化到父 Session（带 branch 字段）。
     * 前台同步执行时主智能体阻塞等待，无并发写入风险；后台任务事件有 branch 隔离。
     */
    private String executeSubagent(TaskCall taskCall, Session parentSession, String branch) {
        String parentSessionId = parentSession.id();
        String taskId = branch;
        MessageEvent inputEvent = MessageEvent.userMessage(parentSessionId, taskCall.prompt());

        Agent agent = buildAgent(parentSession, taskCall.subagentName(), branch);
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(parentSessionId)
                .userId(parentSession.userId())
                .branch(branch)
                .build();
        StringBuilder result = new StringBuilder();
        agent.runStream(inputEvent, ctx)
                .doOnNext(event -> {
                    if (toolUIBridge != null) {
                        Platform.runLater(() -> toolUIBridge.processEvent(taskId, event));
                    }
                    if (event instanceof MessageEvent me && me.isAssistantMessage() && me.getText() != null) {
                        result.append(me.getText());
                    }
                })
                .blockLast();
        try {
            sessionManager.flush(parentSessionId);
        } catch (Exception e) {
            log.warn("[TaskTool] flush 失败: parentSessionId={}, branch={}", parentSessionId, branch, e);
        }
        return "agent_id: " + taskId + "\n\n" + result;
    }

    /**
     * 构建 Agent（带 branch 过滤的 SessionMemoryAdvisor）。
     */
    private Agent buildAgent(Session parentSession, String agentId, String branch) {
        AgentDefinition definition = definitionManager.getDefinition(agentId);
        if (definition == null) {
            throw AgentException.subagentNotFound("子智能体定义不存在: " + agentId
                    + "，可用定义: " + definitionManager.getSubagentDefinitions().stream()
                            .map(AgentDefinition::name).toList());
        }
        ModelTypeEnum modelType = Store.selectedModel.get() != null ? Store.selectedModel.get() : ModelTypeEnum.DEEPSEEK;
        ChatModel chatModel = modelFactory.model(modelType);
        String uid = parentSession.userId() != null ? parentSession.userId() : "default-user";

        List<Advisor> advisors = new ArrayList<>();

        // EventFilter.forBranch(branch): 子智能体仅能看到自己 branch 的事件 + root 事件（主智能体历史）
        // 配合 SessionMemoryAdvisor 写入 branch 字段，实现多智能体共享 session 但上下文隔离
        SessionMemoryAdvisor sessionMemoryAdvisor = SessionMemoryAdvisor.builder(sessionManager)
                .defaultUserId(uid)
                .eventFilter(EventFilter.forBranch(branch))
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

        Path memoriesDir = resolveMemoriesDir(parentSession.id());
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
                .systemPrompt(definition.content())
                .tools(allTools)
                .hooks(List.of())
                .advisors(advisors)
                .build();
        log.info("构建子智能体: agentId={}, branch={}", agentId, branch);
        return agent;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private TaskRepository taskRepository;
        private ToolUIBridge toolUIBridge;
        private FileSystemSessionManager sessionManager;
        private AgentDefinitionManager definitionManager;
        private ModelFactory modelFactory;
        private Toolkit toolkit;
        private SkillManager skillManager;

        private Builder() {
        }

        public Builder taskRepository(TaskRepository taskRepository) {
            this.taskRepository = taskRepository;
            return this;
        }

        public Builder toolUIBridge(ToolUIBridge toolUIBridge) {
            this.toolUIBridge = toolUIBridge;
            return this;
        }

        public Builder sessionManager(FileSystemSessionManager sessionManager) {
            this.sessionManager = sessionManager;
            return this;
        }

        public Builder definitionManager(AgentDefinitionManager definitionManager) {
            this.definitionManager = definitionManager;
            return this;
        }

        public Builder modelFactory(ModelFactory modelFactory) {
            this.modelFactory = modelFactory;
            return this;
        }

        public Builder toolkit(Toolkit toolkit) {
            this.toolkit = toolkit;
            return this;
        }

        public Builder skillManager(SkillManager skillManager) {
            this.skillManager = skillManager;
            return this;
        }

        public TaskTool build() {
            Assert.notNull(this.sessionManager, "必须提供sessionManager");
            Assert.notNull(this.definitionManager, "必须提供definitionManager");
            Assert.notNull(this.modelFactory, "必须提供modelFactory");
            Assert.notNull(this.toolkit, "必须提供toolkit");
            Assert.notNull(this.skillManager, "必须提供skillManager");

            return new TaskTool(TASK_DESCRIPTION,
                    this.taskRepository, this.toolUIBridge,
                    this.sessionManager, this.definitionManager,
                    this.modelFactory, this.toolkit, this.skillManager);
        }
    }
}
