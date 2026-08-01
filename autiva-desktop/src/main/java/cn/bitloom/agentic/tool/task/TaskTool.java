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
import cn.bitloom.agentic.session.CreateSessionRequest;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.agentic.session.MessageFilter;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.session.SessionTypeEnum;
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
 * 子 Session 机制：
 * - 每个子智能体任务通过 ISessionManager 创建一个子 Session（持久化）
 * - 子智能体 Agent 由本工具的 buildAgent 构建
 * - 子 Session 通过 parentId 关联父 Session，支持多轮对话上下文
 * - resume 时通过子 Session 恢复历史对话
 * - 子智能体事件通过 ToolUIBridge 推送到 UI
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

        // 创建或恢复子 Session
        Session subSession;
        String parentSessionId = resolveParentSessionId(context);

        if (Objects.nonNull(taskCall.resume())) {
            // resume：从 ISessionManager 获取已有子 Session
            subSession = sessionManager.getById(taskCall.resume());
            if (subSession == null) {
                throw AgentException.subagentNotFound("无法恢复代理ID: " + taskCall.resume());
            }
        } else {
            // 新建子 Session
            subSession = createSubSession(subagentName, parentSessionId);
        }

        String taskId = subSession.id();

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
                    String result = executeSubagent(taskCall, subSession);
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
            String result = executeSubagent(taskCall, subSession);
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
     * 创建子 Session（继承父 session 的 mode 和 projectName）。
     */
    private Session createSubSession(String subagentName, String parentSessionId) {
        String[] parts = parentSessionId.split("-", 3);
        String mode = parts[0];
        String sessionId;
        if ("code".equals(mode) && parts.length > 1) {
            // code 模式：code-{projectName}-SUB-...
            sessionId = "code-" + parts[1] + "-" + SessionTypeEnum.SUB + "-" + "desktopApp" + "-" + Store.userId.get() + "-" + System.currentTimeMillis();
        } else {
            // work 模式：work-SUB-...
            sessionId = "work-" + SessionTypeEnum.SUB + "-" + "desktopApp" + "-" + Store.userId.get() + "-" + System.currentTimeMillis();
        }

        CreateSessionRequest request = CreateSessionRequest.builder()
                .id(sessionId)
                .userId(Store.userId.get() != null ? Store.userId.get() : "default-user")
                .build();
        return sessionManager.create(request);
    }

    /**
     * 执行子智能体任务（直接调用 Agent.runStream，per-session 锁保证串行）。
     * <p>
     * 流程：
     * 1. 在 per-session 锁保护下构建 Agent 并执行
     * 2. 通过 ToolUIBridge 推送事件到 UI
     * 3. 阻塞等待流完成（blockLast），累积 assistant 文本作为返回结果
     */
    private String executeSubagent(TaskCall taskCall, Session subSession) {
        String taskId = subSession.id();
        MessageEvent inputEvent = MessageEvent.userMessage(taskId, taskCall.prompt());

        return sessionManager.withLock(taskId, () -> {
            Agent agent = buildAgent(subSession, taskCall.subagentName());
            RuntimeContext ctx = RuntimeContext.builder()
                    .sessionId(taskId)
                    .userId(subSession.userId())
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
                sessionManager.flush(taskId);
            } catch (Exception e) {
                log.warn("[TaskTool] flush 失败: taskId={}", taskId, e);
            }
            return "agent_id: " + taskId + "\n\n" + result;
        });
    }

    /**
     * 构建 Agent。
     */
    private Agent buildAgent(Session session, String agentId) {
        AgentDefinition definition = definitionManager.getDefinition(agentId);
        if (definition == null) {
            throw AgentException.subagentNotFound("子智能体定义不存在: " + agentId
                    + "，可用定义: " + definitionManager.getSubagentDefinitions().stream()
                            .map(AgentDefinition::name).toList());
        }
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

        Path memoriesDir = resolveMemoriesDir(session.id());
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
        log.info("构建子智能体: agentId={}", agentId);
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
