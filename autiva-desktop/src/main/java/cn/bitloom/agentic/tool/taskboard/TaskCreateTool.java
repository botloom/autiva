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
 * 任务创建工具 — 在项目任务依赖图上创建 pending 任务（跨会话持久）。
 *
 * <p>与 TodoWrite 的分工：TodoWrite 是会话内轻量计划；本工具创建的任务
 * 落盘在 {@code {project}/.autiva/tasks/}，跨会话存在、可声明依赖、可被原子认领。
 */
@Slf4j
public class TaskCreateTool extends AbstractTool<TaskCreateTool.Input> {

    private static final String DESCRIPTION =
            "在项目任务依赖图上创建任务（跨会话持久化）。可声明 blockedBy 依赖其它任务，"
                    + "依赖全部 completed 后任务才可被认领。适用于多步骤、需跨会话跟踪的工作。"
                    + "会话内轻量计划请继续用 TodoWrite。";

    private final TaskBoardRepository repository;

    private TaskCreateTool(TaskBoardRepository repository) {
        super("TaskCreate", DESCRIPTION, Input.class);
        Assert.notNull(repository, "repository不能为null");
        this.repository = repository;
    }

    public record Input(
            @ToolParam(description = "任务主题（简短一行）") String subject,
            @ToolParam(description = "任务详细描述") String description,
            @ToolParam(description = "依赖的任务 ID 列表（可选，全部完成后本任务才可认领）",
                    required = false) List<String> blockedBy
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        String projectDir = extractString(toolContext, "projectPath");
        if (projectDir == null || projectDir.isBlank()) {
            return ToolResult.error("任务依赖图仅在 code 模式（有项目目录）下可用");
        }
        log.info("[ToolCall] TaskCreate - subject={}, blockedBy={}", input.subject(), input.blockedBy());

        // 依赖存在性校验（不存在直接报错，防止拼错 ID 造出永不解锁的任务）
        List<String> blockedBy = input.blockedBy() != null ? input.blockedBy() : List.of();
        for (String depId : blockedBy) {
            if (repository.load(projectDir, depId).isEmpty()) {
                return ToolResult.error("依赖任务不存在: " + depId);
            }
        }

        String id = repository.newTaskId(projectDir);
        TaskRecord task = repository.create(projectDir, input.subject(), input.description(), blockedBy);
        String message = "已创建任务 " + id + ": " + input.subject();
        if (!blockedBy.isEmpty()) {
            message += "（阻塞于: " + String.join(", ", blockedBy) + "）";
        }
        return ToolResult.success(message);
    }

    static String extractString(ToolContext context, String key) {
        if (context == null || context.getContext() == null) {
            return null;
        }
        Object value = context.getContext().get(key);
        return value instanceof String s ? s : null;
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

        public TaskCreateTool build() {
            return new TaskCreateTool(this.repository);
        }
    }
}
