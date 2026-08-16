package cn.bitloom.agentic.tool.taskboard;

import cn.bitloom.agentic.taskboard.TaskBoardRepository;
import cn.bitloom.agentic.taskboard.TaskRecord;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务清单工具 — 列出项目任务依赖图中的任务，标注每个 pending 任务的阻塞状态。
 */
@Slf4j
public class TaskListTool extends AbstractTool<TaskListTool.Input> {

    private static final String DESCRIPTION =
            "列出项目任务依赖图中的任务（跨会话持久），标注每个 pending 任务的阻塞状态（未完成的依赖）。";

    private final TaskBoardRepository repository;

    private TaskListTool(TaskBoardRepository repository) {
        super("TaskList", DESCRIPTION, Input.class);
        Assert.notNull(repository, "repository不能为null");
        this.repository = repository;
    }

    /** FunctionToolCallback 需要至少一个字段才能生成 JSON Schema */
    public record Input(
            @ToolParam(description = "按状态过滤：pending / in_progress / completed（可选，空为全部）",
                    required = false) String status
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        String projectDir = TaskCreateTool.extractString(toolContext, "projectPath");
        if (projectDir == null || projectDir.isBlank()) {
            return ToolResult.error("任务依赖图仅在 code 模式（有项目目录）下可用");
        }
        log.info("[ToolCall] TaskList - status={}", input.status());

        List<TaskRecord> tasks = repository.list(projectDir, input.status());
        if (tasks.isEmpty()) {
            return ToolResult.success(input.status() == null || input.status().isBlank()
                    ? "任务图为空（用 TaskCreate 创建任务）"
                    : "没有状态为 " + input.status() + " 的任务");
        }

        List<String> lines = new ArrayList<>();
        lines.add("任务清单（" + tasks.size() + " 个）：");
        for (TaskRecord task : tasks) {
            StringBuilder line = new StringBuilder("- " + task.getId() + " [" + task.getStatus() + "] "
                    + task.getSubject());
            if (task.getOwner() != null) {
                line.append("（owner: ").append(task.getOwner()).append("）");
            }
            if (task.isPending()) {
                List<String> incomplete = repository.incompleteDependencies(projectDir, task);
                line.append(incomplete.isEmpty() ? "（可认领）" : "（阻塞于: " + String.join(", ", incomplete) + "）");
            }
            lines.add(line.toString());
        }
        return ToolResult.success(String.join("\n", lines));
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

        public TaskListTool build() {
            return new TaskListTool(this.repository);
        }
    }
}
