package cn.bitloom.agentic.tool.taskboard;

import cn.bitloom.agentic.taskboard.TaskBoardRepository;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

/**
 * 任务认领工具 — 原子认领 pending 任务（锁内二次校验，多智能体并发安全）。
 */
@Slf4j
public class TaskClaimTool extends AbstractTool<TaskClaimTool.Input> {

    private static final String DESCRIPTION =
            "认领任务依赖图中的任务：原子置为 in_progress 并登记 owner（当前会话）。"
                    + "仅 pending、无 owner、依赖全部 completed 的任务可认领。"
                    + "认领后请执行任务并用 TaskComplete 完成。";

    private final TaskBoardRepository repository;

    private TaskClaimTool(TaskBoardRepository repository) {
        super("TaskClaim", DESCRIPTION, Input.class);
        Assert.notNull(repository, "repository不能为null");
        this.repository = repository;
    }

    public record Input(
            @ToolParam(description = "任务 ID") String taskId,
            @ToolParam(description = "认领者标识（可选；队友认领时传自己的队友名，默认为当前会话）", required = false) String owner
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        String projectDir = TaskCreateTool.extractString(toolContext, "projectPath");
        if (projectDir == null || projectDir.isBlank()) {
            return ToolResult.error("任务依赖图仅在 code 模式（有项目目录）下可用");
        }
        String sessionId = TaskCreateTool.extractString(toolContext, "sessionId");
        String owner = input.owner() != null && !input.owner().isBlank() ? input.owner()
                : sessionId != null ? sessionId : "unknown";
        log.info("[ToolCall] TaskClaim - taskId={}, owner={}", input.taskId(), owner);

        TaskBoardRepository.ClaimResult result = repository.claim(projectDir, input.taskId(), owner);
        if (!result.success()) {
            return ToolResult.error("认领失败: " + result.message());
        }
        String message = result.message() + ": " + result.task().getSubject();
        if (result.task().getWorktree() != null) {
            message += "（worktree: " + result.task().getWorktree() + "）";
        }
        return ToolResult.success(message);
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

        public TaskClaimTool build() {
            return new TaskClaimTool(this.repository);
        }
    }
}
