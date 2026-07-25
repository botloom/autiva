package cn.bitloom.agentic.tool.task;

import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.session.InMemorySessionManager;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.session.SessionRespTypeEnum;
import cn.bitloom.agentic.session.SessionRunner;
import cn.bitloom.agentic.session.SessionTypeEnum;
import cn.bitloom.agentic.task.repository.TaskRepository;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.bridge.desktop.ToolUIBridge;
import cn.bitloom.exception.AgentException;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 启动新的代理来自主处理复杂的多步骤任务。
 * <p>
 * Task工具启动专门的代理（子进程），它们自主处理复杂任务。
 * 每种代理类型都有特定的能力和可用的工具。
 * <p>
 * 子 Session 机制：
 * - 每个子智能体任务创建一个子 Session（InMemorySessionManager 管理）
 * - 子智能体 Agent 由 InMemorySessionManager.activate() 内部 per-session 构建（不在 TaskTool 中创建）
 * - 子 Session 拥有独立的 InMemoryChatMemory，支持多轮对话上下文
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

    private final TaskRepository taskRepository;
    private final ToolUIBridge toolUIBridge;
    private final InMemorySessionManager inMemorySessionManager;

    private TaskTool(String description,
                     TaskRepository taskRepository,
                     ToolUIBridge toolUIBridge,
                     InMemorySessionManager inMemorySessionManager) {
        super("Task", description, Input.class);
        this.taskRepository = taskRepository;
        this.toolUIBridge = toolUIBridge;
        this.inMemorySessionManager = inMemorySessionManager;
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

        // 后台任务
        if (Boolean.TRUE.equals(taskCall.runInBackground())) {
            var bgTask = this.taskRepository.putTask(taskId, () -> {
                try {
                    String result = executeSubagent(taskCall, taskId);
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
            String result = executeSubagent(taskCall, taskId);
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
     * 执行子智能体任务（走 SessionRunner + EventBus 模式）。
     * <p>
     * 流程：
     * 1. 激活子 Session（幂等：已激活则跳过；内部按 sessionId 加锁 per-session 创建 Agent + SessionRunner）
     * 2. 通过 SessionRunner.getResultFuture() 拿到结果 future
     * 3. 通过 EventBus.publishIn 投递用户消息，触发 SessionRunner 处理
     * 4. 阻塞等待 future 完成（5 分钟超时）
     * <p>
     * Agent 构建由 InMemorySessionManager.activate() 内部完成（per-session InMemoryChatMemory + buildAgent），
     * TaskTool 不再直接创建 Agent。
     */
    private String executeSubagent(TaskCall taskCall, String taskId) {
        // 1. 激活子 Session（per-session 创建 Agent + 启动 SessionRunner 消息循环）
        inMemorySessionManager.activate(taskId);

        // 2. 拿到 SessionRunner 的 resultFuture（用于同步等待结果）
        SessionRunner runner = inMemorySessionManager.getRunner(taskId);
        if (runner == null) {
            throw AgentException.subagentExecutionFailed(taskCall.subagentName(),
                    new IllegalStateException("SessionRunner 未创建: " + taskId));
        }
        CompletableFuture<String> resultFuture = runner.getResultFuture();

        // 3. 发布用户消息到 inBox，触发 SessionRunner 处理
        EventBus.publishIn(MessageEvent.userMessage(taskId, taskCall.prompt()));

        // 4. 阻塞等待结果（5 分钟超时）
        try {
            String result = resultFuture.get(5, TimeUnit.MINUTES);
            return "agent_id: " + taskId + "\n\n" + result;
        } catch (TimeoutException e) {
            runner.stop();
            throw AgentException.subagentExecutionFailed(taskCall.subagentName(),
                    new IllegalStateException("子智能体执行超时（5 分钟）: " + taskId));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            runner.stop();
            throw AgentException.subagentExecutionFailed(taskCall.subagentName(), e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw AgentException.subagentExecutionFailed(taskCall.subagentName(), cause);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private TaskRepository taskRepository;
        private ToolUIBridge toolUIBridge;
        private InMemorySessionManager inMemorySessionManager;

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

        public Builder inMemorySessionManager(InMemorySessionManager inMemorySessionManager) {
            this.inMemorySessionManager = inMemorySessionManager;
            return this;
        }

        public TaskTool build() {
            Assert.notNull(this.inMemorySessionManager, "必须提供inMemorySessionManager");

            return new TaskTool(TASK_DESCRIPTION,
                    this.taskRepository, this.toolUIBridge,
                    this.inMemorySessionManager);
        }
    }
}
