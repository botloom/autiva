package cn.bitloom.agentic.tool;

import cn.bitloom.agentic.task.TaskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskTool implements ITool {

    private final TaskManager taskManager;

    @Tool(name = "taskCreate", description = "创建一个任务")
    public ToolResult create(@ToolParam(description = "任务主题") String subject, @ToolParam(description = "任务描述") String description) {
        log.info("[ToolCall] taskCreate - 创建任务: subject={}, description={}", subject, description);
        try {
            String result = taskManager.create(subject, description);
            log.info("[ToolCall] taskCreate - 创建完成: {}", result);
            return ToolResult.success("创建任务成功", result);
        } catch (Exception e) {
            log.error("[ToolCall] taskCreate - 创建失败", e);
            return ToolResult.failure("创建任务失败: " + e.getMessage());
        }
    }

    @Tool(name = "taskUpdate", description = "更新任务的状态或依赖项")
    public ToolResult update(Long taskId, @ToolParam(description = "pending, in_progress, completed") String status, List<Long> addBlockedBy, List<Long> addBlocks) {
        log.info("[ToolCall] taskUpdate - 更新任务: taskId={}, status={}, addBlockedBy={}, addBlocks={}", taskId, status, addBlockedBy, addBlocks);
        try {
            String result = taskManager.update(taskId, status, addBlockedBy, addBlocks);
            log.info("[ToolCall] taskUpdate - 更新完成: {}", result);
            return ToolResult.success("更新任务成功", result);
        } catch (Exception e) {
            log.error("[ToolCall] taskUpdate - 更新失败", e);
            return ToolResult.failure("更新任务失败: " + e.getMessage());
        }
    }

    @Tool(name = "taskList", description = "列出所有带有状态摘要的任务")
    public ToolResult list() {
        log.info("[ToolCall] taskList - 列出所有任务");
        try {
            String result = taskManager.list();
            log.info("[ToolCall] taskList - 查询完成");
            return ToolResult.success("查询任务列表成功", result);
        } catch (Exception e) {
            log.error("[ToolCall] taskList - 查询失败", e);
            return ToolResult.failure("查询任务列表失败: " + e.getMessage());
        }
    }

    @Tool(name = "taskGet", description = "通过ID获取任务的全部详细信息")
    public ToolResult get(Long taskId) {
        log.info("[ToolCall] taskGet - 获取任务详情: taskId={}", taskId);
        try {
            String result = taskManager.getTask(taskId);
            log.info("[ToolCall] taskGet - 查询完成");
            return ToolResult.success("获取任务详情成功", result);
        } catch (Exception e) {
            log.error("[ToolCall] taskGet - 查询失败", e);
            return ToolResult.failure("获取任务详情失败: " + e.getMessage());
        }
    }

    @Tool(name = "scanUnclaimedTasks", description = "扫描所有未被认领的任务")
    public ToolResult scanUnclaimed() {
        log.info("[ToolCall] scanUnclaimedTasks - 扫描未认领任务");
        try {
            String result = taskManager.scanUnclaimed();
            log.info("[ToolCall] scanUnclaimedTasks - 扫描完成");
            return ToolResult.success("扫描未认领任务成功", result);
        } catch (Exception e) {
            log.error("[ToolCall] scanUnclaimedTasks - 扫描失败", e);
            return ToolResult.failure("扫描未认领任务失败: " + e.getMessage());
        }
    }

    @Tool(name = "claimTask", description = "认领一个任务")
    public ToolResult claim(Long taskId) {
        log.info("[ToolCall] claimTask - 认领任务: taskId={}", taskId);
        try {
            String result = taskManager.claim(taskId);
            log.info("[ToolCall] claimTask - 认领完成: {}", result);
            return ToolResult.success("认领任务成功", result);
        } catch (Exception e) {
            log.error("[ToolCall] claimTask - 认领失败", e);
            return ToolResult.failure("认领任务失败: " + e.getMessage());
        }
    }
}
