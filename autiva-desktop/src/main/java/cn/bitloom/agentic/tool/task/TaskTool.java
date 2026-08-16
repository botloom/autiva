package cn.bitloom.agentic.tool.task;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.tool.task.repository.TaskRepository;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.bridge.desktop.ToolUIBridge;
import cn.bitloom.exception.AgentException;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import javafx.application.Platform;
import java.util.HashMap;
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
    private final cn.bitloom.agentic.agent.SubAgentFactory subAgentFactory;
    private final cn.bitloom.agentic.goal.GoalManager goalManager;

    private TaskTool(String description,
                     TaskRepository taskRepository,
                     ToolUIBridge toolUIBridge,
                     FileSystemSessionManager sessionManager,
                     cn.bitloom.agentic.agent.SubAgentFactory subAgentFactory,
                     cn.bitloom.agentic.goal.GoalManager goalManager) {
        super("Task", description, Input.class);
        this.taskRepository = taskRepository;
        this.toolUIBridge = toolUIBridge;
        this.sessionManager = sessionManager;
        this.subAgentFactory = subAgentFactory;
        this.goalManager = goalManager;
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
        String projectPath = resolveProjectPath(context);
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
                    String result = executeSubagent(taskCall, parentSession, branch, projectPath);
                    if (this.toolUIBridge != null) {
                        this.toolUIBridge.completeTaskCard(taskId, null);
                    }
                    notifyBackgroundCompletion(parentSession.id(), taskId, taskCall.description(), result, null);
                    return result;
                } catch (Exception e) {
                    if (this.toolUIBridge != null) {
                        this.toolUIBridge.failTaskCard(taskId, e.getMessage());
                    }
                    notifyBackgroundCompletion(parentSession.id(), taskId, taskCall.description(), null,
                            e.getMessage());
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
            String result = executeSubagent(taskCall, parentSession, branch, projectPath);
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
     * 后台任务完成/失败时向父 session 追加 synthetic 通知事件（push 模型，对标 s11 通知收集）。
     * <p>
     * branch=null（root 可见），SessionMemoryAdvisor 下一轮加载时注入主智能体上下文并标记
     * consumed（一次性消费，防止每轮重复注入）；TaskOutput 工具保留——主动查询与被动通知双通道并存。
     * 不复用原 tool_use_id（一个 tool_use 只对应一个 tool_result 的 API 约束）。
     */
    private void notifyBackgroundCompletion(String parentSessionId, String taskId, String description,
            String result, String error) {
        try {
            String status = error != null ? "失败" : "完成";
            String summary = error != null
                    ? "失败原因：" + truncate(error, 500)
                    : "结果摘要：" + truncate(result != null ? result : "", 500);
            String text = "<task_notification>\n后台任务 " + taskId + "（" + description + "）已" + status + "。\n"
                    + summary + "\n完整结果可通过 TaskOutput 工具获取。\n</task_notification>";

            Map<String, Object> metadata = new HashMap<>();
            metadata.put(MessageEvent.METADATA_SYNTHETIC, Boolean.TRUE);
            metadata.put(MessageEvent.METADATA_NOTIFICATION, Boolean.TRUE);
            MessageEvent notification = MessageEvent.builder()
                    .sessionId(parentSessionId)
                    .message(new UserMessage(text))
                    .metadata(metadata)
                    .build();
            sessionManager.appendEvent(notification);

            // UI：系统通知卡片（区别于用户消息样式）
            if (this.toolUIBridge != null) {
                this.toolUIBridge.showNotification("后台任务 " + taskId + "（" + description + "）已" + status,
                        parentSessionId);
            }

            // Goal Loop defer 恢复：目标 deferred 时后台任务完成即自动续轮推进目标
            if (this.goalManager != null) {
                this.goalManager.resumeIfDeferred(parentSessionId,
                        "<task_notification>\n后台任务 " + taskId + "（" + description + "）已" + status
                                + "，完整结果已注入上下文。请继续推进既定目标。\n</task_notification>");
            }
        } catch (Exception e) {
            log.warn("写入后台任务通知失败: taskId={}", taskId, e);
        }
    }

    private String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "...";
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
     * 从 ToolContext 解析项目路径
     */
    private String resolveProjectPath(ToolContext context) {
        if (context != null) {
            Object projectPath = context.getContext().get("projectPath");
            if (projectPath instanceof String path) {
                return path;
            }
        }
        return null;
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
    private String executeSubagent(TaskCall taskCall, Session parentSession, String branch, String projectPath) {
        String parentSessionId = parentSession.id();
        String taskId = branch;
        MessageEvent inputEvent = MessageEvent.userMessage(parentSessionId, taskCall.prompt());

        Agent agent = subAgentFactory.build(parentSession, taskCall.subagentName(), branch, projectPath, null, null);
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(parentSessionId)
                .userId(parentSession.userId())
                .branch(branch)
                .projectPath(projectPath)
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
        return "agent_id: " + taskId + "\n\n" + result;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private TaskRepository taskRepository;
        private ToolUIBridge toolUIBridge;
        private FileSystemSessionManager sessionManager;
        private cn.bitloom.agentic.agent.SubAgentFactory subAgentFactory;
        private cn.bitloom.agentic.goal.GoalManager goalManager;

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

        public Builder subAgentFactory(cn.bitloom.agentic.agent.SubAgentFactory subAgentFactory) {
            this.subAgentFactory = subAgentFactory;
            return this;
        }

        public Builder goalManager(cn.bitloom.agentic.goal.GoalManager goalManager) {
            this.goalManager = goalManager;
            return this;
        }

        public TaskTool build() {
            Assert.notNull(this.sessionManager, "必须提供sessionManager");
            Assert.notNull(this.subAgentFactory, "必须提供subAgentFactory");

            return new TaskTool(TASK_DESCRIPTION,
                    this.taskRepository, this.toolUIBridge,
                    this.sessionManager, this.subAgentFactory,
                    this.goalManager);
        }
    }
}
