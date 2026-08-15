package cn.bitloom.agentic.tool.task;

import cn.bitloom.agentic.tool.task.repository.BackgroundTask;
import cn.bitloom.agentic.tool.task.repository.TaskRepository;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.util.Map;

/**
 * 用于从正在运行或已完成的后台任务获取输出的工具。支持阻塞和非阻塞模式，可配置超时。
 *
 */
public class TaskOutputTool extends AbstractTool<TaskOutputTool.Input> {

    private static final String DESCRIPTION = """
            获取后台任务的输出。block=true(默认)等待完成，block=false 非阻塞检查。
            """;

    /**
     * 输入参数 record
     */
    public record Input(
            @ToolParam(description = "要获取输出的任务ID") String task_id,
            @ToolParam(description = "是否等待完成", required = false) Boolean block,
            @ToolParam(description = "最大等待时间（毫秒）", required = false) Long timeout) {
    }

    private final TaskRepository taskRepository;

    private TaskOutputTool(TaskRepository taskRepository) {
        super("TaskOutput", DESCRIPTION, Input.class);
        this.taskRepository = taskRepository;
    }

    @Override
    public @NonNull ToolResult execute(Input input, ToolContext context) {
        BackgroundTask bgTask = taskRepository.getTasks(input.task_id());

        if (bgTask == null) {
            return ToolResult.error("未找到ID为 " + input.task_id() + " 的后台任务");
        }

        boolean shouldBlock = input.block() == null || input.block();
        long timeoutMs = input.timeout() != null ? Math.min(input.timeout(), 600000) : 30000;
        if (shouldBlock && !bgTask.isCompleted()) {
            try {
                bgTask.waitForCompletion(timeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ToolResult.error("等待任务被中断");
            }
        }

        StringBuilder result = new StringBuilder();
        result.append("任务ID: ").append(input.task_id()).append("\n");
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

        return ToolResult.success("任务输出", Map.of("task_id", input.task_id(), "status", bgTask.getStatus()), result.toString());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private TaskRepository taskRepository;

        private Builder() {
        }

        public Builder taskRepository(TaskRepository taskRepository) {
            Assert.notNull(taskRepository, "taskRepository不能为null");
            this.taskRepository = taskRepository;
            return this;
        }

        public TaskOutputTool build() {
            Assert.notNull(this.taskRepository, "必须提供taskRepository");
            return new TaskOutputTool(this.taskRepository);
        }

    }

}
