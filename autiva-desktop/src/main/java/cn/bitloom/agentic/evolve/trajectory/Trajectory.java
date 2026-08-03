package cn.bitloom.agentic.evolve.trajectory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 一次完整的对话轨迹，记录 Agent 从接收用户消息到生成最终回复的全过程。
 *
 * @param id          轨迹唯一标识（UUID）
 * @param sessionId   会话 ID
 * @param branch      分支标识（子智能体隔离用，主智能体为 null）
 * @param agentName   智能体名称
 * @param startTime   轨迹开始时间
 * @param endTime     轨迹结束时间
 * @param userMessage 用户输入消息
 * @param steps       轨迹步骤列表
 * @param outcome     轨迹结果
 * @param metadata    额外元数据
 */
public record Trajectory(
        String id,
        String sessionId,
        String branch,
        String agentName,
        Instant startTime,
        Instant endTime,
        String userMessage,
        List<TrajectoryStep> steps,
        TrajectoryOutcome outcome,
        Map<String, Object> metadata
) {
}
