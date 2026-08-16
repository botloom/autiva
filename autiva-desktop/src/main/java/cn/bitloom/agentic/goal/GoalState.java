package cn.bitloom.agentic.goal;

import java.time.Instant;

/**
 * 目标状态（对标 learn-claude-code s17 Goal Loop）。
 *
 * <p>目标三要素由一条自然语言描述承载：结束状态 + 验证方式 + 限制条件，
 * 如「pytest tests/auth 退出码为 0，且 lint 无错误」。
 *
 * <p>生命周期：active → achieved（判断器确认达成）
 *              → impossible（判断器确认无法达成）
 *              → blocked（连续阻止达到上限，停止自动续轮，保留目标等待用户）
 * dafault deferred 标记：后台任务仍在运行时不判断，等 task_notification 后恢复。
 */
public class GoalState {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_ACHIEVED = "achieved";
    public static final String STATUS_IMPOSSIBLE = "impossible";
    public static final String STATUS_BLOCKED = "blocked";

    private final String goal;
    private final Instant createdAt = Instant.now();

    private volatile String status = STATUS_ACTIVE;
    /** 判断器已执行次数 */
    private volatile int judgeCount = 0;
    /** 连续判定未通过的次数（出口保护计数） */
    private volatile int blockedCount = 0;
    /** 最近一次判定原因（未达成理由 / 失败原因） */
    private volatile String lastReason;
    /** defer 标记：后台任务运行中暂停判断，待 task_notification 后恢复续轮 */
    private volatile boolean deferred = false;

    public GoalState(String goal) {
        this.goal = goal;
    }

    public String getGoal() {
        return goal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getJudgeCount() {
        return judgeCount;
    }

    public void incrementJudgeCount() {
        this.judgeCount++;
    }

    public int getBlockedCount() {
        return blockedCount;
    }

    public void incrementBlockedCount() {
        this.blockedCount++;
    }

    public String getLastReason() {
        return lastReason;
    }

    public void setLastReason(String lastReason) {
        this.lastReason = lastReason;
    }

    public boolean isDeferred() {
        return deferred;
    }

    public void setDeferred(boolean deferred) {
        this.deferred = deferred;
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }
}
