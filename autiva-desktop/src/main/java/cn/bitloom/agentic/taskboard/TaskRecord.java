package cn.bitloom.agentic.taskboard;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 持久化任务记录 — 跨会话任务依赖图的节点（对标 learn-claude-code s10）。
 *
 * <p>与 TodoWrite 是两层并存：TodoWrite 是会话内轻量计划（内存级），
 * TaskRecord 是项目级持久任务图（依赖 + 认领），供多会话/多智能体协作。
 *
 * <p>状态机：{@code pending →(claim)→ in_progress →(complete)→ completed}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskRecord {

    /** 任务 ID："task_" + 8 位随机 hex */
    private String id;
    /** 任务主题（简短一行） */
    private String subject;
    /** 任务描述（详细） */
    private String description;
    /** pending | in_progress | completed */
    private String status;
    /** 认领者：sessionId / teammate 名 / null */
    private String owner;
    /** 依赖的 taskId 列表（全部 completed 后才可认领） */
    private List<String> blockedBy;
    /** 可选：绑定的 git worktree 路径（P2-2 Agent Teams 使用） */
    private String worktree;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED = "completed";

    /** 创建新任务（pending、无 owner、时间戳初始化） */
    public static TaskRecord newTask(String id, String subject, String description, List<String> blockedBy) {
        Instant now = Instant.now();
        return TaskRecord.builder()
                .id(id)
                .subject(subject)
                .description(description)
                .status(STATUS_PENDING)
                .owner(null)
                .blockedBy(blockedBy != null ? List.copyOf(blockedBy) : List.of())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public boolean isInProgress() {
        return STATUS_IN_PROGRESS.equals(status);
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }
}
