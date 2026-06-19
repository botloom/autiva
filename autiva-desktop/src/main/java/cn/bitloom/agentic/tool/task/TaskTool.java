package cn.bitloom.agentic.tool.task;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentDefinitionManager;
import cn.bitloom.agentic.agent.AgentKind;
import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.task.repository.TaskRepository;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.Toolkit;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.exception.AgentException;
import cn.bitloom.bridge.desktop.ToolUIBridge;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 启动新的代理来自主处理复杂的多步骤任务。
 * <p>
 * Task工具启动专门的代理（子进程），它们自主处理复杂任务。
 * 每种代理类型都有特定的能力和可用的工具。
 * <p>
 * 参考 Claude Code 子智能体设计：
 * - 子智能体是独立的 LLM 调用，不创建 Session
 * - 执行完即丢弃，只返回最终文本给主对话
 * - 不持久化中间过程
 */
@Slf4j
public class TaskTool extends AbstractTool<TaskTool.Input> {

    private static final String TASK_DESCRIPTION_TEMPLATE = """
            启动新的代理来自主处理复杂的多步骤任务。
            
            Task工具启动专门的代理（子进程），它们自主处理复杂任务。每种代理类型都有特定的能力和可用的工具。
            
            可用的代理类型及其能力：
            %s
            
            使用Task工具时，必须指定subagent_type参数来选择使用哪种代理类型。
            
            何时不应使用Task工具：
            - 简单的网页内容获取，可以直接使用WebFetch工具
            - 管理任务列表，使用TodoWrite工具
            - 向用户提问，使用AskUserQuestion工具
            
            使用说明：
            - 始终包含简短描述（3-5个词）总结代理将要做什么
            - 尽可能并发启动多个代理，以最大化性能；为此，使用包含多个工具使用的单条消息
            - 当代理完成时，它会向你返回单条消息。代理返回的结果对用户不可见。要向用户显示结果，你应该向用户发送包含结果简明摘要的文本消息。
            - 你可以选择使用run_in_background参数在后台运行代理。当代理在后台运行时，你需要使用TaskOutput在其完成后检索结果。你可以在后台代理运行时继续工作 - 当你需要它们的结果来继续时，可以使用阻塞模式的TaskOutput暂停并等待它们的结果。
            - 在后台运行任务时，Task工具会立即返回task_id。使用带有此task_id的TaskOutput工具检查状态并检索结果。
            - 可以通过传递前一次调用的代理ID使用`resume`参数恢复代理。恢复时，代理继续保留其完整的先前上下文。不恢复时，每次调用都是全新的，你应该提供包含所有必要上下文的详细任务描述。
            - 当代理完成时，它会向你返回单条消息及其代理ID。如果需要后续工作，你可以使用此ID稍后恢复代理。
            - 提供清晰、详细的提示（delegation brief），以便代理可以自主工作并准确返回你需要的信息。代理不继承当前对话上下文，所以必须在 prompt 中提供所有必要信息。
            - 代理的输出通常应该被信任
            - 明确告诉代理你期望它编写代码还是仅进行研究（搜索、文件读取、网络获取等），因为它不知道用户的意图
            - 如果用户指定他们希望你"并行"运行代理，你必须发送包含多个Task工具使用内容块的单条消息。
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
    public record TaskCall(String description, String prompt, String subagentName,
                           String resume, Boolean runInBackground) {
    }

    private final Toolkit toolkit;
    private final ModelFactory modelFactory;
    private final SkillManager skillManager;
    private final TaskRepository taskRepository;
    private final ToolUIBridge toolUIBridge;
    private final AgentDefinitionManager definitionManager;

    private TaskTool(String description, Toolkit toolkit, ModelFactory modelFactory,
                     SkillManager skillManager, TaskRepository taskRepository,
                     ToolUIBridge toolUIBridge, AgentDefinitionManager definitionManager) {
        super("Task", description, Input.class);
        this.toolkit = toolkit;
        this.modelFactory = modelFactory;
        this.skillManager = skillManager;
        this.taskRepository = taskRepository;
        this.toolUIBridge = toolUIBridge;
        this.definitionManager = definitionManager;
    }

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        String subagentName = input.subagent_type();

        // 创建子智能体 Agent（纯净模式，无 Advisor）
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

        // 用 taskId 标识任务（不创建 Session）
        String taskId;
        if (Objects.nonNull(taskCall.resume())) {
            taskId = taskCall.resume();
        } else {
            taskId = subagentName + "-" + System.currentTimeMillis();
        }

        // TaskCard UI
        if (this.toolUIBridge != null) {
            ObjectNode taskJson = JsonUtils.createObject();
            taskJson.put("subagentName", subagentName);
            taskJson.put("description", taskCall.description());
            taskJson.put("taskId", taskId);
            this.toolUIBridge.createTaskCard(taskId, JsonUtils.toJson(taskJson));
        }

        // 构造子智能体的 RuntimeContext（无 Session）
        RuntimeContext ctx = new RuntimeContext(taskId);

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
     * 创建子智能体 Agent 实例（纯净模式，不注册任何 Advisor）
     */
    private Agent createSubagent(String name) {
        AgentDefinition definition = definitionManager.getDefinition(name);
        if (definition == null) {
            throw AgentException.subagentNotFound(name);
        }

        ChatModel chatModel = modelFactory.model(resolveModel(definition, null));

        return Agent.builder()
                .name(name)
                .definition(definition)
                .model(chatModel)
                .systemPrompt(buildSubagentSystemPrompt(definition))
                .tools(toolkit.buildToolCallbacks(definition))
                .hooks(List.of())
                .tollCallMessage(false)
                .build();
    }

    /**
     * 执行子智能体任务（复用 agent.runStream，通过 RuntimeContext 传递参数）
     */
    private String executeSubagent(Agent agent, RuntimeContext ctx, TaskCall taskCall, String taskId) {
        UserMessage userMessage = new UserMessage(taskCall.prompt());
        StringBuilder fullResult = new StringBuilder();

        agent.runStream(ctx, userMessage)
                .doOnNext(msg -> {
                    String text = msg.getText();
                    if (text != null && !text.isEmpty()) {
                        fullResult.append(text);
                        if (this.toolUIBridge != null) {
                            this.toolUIBridge.appendTaskOutput(taskId, text);
                        }
                    }
                })
                .doOnError(e -> log.error("子智能体执行失败: taskId={}", taskId, e))
                .blockLast();

        return "agent_id: " + taskId + "\n\n" + fullResult;
    }

    /**
     * 解析子智能体使用的模型
     */
    private ModelTypeEnum resolveModel(AgentDefinition definition, Map<String, Object> context) {
        if (definition.model() != null && !definition.model().isEmpty()) {
            try {
                return ModelTypeEnum.valueOf(definition.model().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("未知的模型类型: {}, 使用默认模型", definition.model());
            }
        }
        Object contextModel = context != null ? context.get("model") : null;
        if (contextModel instanceof ModelTypeEnum modelTypeEnum) {
            return modelTypeEnum;
        }
        return ModelTypeEnum.DEEPSEEK;
    }

    /**
     * 构建子智能体的系统提示词
     */
    private String buildSubagentSystemPrompt(AgentDefinition definition) {
        StringBuilder sb = new StringBuilder();
        sb.append(definition.content());

        // 注入技能内容
        if (skillManager != null && definition.skills() != null && !definition.skills().isEmpty()) {
            var skills = skillManager.getAllSkills();
            String skillsContent = skills.stream()
                    .filter(s -> definition.skills().contains(s.name()))
                    .map(skill -> "%s\n\n%s".formatted(skill.toXml(), skill.content()))
                    .collect(Collectors.joining("\n\n"));
            if (!skillsContent.isEmpty()) {
                sb.append("\n").append(skillsContent);
            }
        }

        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Toolkit toolkit;
        private ModelFactory modelFactory;
        private SkillManager skillManager;
        private TaskRepository taskRepository;
        private ToolUIBridge toolUIBridge;
        private AgentDefinitionManager agentDefinitionManager;

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

        public Builder skillManager(SkillManager skillManager) {
            this.skillManager = skillManager;
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

        public TaskTool build() {
            Assert.notNull(this.toolkit, "必须提供toolkit");
            Assert.notNull(this.modelFactory, "必须提供modelFactory");
            Assert.notNull(this.agentDefinitionManager, "必须提供agentDefinitionManager");

            // 获取所有子智能体定义，构建描述
            List<AgentDefinition> subagentDefs = agentDefinitionManager.getSubagentDefinitions();
            String subagentRegistrations = subagentDefs.stream()
                    .map(AgentDefinition::toRegistrationText)
                    .collect(Collectors.joining("\n"));

            String description = TASK_DESCRIPTION_TEMPLATE.formatted(subagentRegistrations);

            return new TaskTool(description, toolkit, modelFactory,
                    this.skillManager, this.taskRepository, this.toolUIBridge,
                    this.agentDefinitionManager);
        }
    }
}
