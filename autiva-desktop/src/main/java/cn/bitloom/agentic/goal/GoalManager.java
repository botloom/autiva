package cn.bitloom.agentic.goal;

import cn.bitloom.agentic.tool.task.repository.TaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * 目标状态管理器（对标 learn-claude-code s17 Goal Loop）。
 *
 * <p>per-session 目标容器：GoalSetTool（模型设置目标）与 GoalJudgeHook（回合结束判定）
 * 通过本类共享状态；自动续轮回调（continuation）由 ViewModel 注册，
 * GoalJudgeHook / TaskTool 后台任务通知通过 {@link #continueRound} 触发下一次 runStream。
 *
 * <p>defer 语义：{@link #hasBackgroundWork()} 检查 TaskRepository 中是否有运行中的
 * 后台任务（TaskTool run_in_background），运行中时不判定目标（等 task_notification
 * 注入后由 {@link #resumeIfDeferred} 恢复续轮）。
 */
@Slf4j
@Component
public class GoalManager {

    /** 自动续轮回调列表（coder/work 两个 ViewModel 各注册一个，按 sessionId 自行路由） */
    private final List<BiConsumer<String, String>> continuations = new CopyOnWriteArrayList<>();

    private final Map<String, GoalState> goals = new ConcurrentHashMap<>();

    private final TaskRepository taskRepository;

    public GoalManager(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /** 设置/覆盖目标（重置计数），仅主智能体 session */
    public GoalState setGoal(String sessionId, String goal) {
        GoalState state = new GoalState(goal);
        goals.put(sessionId, state);
        log.info("[Goal] 设置目标: session={}, goal={}", sessionId, goal);
        return state;
    }

    public Optional<GoalState> getGoal(String sessionId) {
        return Optional.ofNullable(goals.get(sessionId));
    }

    public void clearGoal(String sessionId) {
        goals.remove(sessionId);
    }

    /** 注册自动续轮回调：(sessionId, message) -> 发起下一次 runStream */
    public void registerContinuation(BiConsumer<String, String> continuation) {
        continuations.add(continuation);
    }

    /**
     * 触发自动续轮：以 message 为输入对 sessionId 发起下一次 runStream。
     *
     * @return 是否有回调接受处理
     */
    public boolean continueRound(String sessionId, String message) {
        boolean handled = false;
        for (BiConsumer<String, String> continuation : continuations) {
            try {
                continuation.accept(sessionId, message);
                handled = true;
            } catch (Exception e) {
                log.warn("[Goal] 续轮回调执行失败: session={}: {}", sessionId, e.getMessage());
            }
        }
        return handled;
    }

    /** 后台任务（TaskTool run_in_background）是否有运行中的 */
    public boolean hasBackgroundWork() {
        return taskRepository.hasRunningTasks();
    }

    /**
     * defer 恢复入口：后台任务通知注入后调用。
     * 目标仍为 active 且 deferred 时，以续行消息恢复自动续轮。
     *
     * @return 是否触发了续轮
     */
    public boolean resumeIfDeferred(String sessionId, String message) {
        GoalState state = goals.get(sessionId);
        if (state == null || !state.isActive() || !state.isDeferred()) {
            return false;
        }
        state.setDeferred(false);
        log.info("[Goal] 后台任务完成，恢复目标续轮: session={}", sessionId);
        return continueRound(sessionId, message);
    }
}
