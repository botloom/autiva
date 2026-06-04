package cn.bitloom.agentic.tool.core;

import cn.bitloom.agentic.agent.subagent.*;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.session.SessionManager;
import cn.bitloom.agentic.task.repository.TaskRepository;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.exception.AgentException;
import cn.bitloom.node.TaskCard;
import cn.bitloom.store.ToolUIBridge;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Slf4j
public class TaskTool {

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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final List<SubagentType> subagentTypes = new ArrayList<>();

        private final List<SubagentReference> subagentReferences = new ArrayList<>();

        private TaskRepository taskRepository;

        private SessionManager sessionManager;

        private ToolUIBridge toolUIBridge;

        private Builder() {
        }

        public Builder taskRepository(TaskRepository taskRepository) {
            this.taskRepository = taskRepository;
            return this;
        }

        public Builder subagentTypes(List<SubagentType> subagentTypes) {
            this.subagentTypes.addAll(subagentTypes);
            return this;
        }

        public Builder subagentTypes(SubagentType... subagentTypes) {
            this.subagentTypes.addAll(List.of(subagentTypes));
            return this;
        }

        public Builder subagentReferences(List<SubagentReference> subagentReferences) {
            this.subagentReferences.addAll(subagentReferences);
            return this;
        }

        public Builder subagentReferences(SubagentReference... subagentReference) {
            this.subagentReferences.addAll(List.of(subagentReference));
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

        private SubagentDefinition resolve(SubagentReference subagentReference) {
            for (SubagentResolver subagentResolver : this.subagentTypes.stream().map(SubagentType::resolver).toList()) {
                if (subagentResolver.canResolve(subagentReference)) {
                    return subagentResolver.resolve(subagentReference);
                }
            }
            throw AgentException.subagentResolverNotFound(subagentReference.toString());
        }

        public ToolCallback build() {
            List<SubagentDefinition> subagentDefinitions = this.subagentReferences.stream()
                    .map(this::resolve)
                    .toList();

            String subagentRegistrations = subagentDefinitions.stream()
                    .map(SubagentDefinition::toSubagentRegistrations)
                    .collect(Collectors.joining("\n"));

            List<SubagentExecutor> executors = this.subagentTypes.stream().map(SubagentType::executor).toList();

            return FunctionToolCallback
                    .builder("Task", new TaskFunction(subagentDefinitions, executors, this.taskRepository, this.sessionManager, this.toolUIBridge))
                    .description(TASK_DESCRIPTION_TEMPLATE.formatted(subagentRegistrations))
                    .inputType(TaskCall.class)
                    .build();
        }

    }

    public static class TaskFunction implements BiFunction<TaskCall, ToolContext, String> {

        private final TaskRepository taskRepository;
        private final SessionManager sessionManager;
        private final ToolUIBridge toolUIBridge;
        private final Map<String, SubagentDefinition> subagents;
        private final Map<String, SubagentExecutor> subagentExecutors;

        public TaskFunction(List<SubagentDefinition> subagents, List<SubagentExecutor> subagentExecutors, TaskRepository taskRepository, SessionManager sessionManager, ToolUIBridge toolUIBridge) {
            this.taskRepository = taskRepository;
            this.subagents = subagents.stream().collect(Collectors.toMap(SubagentDefinition::getName, sa -> sa));
            this.subagentExecutors = subagentExecutors.stream().collect(Collectors.toMap(SubagentExecutor::getKind, se -> se));
            this.sessionManager = sessionManager;
            this.toolUIBridge = toolUIBridge;
        }

        @Override
        public String apply(TaskCall taskCall, ToolContext toolContext) {

            String subagentName = taskCall.subagentName();

            if (!this.subagents.containsKey(subagentName)) {
                throw AgentException.subagentNotFound(subagentName);
            }
            SubagentDefinition subagent = this.subagents.get(subagentName);
            SubagentExecutor subagentExecutor = this.subagentExecutors.get(subagent.getKind());
            if (subagentExecutor == null) {
                throw AgentException.subagentExecutorNotFound(subagent.getKind());
            }

            Session childSession;
            if (Objects.nonNull(taskCall.resume())) {
                childSession = this.sessionManager.getById(taskCall.resume());
            } else {
                childSession = this.sessionManager.forkSession((String) toolContext.getContext().get("sessionId"), subagentName);
            }

            if (this.toolUIBridge != null) {
                JSONObject taskJson = new JSONObject();
                taskJson.put("subagentName", subagentName);
                taskJson.put("description", taskCall.description());
                taskJson.put("taskId", childSession.getId());
                TaskCard taskCard = this.toolUIBridge.createTaskCard(childSession.getId(), taskJson.toJSONString());
                if (taskCard != null) {
                    taskCard.subscribeSession(childSession.getId());
                }
            }

            Map<String, Object> execContext = Map.of("sessionId", childSession.getId(), "model", toolContext.getContext().get("model"));

            if (Boolean.TRUE.equals(taskCall.runInBackground())) {
                var bgTask = this.taskRepository.putTask(childSession.getId(),
                        () -> {
                            try {
                                String result = subagentExecutor.execute(taskCall, execContext, subagent, chunk -> {
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
                                bgTask.getTaskId(), bgTask.getTaskId(), bgTask.getTaskId())).toJson();
            }

            try {
                String result = subagentExecutor.execute(taskCall, execContext, subagent, chunk -> {
                    if (this.toolUIBridge != null) {
                        this.toolUIBridge.appendTaskOutput(childSession.getId(), chunk);
                    }
                });
                if (this.toolUIBridge != null) {
                    this.toolUIBridge.completeTaskCard(childSession.getId(), null);
                }
                return ToolResult.success("任务已完成", Map.of("subagentName", subagentName), result).toJson();
            } catch (Exception e) {
                if (this.toolUIBridge != null) {
                    this.toolUIBridge.failTaskCard(childSession.getId(), e.getMessage());
                }
                throw AgentException.subagentExecutionFailed(subagentName, e);
            }
        }

    }

}
