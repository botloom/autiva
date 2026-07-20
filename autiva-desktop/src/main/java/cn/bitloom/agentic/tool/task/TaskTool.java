package cn.bitloom.agentic.tool.task;

import cn.bitloom.agentic.agent.*;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.event.EventConverter;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.session.InMemorySessionManager;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.session.SessionRespTypeEnum;
import cn.bitloom.agentic.session.SessionTypeEnum;
import cn.bitloom.agentic.task.repository.TaskRepository;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.tool.Toolkit;
import cn.bitloom.bridge.desktop.ToolUIBridge;
import cn.bitloom.exception.AgentException;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

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
 * - 每个子智能体任务创建一个子 Session（InMemorySessionManager 管理）
 * - 子 Session 拥有 ChatMemory，支持多轮对话上下文
 * - resume 时通过子 Session 恢复历史对话
 * - 子 Session 纯内存存储，不持久化到磁盘
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

    private final Toolkit toolkit;
    private final ModelFactory modelFactory;
    private final TaskRepository taskRepository;
    private final ToolUIBridge toolUIBridge;
    private final AgentDefinitionManager definitionManager;
    private final InMemorySessionManager inMemorySessionManager;

    private TaskTool(String description, Toolkit toolkit, ModelFactory modelFactory,
                     TaskRepository taskRepository,
                     ToolUIBridge toolUIBridge, AgentDefinitionManager definitionManager,
                     InMemorySessionManager inMemorySessionManager) {
        super("Task", description, Input.class);
        this.toolkit = toolkit;
        this.modelFactory = modelFactory;
        this.taskRepository = taskRepository;
        this.toolUIBridge = toolUIBridge;
        this.definitionManager = definitionManager;
        this.inMemorySessionManager = inMemorySessionManager;
    }

    @Override
    public @NonNull ToolResult execute(Input input, ToolContext context) {
        String subagentName = input.subagent_type();

        // 创建子智能体 Agent（带 ChatMemory）
        Agent agent = createSubagent(subagentName);
        if (agent.getDefinition().kind() != AgentKind.SUBAGENT) {
            throw AgentException.subagentNotFound(subagentName);
        }

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
            // resume：从 InMemorySessionManager 获取已有子 Session
            subSession = inMemorySessionManager.getById(taskCall.resume());
            if (subSession == null) {
                throw AgentException.subagentNotFound("无法恢复代理ID: " + taskCall.resume());
            }
        } else {
            // 新建子 Session
            subSession = inMemorySessionManager.create(
                    subagentName, parentSessionId, SessionTypeEnum.SUB,
                    SessionRespTypeEnum.STREAM, ModelTypeEnum.DEEPSEEK);
        }

        String taskId = subSession.getId();

        // TaskCard UI
        if (this.toolUIBridge != null) {
            ObjectNode taskJson = JsonUtils.createObject();
            taskJson.put("subagentName", subagentName);
            taskJson.put("description", taskCall.description());
            taskJson.put("taskId", taskId);
            this.toolUIBridge.createTaskCard(taskId, JsonUtils.toJson(taskJson));
        }

        // 构造子智能体的 RuntimeContext（包含子 Session）
        RuntimeContext ctx = new RuntimeContext(subSession);
        ctx.param("sessionId", taskId);

        // 后台任务
        if (Boolean.TRUE.equals(taskCall.runInBackground())) {
            var bgTask = this.taskRepository.putTask(taskId, () -> {
                try {
                    String result = executeSubagent(agent, ctx, taskCall, taskId);
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
            String result = executeSubagent(agent, ctx, taskCall, taskId);
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
     * 创建子智能体 Agent 实例（带 ChatMemory，支持对话历史）
     */
    private Agent createSubagent(String name) {
        AgentDefinition definition = definitionManager.getDefinition(name);
        if (definition == null) {
            throw AgentException.subagentNotFound(name);
        }

        ChatModel chatModel = modelFactory.model(ModelTypeEnum.DEEPSEEK);

        return Agent.builder()
                .name(name)
                .definition(definition)
                .model(chatModel)
                .systemPrompt(definition.content())
                .tools(toolkit.buildToolCallbacks(definition))
                .hooks(List.of())
                .memory(inMemorySessionManager.getChatMemory())
                .build();
    }

    /**
     * 执行子智能体任务（复用 agent.runStream，通过 RuntimeContext 传递参数）
     */
    private String executeSubagent(Agent agent, RuntimeContext ctx, TaskCall taskCall, String taskId) {
        UserMessage userMessage = new UserMessage(taskCall.prompt());
        StringBuilder fullResult = new StringBuilder();

        agent.runStream(ctx, userMessage)
                .map(msg -> EventConverter.fromMessage(taskId, msg))
                .doOnNext(event -> {
                    EventBus.publishOut(event);
                    if (event.isAssistantMessage() && event.getText() != null) {
                        fullResult.append(event.getText());
                    }
                })
                .doOnError(e -> log.error("子智能体执行失败: taskId={}", taskId, e))
                .blockLast();

        return "agent_id: " + taskId + "\n\n" + fullResult;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Toolkit toolkit;
        private ModelFactory modelFactory;
        private TaskRepository taskRepository;
        private ToolUIBridge toolUIBridge;
        private AgentDefinitionManager agentDefinitionManager;
        private InMemorySessionManager inMemorySessionManager;

        private Builder() {
        }

        public Builder toolkit(Toolkit toolkit) {
            this.toolkit = toolkit;
            return this;
        }

        public Builder modelFactory(ModelFactory modelFactory) {
            this.modelFactory = modelFactory;
            return this;
        }

        public Builder taskRepository(TaskRepository taskRepository) {
            this.taskRepository = taskRepository;
            return this;
        }

        public Builder toolUIBridge(ToolUIBridge toolUIBridge) {
            this.toolUIBridge = toolUIBridge;
            return this;
        }

        public Builder agentDefinitionManager(AgentDefinitionManager agentDefinitionManager) {
            this.agentDefinitionManager = agentDefinitionManager;
            return this;
        }

        public Builder inMemorySessionManager(InMemorySessionManager inMemorySessionManager) {
            this.inMemorySessionManager = inMemorySessionManager;
            return this;
        }

        public TaskTool build() {
            Assert.notNull(this.toolkit, "必须提供toolkit");
            Assert.notNull(this.modelFactory, "必须提供modelFactory");
            Assert.notNull(this.agentDefinitionManager, "必须提供agentDefinitionManager");
            Assert.notNull(this.inMemorySessionManager, "必须提供inMemorySessionManager");

            return new TaskTool(TASK_DESCRIPTION, toolkit, modelFactory,
                    this.taskRepository, this.toolUIBridge,
                    this.agentDefinitionManager, this.inMemorySessionManager);
        }
    }
}
