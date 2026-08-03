package cn.bitloom.agentic.evolve.verify;

import cn.bitloom.agentic.evolve.trajectory.TrajectoryOutcome;

import java.time.Instant;
import java.util.List;

/**
 * 轨迹验证的完整结果。
 *
 * @param trajectoryId 被验证的轨迹 ID
 * @param verifiedAt   验证时间
 * @param outcome      综合验证结果（映射为轨迹结果类型）
 * @param dimensions   各维度的验证详情
 * @param summary      验证总结
 * @param confidence   综合置信度（0.0 - 1.0）
 */
public record VerificationResult(
        String trajectoryId,
        Instant verifiedAt,
        TrajectoryOutcome outcome,
        List<DimensionResult> dimensions,
        String summary,
        double confidence
) {
}
