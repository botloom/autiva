package cn.bitloom.agentic.tool.task;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentKind;
import cn.bitloom.agentic.agent.advisor.HookAdvisor;
import cn.bitloom.agentic.hook.AgentHook;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.session.SessionManager;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.task.repository.TaskRepository;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.Toolkit;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.exception.AgentException;
import cn.bitloom.node.TaskCard;
import cn.bitloom.store.ToolUIBridge;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 启动新的代理来自主处理复杂的多步骤任务。
 * <p>
 * Task工具启动专门的代理（子进程），它们自主处理复杂任务。
 * 每种代理类型都有特定的能力和可用的工具。
 */
@Slf4j
public class TaskTool extends AbstractTool<TaskTool.Input> {

    private static final String TASK_DESCRIPTION_TEMPLATE = """
            启动新的代理来自主处理复杂的多步骤任务。
            
            Task工具启动专门的代理（子进程），它们自主处理复杂任务。每种代理类型都有特定的能力和可用的工具。
            
            可用的代理类型及其能力：
            %s
            
            使用Task工具时，必须指定subagent_type参数来选择使用哪种代理类型。
            
            
            
            
            
            如何选择子智能体：
            - 执行Shell命令、构建、部署 → 使用 Bash 子智能体
            
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
            - 提供清晰、详细的提示，以便代理可以自主工作并准确返回你需要的信息。
            - 具有"访问当前上下文"权限的代理可以看到工具调用之前的完整对话历史。使用这些代理时，你可以编写引用先前上下文的简洁提示（例如，"调查上面讨论的错误"）而不是重复信息。代理将接收所有先前的消息并理解上下文。
            - 代理的输出通常应该被信任
            - 明确告诉代理你期望它编写代码还是仅进行研究（搜索、文件读取、网络获取等），因为它不知道用户的意图
            - 如果代理描述提到应该主动使用它，那么你应该尽最大努力使用它，而不需要用户先请求。使用你的判断力。
            - 如果用户指定他们希望你"并行"运行代理，你必须发送包含多个Task工具使用内容块的单条消息。例如，如果你需要并行启动code-reviewer代理和test-runner代理，发送包含两个工具调用的单条消息。
            
            示例用法：
            
            <example>
            user: "运行构建并修复任何类型错误"
            assistant: 我将使用 Bash 子智能体运行构建
            assistant: 使用Task工具，subagent_type="Bash"，prompt="运行构建命令"
            </example>
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
    record TaskCall(String description, String prompt, String subagentName,
                    String resume, Boolean runInBackground) {
    }

    private final Toolkit toolkit;
    private final ModelFactory modelFactory;
    private final SkillManager skillManager;
    private final TaskRepository taskRepository;
    private final SessionManager sessionManager;
    private final ToolUIBridge toolUIBridge;

    private TaskTool(String description, Toolkit toolkit, ModelFactory modelFactory,
                     SkillManager skillManager, TaskRepository taskRepository,
                     SessionManager sessionManager, ToolUIBridge toolUIBridge) {
        super("Task", description, Input.class);
        this.toolkit = toolkit;
        this.modelFactory = modelFactory;
        this.skillManager = skillManager;
        this.taskRepository = taskRepository;
        this.sessionManager = sessionManager;
        this.toolUIBridge = toolUIBridge;
    }

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        String subagentName = input.subagent_type();

        // 直接创建子智能体 Agent
        Agent agent = createSubagent(subagentName);
        if (agent.getDefinition().kind() != AgentKind.SUBAGENT) {
            throw AgentException.subagentNotFound(subagentName);
        }

        // 从 Input 构建 TaskCall
        TaskCall taskCall = new TaskCall(
                input.description(),
                input.prompt(),
                subagentName,
                input.resume(),
                input.run_in_background()
        );

        Session childSession;
        if (Objects.nonNull(taskCall.resume())) {
            childSession = this.sessionManager.getById(taskCall.resume());
        } else {
            childSession = this.sessionManager.forkSession((String) context.getContext().get("sessionId"), subagentName);
        }

        if (this.toolUIBridge != null) {
            JSONObject taskJson = new JSONObject();
            taskJson.put("subagentName", subagentName);
            taskJson.put("description", taskCall.description());
            taskJson.put("taskId", childSession.getId());
            TaskCard taskCard = this.toolUIBridge.createTaskCard(childSession.getId(), taskJson.toJSONString());
            if (taskCard != null) {
                taskCard.subscribeSession(childSession.getId(), this.sessionManager);
            }
        }

        Map<String, Object> execContext = Map.of("sessionId", childSession.getId(), "model", context.getContext().get("model"));

        if (Boolean.TRUE.equals(taskCall.runInBackground())) {
            var bgTask = this.taskRepository.putTask(childSession.getId(),
                    () -> {
                        try {
                            String result = executeSubagent(agent, taskCall, execContext, chunk -> {
                                if (this.toolUIBridge != null) {
                                    this.toolUIBridge.appendTaskOutput(childSession.getId(), chunk);
                                }
                            });
                            if (this.toolUIBridge != null) {
                                this.toolUIBridge.completeTaskCard(childSession.getId(), null);
                            }
                            return result;
                        } catch (Exception e) {
                            if (this.toolUIBridge != null) {
                                this.toolUIBridge.failTaskCard(childSession.getId(), e.getMessage());
                            }
                            throw e;
                        }
                    });

            return ToolResult.success("后台任务已启动",
                    Map.of("task_id", bgTask.getTaskId()),
                    String.format("task_id: %s\n\n后台任务已启动，ID: %s\n使用TaskOutput工具并传入task_id='%s'来获取结果。",
                            bgTask.getTaskId(), bgTask.getTaskId(), bgTask.getTaskId()));
        }

        try {
            String result = executeSubagent(agent, taskCall, execContext, chunk -> {
                if (this.toolUIBridge != null) {
                    this.toolUIBridge.appendTaskOutput(childSession.getId(), chunk);
                }
            });
            if (this.toolUIBridge != null) {
                this.toolUIBridge.completeTaskCard(childSession.getId(), null);
            }
            return ToolResult.success("任务已完成", Map.of("subagentName", subagentName), result);
        } catch (Exception e) {
            if (this.toolUIBridge != null) {
                this.toolUIBridge.failTaskCard(childSession.getId(), e.getMessage());
            }
            throw AgentException.subagentExecutionFailed(subagentName, e);
        }
    }

    /**
     * 从文件系统加载子智能体定义
     */
    private AgentDefinition loadSubagentDefinition(String name) {
        Path agentFile = AppConstants.Base.agentDefinitionFile(name);
        if (Files.exists(agentFile)) {
            return AgentDefinition.fromMarkdown(agentFile);
        }
        throw AgentException.subagentNotFound(name);
    }

    /**
     * 创建子智能体 Agent 实例
     */
    private Agent createSubagent(String name) {
        AgentDefinition definition = loadSubagentDefinition(name);
        return Agent.builder()
                .agentId(name)
                .definition(definition)
                .modelFactory(modelFactory)
                .tools(toolkit.buildToolCallbacks(definition))
                .hooks(List.of())
                .build();
    }

    /**
     * 扫描文件系统获取可用子智能体定义列表
     */
    private static List<AgentDefinition> scanSubagentDefinitions() {
        Path agentsDir = AppConstants.Base.AGENTS_DIR;
        if (!Files.exists(agentsDir) || !Files.isDirectory(agentsDir)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(agentsDir)) {
            return dirs
                    .filter(Files::isDirectory)
                    .map(dir -> dir.resolve("agent.md"))
                    .filter(Files::exists)
                    .map(agentFile -> {
                        try {
                            return AgentDefinition.fromMarkdown(agentFile);
                        } catch (Exception e) {
                            log.warn("加载智能体定义失败: {}", agentFile, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .filter(d -> d.kind() == AgentKind.SUBAGENT)
                    .toList();
        } catch (java.io.IOException e) {
            log.warn("扫描智能体目录失败: {}", agentsDir, e);
            return List.of();
        }
    }

    /**
     * 执行子智能体任务（原 Agent.execute 逻辑）
     */
    private String executeSubagent(Agent agent, TaskCall taskCall, Map<String, Object> context, Consumer<String> onChunk) {
        ModelTypeEnum model = resolveModel(agent, context);
        String sessionId = (String) context.get("sessionId");
        ChatClient chatClient = getOrCreateChatClient(agent, model);

        String systemPrompt = buildSubagentSystemPrompt(agent);

        if (onChunk != null) {
            // 流式执行
            StringBuilder fullResult = new StringBuilder();
            AtomicBoolean stopped = new AtomicBoolean(false);

            chatClient.prompt()
                    .system(systemPrompt)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .user(taskCall.prompt())
                    .stream()
                    .chatResponse()
                    .takeUntil(chatResponse -> sessionManager.isStop(sessionId))
                    .doOnNext(chatResponse -> {
                        var result = chatResponse.getResult();
                        AssistantMessage output = result.getOutput();
                        String text = output.getText();
                        if (text != null && !text.isEmpty()) {
                            fullResult.append(text);
                            onChunk.accept(text);
                        }
                    })
                    .doOnComplete(() -> {
                        if (sessionManager.isStop(sessionId)) {
                            stopped.set(true);
                        }
                        if (stopped.get()) {
                            onChunk.accept("\n[已停止]");
                        } else {
                            onChunk.accept("\n[完成] agent_id: " + sessionId);
                        }
                    })
                    .doOnError(e -> {
                        if (e instanceof WebClientResponseException webEx) {
                            log.error("子智能体流式执行失败 - Status: {}, Body: {}", webEx.getStatusCode(), webEx.getResponseBodyAsString(), e);
                            onChunk.accept("\n[错误] " + webEx.getStatusCode() + ": " + webEx.getResponseBodyAsString());
                        } else {
                            log.error("子智能体流式执行失败", e);
                            onChunk.accept("\n[错误] " + e.getMessage());
                        }
                    })
                    .blockLast();

            return "agent_id: " + sessionId + "\n\n" + fullResult;
        }

        // 阻塞执行
        String result = chatClient.prompt()
                .system(systemPrompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .user(taskCall.prompt())
                .call()
                .content();

        return "agent_id: " + sessionId + "\n\n" + result;
    }

    /**
     * 获取或创建子智能体的 ChatClient
     */
    private ChatClient getOrCreateChatClient(Agent agent, ModelTypeEnum model) {
        ChatClient.Builder builder = ChatClient.builder(agent.getModelFactory().model(model));
        List<AgentHook> hooks = agent.getHooks();
        if (!hooks.isEmpty()) {
            builder.defaultAdvisors(new HookAdvisor(hooks));
        }
        return builder.build();
    }

    /**
     * 解析子智能体使用的模型
     */
    private ModelTypeEnum resolveModel(Agent agent, Map<String, Object> context) {
        AgentDefinition definition = agent.getDefinition();
        if (definition.model() != null && !definition.model().isEmpty()) {
            try {
                return ModelTypeEnum.valueOf(definition.model().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("未知的模型类型: {}, 使用默认模型", definition.model());
            }
        }
        Object contextModel = context.get("model");
        if (contextModel instanceof ModelTypeEnum modelTypeEnum) {
            return modelTypeEnum;
        }
        return ModelTypeEnum.DEEPSEEK;
    }

    /**
     * 构建子智能体的系统提示词
     */
    private String buildSubagentSystemPrompt(Agent agent) {
        AgentDefinition definition = agent.getDefinition();
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
        private SessionManager sessionManager;
        private ToolUIBridge toolUIBridge;

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

        public Builder sessionManager(SessionManager sessionManager) {
            this.sessionManager = sessionManager;
            return this;
        }

        public Builder toolUIBridge(ToolUIBridge toolUIBridge) {
            this.toolUIBridge = toolUIBridge;
            return this;
        }

        public TaskTool build() {
            Assert.notNull(this.toolkit, "必须提供toolkit");
            Assert.notNull(this.modelFactory, "必须提供modelFactory");
            Assert.notNull(this.sessionManager, "必须提供sessionManager");

            // 扫描文件系统获取所有子智能体定义，构建描述
            List<AgentDefinition> subagentDefs = scanSubagentDefinitions();
            String subagentRegistrations = subagentDefs.stream()
                    .map(AgentDefinition::toRegistrationText)
                    .collect(Collectors.joining("\n"));

            String description = TASK_DESCRIPTION_TEMPLATE.formatted(subagentRegistrations);

            return new TaskTool(description, toolkit, modelFactory,
                    this.skillManager, this.taskRepository, this.sessionManager, this.toolUIBridge);
        }
    }
}
