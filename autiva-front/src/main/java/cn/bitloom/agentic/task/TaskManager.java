package cn.bitloom.agentic.task;

import cn.bitloom.agentic.agent.subagent.*;
import cn.bitloom.agentic.agent.subagent.code.CodeSubagentDefinition;
import cn.bitloom.agentic.task.repository.BackgroundTask;
import cn.bitloom.agentic.task.repository.DefaultTaskRepository;
import cn.bitloom.agentic.task.repository.TaskRepository;
import cn.bitloom.constant.AppConstants;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class TaskManager {

    private static final String TASK_DESCRIPTION_TEMPLATE = """
            启动新的代理来自主处理复杂的多步骤任务。
            
            Task工具启动专门的代理（子进程），它们自主处理复杂任务。每种代理类型都有特定的能力和可用的工具。
            
            可用的代理类型及其能力：
            %s
            
            使用Task工具时，必须指定subagent_type参数来选择使用哪种代理类型。
            
            如何选择子智能体：
            - 编写/修改代码、创建文件、修复bug → 使用 Code 子智能体
            - 搜索代码、探索代码库结构 → 使用 Explore 子智能体
            - 制定实现计划、设计架构 → 使用 Plan 子智能体
            - 执行Shell命令、构建、部署 → 使用 Bash 子智能体
            - 复杂多步骤研究任务 → 使用 General Purpose 子智能体
            
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
            user: "请编写一个检查数字是否为质数的函数"
            assistant: 好的，我将使用 Code 子智能体来编写这个函数
            assistant: 使用Task工具，subagent_type="Code"，prompt="编写一个检查数字是否为质数的函数"
            </example>
            
            <example>
            user: "帮我找到处理用户认证的代码"
            assistant: 我将使用 Explore 子智能体来搜索相关代码
            assistant: 使用Task工具，subagent_type="Explore"，prompt="搜索处理用户认证的代码，包括认证中间件、登录控制器等"
            </example>
            
            <example>
            user: "运行构建并修复任何类型错误"
            assistant: 我将先使用 Bash 子智能体运行构建，然后根据结果使用 Code 子智能体修复错误
            assistant: 使用Task工具，subagent_type="Bash"，prompt="运行构建命令"
            </example>
            """;

    private static final String TASK_OUTPUT_DESCRIPTION_TEMPLATE = """
            - 从正在运行或已完成的任务（后台代理）获取输出
            - 接受一个标识任务的task_id参数
            - 返回任务输出及状态信息
            - 使用block=true（默认）等待任务完成
            - 使用block=false进行非阻塞的当前状态检查
            - 任务ID可以通过/tasks命令找到
            """;

    private final TaskRepository taskRepository = new DefaultTaskRepository();

    private final List<SubagentType> subagentTypes = new ArrayList<>();

    private final List<SubagentReference> subagentReferences = new ArrayList<>();

    private Map<String, SubagentDefinition> subagentDefinitions;

    private Map<String, SubagentExecutor> subagentExecutors;

    @PostConstruct
    public void init() {
        this.subagentDefinitions = Map.of();
        this.subagentExecutors = Map.of();
    }

    public void registerSubagentTypes(List<SubagentType> types) {
        Assert.notNull(types, "subagentTypes不能为null");
        this.subagentTypes.addAll(types);
        resolveAndIndex();
    }

    public void registerSubagentTypes(SubagentType... types) {
        Assert.notNull(types, "subagentTypes不能为null");
        this.subagentTypes.addAll(List.of(types));
        resolveAndIndex();
    }

    public void registerSubagentReferences(List<SubagentReference> references) {
        Assert.notNull(references, "subagentReferences不能为null");
        this.subagentReferences.addAll(references);
        resolveAndIndex();
    }

    public void registerSubagentReferences(SubagentReference... references) {
        Assert.notNull(references, "subagentReferences不能为null");
        this.subagentReferences.addAll(List.of(references));
        resolveAndIndex();
    }

    private void resolveAndIndex() {
        if (this.subagentTypes.stream().anyMatch(st -> st.kind().equals(CodeSubagentDefinition.KIND))) {
            loadWorkspaceSubagentReferences();
        }

        List<SubagentDefinition> definitions = this.subagentReferences.stream()
                .map(this::resolve)
                .toList();

        this.subagentDefinitions = definitions.stream()
                .collect(Collectors.toMap(SubagentDefinition::getName, sa -> sa));
        this.subagentExecutors = this.subagentTypes.stream()
                .map(SubagentType::executor)
                .collect(Collectors.toMap(SubagentExecutor::getKind, se -> se));
    }

    private void loadWorkspaceSubagentReferences() {
        Path subagentDir = AppConstants.Base.SUBAGENT_DIR;
        if (!Files.exists(subagentDir) || !Files.isDirectory(subagentDir)) {
            log.warn("子智能体工作目录不存在: {}", subagentDir);
            return;
        }

        try (Stream<Path> paths = Files.list(subagentDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .forEach(p -> {
                        String uri = p.toUri().toString();
                        if (this.subagentReferences.stream().noneMatch(r -> r.uri().equals(uri))) {
                            this.subagentReferences.add(new SubagentReference(uri, CodeSubagentDefinition.KIND));
                        }
                    });
        } catch (IOException e) {
            log.error("从工作目录加载子智能体引用失败", e);
        }
    }

    private SubagentDefinition resolve(SubagentReference subagentReference) {
        for (SubagentResolver subagentResolver : this.subagentTypes.stream().map(SubagentType::resolver).toList()) {
            if (subagentResolver.canResolve(subagentReference)) {
                return subagentResolver.resolve(subagentReference);
            }
        }
        throw new RuntimeException("未找到能够解析子代理引用的SubagentResolver: " + subagentReference);
    }

    public String executeTask(TaskCall taskCall) {
        String subagentName = taskCall.subagent_type();

        if (!this.subagentDefinitions.containsKey(subagentName)) {
            throw new RuntimeException("未找到名为 " + subagentName + " 的子代理");
        }

        SubagentDefinition subagent = this.subagentDefinitions.get(subagentName);
        SubagentExecutor subagentExecutor = this.subagentExecutors.get(subagent.getKind());

        if (subagentExecutor == null) {
            throw new RuntimeException("未找到子代理类型 " + subagent.getKind() + " 的执行器");
        }

        if (Boolean.TRUE.equals(taskCall.run_in_background())) {
            var bgTask = this.taskRepository.putTask("task_" + UUID.randomUUID(),
                    () -> subagentExecutor.execute(taskCall, subagent));

            return String.format(
                    "task_id: %s\n\n后台任务已启动，ID: %s\n使用TaskOutput工具并传入task_id='%s'来获取结果。",
                    bgTask.getTaskId(), bgTask.getTaskId(), bgTask.getTaskId());
        }

        return subagentExecutor.execute(taskCall, subagent);
    }

    public String getTaskOutput(String taskId, Boolean block, Long timeout) {
        BackgroundTask bgTask = this.taskRepository.getTasks(taskId);

        if (bgTask == null) {
            return "错误：未找到ID为 " + taskId + " 的后台任务";
        }

        boolean shouldBlock = block == null || block;
        long timeoutMs = timeout != null ? Math.min(timeout, 600000) : 30000;
        if (shouldBlock && !bgTask.isCompleted()) {
            try {
                bgTask.waitForCompletion(timeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "错误：等待任务被中断";
            }
        }

        StringBuilder result = new StringBuilder();
        result.append("任务ID: ").append(taskId).append("\n");
        result.append("状态: ").append(bgTask.getStatus()).append("\n\n");

        if (bgTask.isCompleted() && bgTask.getResult() != null) {
            result.append("结果:\n").append(bgTask.getResult());
        } else if (bgTask.getError() != null) {
            result.append("错误:\n").append(bgTask.getError().getMessage());
            if (bgTask.getError().getCause() != null) {
                result.append("\n原因: ").append(bgTask.getError().getCause().getMessage());
            }
        } else if (!bgTask.isCompleted()) {
            result.append("任务仍在运行...");
        }

        return result.toString();
    }

    public ToolCallback buildTaskToolCallback() {
        Assert.notEmpty(this.subagentTypes, "必须至少注册一个subagentType");

        String subagentRegistrations = this.subagentDefinitions.values().stream()
                .map(SubagentDefinition::toSubagentRegistrations)
                .collect(Collectors.joining("\n"));

        return FunctionToolCallback
                .builder("Task", new TaskFunction(this))
                .description(TASK_DESCRIPTION_TEMPLATE.formatted(subagentRegistrations))
                .inputType(TaskCall.class)
                .build();
    }

    public ToolCallback buildTaskOutputToolCallback() {
        return FunctionToolCallback.builder("TaskOutput", new TaskOutputFunction(this))
                .description(TASK_OUTPUT_DESCRIPTION_TEMPLATE)
                .inputType(TaskOutputCall.class)
                .build();
    }

    public List<ToolCallback> buildToolCallbacks() {
        return List.of(buildTaskToolCallback(), buildTaskOutputToolCallback());
    }

    public static class TaskFunction implements Function<TaskCall, String> {

        private final TaskManager taskManager;

        public TaskFunction(TaskManager taskManager) {
            this.taskManager = taskManager;
        }

        @Override
        public String apply(TaskCall taskCall) {
            return this.taskManager.executeTask(taskCall);
        }

    }

    public record TaskOutputCall(
            @ToolParam(description = "要获取输出的任务ID") String task_id,
            @ToolParam(description = "是否等待完成", required = false) Boolean block,
            @ToolParam(description = "最大等待时间（毫秒）", required = false) Long timeout) {
    }

    public static class TaskOutputFunction implements Function<TaskOutputCall, String> {

        private final TaskManager taskManager;

        public TaskOutputFunction(TaskManager taskManager) {
            this.taskManager = taskManager;
        }

        @Override
        public String apply(TaskOutputCall taskOutputCall) {
            return this.taskManager.getTaskOutput(taskOutputCall.task_id(), taskOutputCall.block(), taskOutputCall.timeout());
        }

    }

}
