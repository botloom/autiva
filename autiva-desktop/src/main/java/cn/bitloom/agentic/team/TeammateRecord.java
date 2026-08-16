package cn.bitloom.agentic.team;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 队友记录 — 长生命周期子智能体（对标 learn-claude-code s13 Agent Teams）。
 *
 * <p>与 TaskTool 一次性委派的本质区别：Task = 上下文隔离用后即弃，
 * Teammate = 专属 branch {@code teammate.{name}} 持久上下文，跨任务保留事件，双向协作。
 *
 * <p>状态机：{@code spawned → work → idle →（消息/任务唤醒）→ work → … → shutdown}。
 *
 * <p>{@code workVersion} 用于类型化协议（防伪造回复）：每次 work→idle 转换 +1，
 * 关机等控制请求携带的版本不匹配时拒绝。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeammateRecord {

    public static final String STATUS_SPAWNED = "spawned";
    public static final String STATUS_WORK = "work";
    public static final String STATUS_IDLE = "idle";
    public static final String STATUS_SHUTDOWN = "shutdown";

    /** 队友名（唯一，仅允许字母数字下划线连字符，防 branch 注入） */
    private String name;
    /** 职责描述（Lead spawn 时给出，注入队友系统提示） */
    private String description;
    /** 底层 AgentDefinition 名（General/Explore/review/plan…） */
    private String definition;
    /** spawned | work | idle | shutdown */
    private String status;
    /** spawn 时捕获的 projectPath（任务板轮询 / worktree 解析用） */
    private String projectPath;
    /** 绑定的 git worktree 路径（可选；绑定后队友工具的 projectPath 解析到此） */
    private String worktreePath;
    /** 类型化协议版本号：每次 work→idle 转换 +1 */
    private long workVersion;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;

    public static TeammateRecord spawn(String name, String description, String definition, String projectPath) {
        Instant now = Instant.now();
        return TeammateRecord.builder()
                .name(name)
                .description(description != null ? description : "")
                .definition(definition != null ? definition : "General")
                .status(STATUS_SPAWNED)
                .projectPath(projectPath)
                .workVersion(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /** 队友专属 branch：事件流隔离单位 */
    public String branch() {
        return "teammate." + name;
    }

    /** 队友运行时 projectPath：绑定 worktree 时解析到 worktree 路径 */
    public String effectiveProjectPath() {
        return worktreePath != null && !worktreePath.isBlank() ? worktreePath : projectPath;
    }

    public boolean isShutdown() {
        return STATUS_SHUTDOWN.equals(status);
    }

    public boolean isWorking() {
        return STATUS_WORK.equals(status);
    }

    public boolean isIdle() {
        return STATUS_IDLE.equals(status);
    }
}
