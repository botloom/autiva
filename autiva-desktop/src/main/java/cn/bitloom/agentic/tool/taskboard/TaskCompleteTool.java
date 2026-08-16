package cn.bitloom.agentic.tool.taskboard;

import cn.bitloom.agentic.taskboard.TaskBoardRepository;
import cn.bitloom.agentic.taskboard.TaskRecord;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.util.List;

/**
 * 任务完成工具 — 置任务 completed 并输出"刚解锁"的下游 pending 任务提示。
 */
@Slf4j
public class TaskCompleteTool extends AbstractTool<TaskCompleteTool.Input> {

    private static final String DESCRIPTION =
            "完成任务依赖图中的任务（置 completed）。完成后自动扫描依赖图，"
                    + "输出因此刚解除阻塞、现在可认领的下游任务（Unlocked 提示）。";

    private final TaskBoardRepository repository;

    private TaskCompleteTool(TaskBoardRepository repository) {
        super("TaskComplete", DESCRIPTION, Input.class);
        Assert.notNull(repository, "repository不能为null");
        this.repository = repository;
    }

    public record Input(
            @ToolParam(description = "任务 ID") String taskId
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        String projectDir = TaskCreateTool.extractString(toolContext, "projectPath");
        if (projectDir == null || projectDir.isBlank()) {
            return ToolResult.error("任务依赖图仅在 code 模式（有项目目录）下可用");
        }
        log.info("[ToolCall] TaskComplete - taskId={}", input.taskId());

        TaskBoardRepository.CompleteResult result = repository.complete(projectDir, input.taskId());
        if (!result.success()) {
            return ToolResult.error("完成失败: " + result.message());
        }

        StringBuilder sb = new StringBuilder(result.message() + ": " + input.taskId());
        List<TaskRecord> unlocked = result.unlocked();
        if (!unlocked.isEmpty()) {
            sb.append("\nUnlocked（刚解除阻塞、可认领的任务）:");
            for (TaskRecord task : unlocked) {
                sb.append("\nUnlocked: ").append(task.getId()).append("（").append(task.getSubject()).append("）");
            }
        }
        return ToolResult.success(sb.toString());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private TaskBoardRepository repository;

        private Builder() {}

        public Builder repository(TaskBoardRepository repository) {
            this.repository = repository;
            return this;
        }

        public TaskCompleteTool build() {
            return new TaskCompleteTool(this.repository);
        }
    }
}
