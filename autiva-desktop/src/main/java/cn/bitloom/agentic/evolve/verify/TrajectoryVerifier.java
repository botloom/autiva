package cn.bitloom.agentic.evolve.verify;

import cn.bitloom.agentic.evolve.trajectory.Trajectory;
import cn.bitloom.agentic.evolve.trajectory.TrajectoryOutcome;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 轨迹验证编排器 — 编排三层验证器对轨迹进行完整验证。
 * <p>
 * 验证流程：
 * <ol>
 *   <li>结果层验证（{@link ResultVerifier}）：读取环境真值判断"事情是否真的办成"</li>
 *   <li>过程层验证（{@link ProcessVerifier}）：检查"是否以允许的方式办成"</li>
 *   <li>质量层验证（{@link QualityVerifier}）：LLM Judge 评估回复质量</li>
 * </ol>
 * 汇总所有维度的 {@link DimensionResult}，计算综合 outcome 和 confidence。
 */
@Slf4j
public class TrajectoryVerifier {

    /** 一票否决维度（该维度 FAIL 则整体 FAILURE） */
    private static final Set<String> VETO_DIMENSIONS = Set.of("task_result", "rule_compliance");

    private final ResultVerifier resultVerifier;
    private final ProcessVerifier processVerifier;
    private final QualityVerifier qualityVerifier;

    public TrajectoryVerifier(ResultVerifier resultVerifier,
                               ProcessVerifier processVerifier,
                               QualityVerifier qualityVerifier) {
        this.resultVerifier = resultVerifier;
        this.processVerifier = processVerifier;
        this.qualityVerifier = qualityVerifier;
    }

    /**
     * 执行完整的三层验证。
     *
     * @param trajectory 被验证的轨迹
     * @param context    验证上下文
     * @return 验证结果
     */
    public VerificationResult verify(Trajectory trajectory, VerificationContext context) {
        List<DimensionResult> allDimensions = new ArrayList<>();

        // 1. 结果层验证
        try {
            allDimensions.add(resultVerifier.verify(trajectory, context));
        } catch (Exception e) {
            log.error("[TrajectoryVerifier] 结果层验证失败", e);
            allDimensions.add(new DimensionResult(
                    "task_result", Verdict.UNCERTAIN, "验证异常", 0.0,
                    "结果层验证失败: " + e.getMessage()));
        }

        // 2. 过程层验证
        try {
            allDimensions.addAll(processVerifier.verify(trajectory, context));
        } catch (Exception e) {
            log.error("[TrajectoryVerifier] 过程层验证失败", e);
            allDimensions.add(new DimensionResult(
                    "rule_compliance", Verdict.UNCERTAIN, "验证异常", 0.0,
                    "过程层验证失败: " + e.getMessage()));
            allDimensions.add(new DimensionResult(
                    "promise_action_consistency", Verdict.UNCERTAIN, "验证异常", 0.0,
                    "过程层验证失败: " + e.getMessage()));
        }

        // 3. 质量层验证
        try {
            allDimensions.addAll(qualityVerifier.verify(trajectory, context));
        } catch (Exception e) {
            log.error("[TrajectoryVerifier] 质量层验证失败", e);
            allDimensions.add(new DimensionResult(
                    "privacy", Verdict.UNCERTAIN, "验证异常", 0.0,
                    "质量层验证失败: " + e.getMessage()));
            allDimensions.add(new DimensionResult(
                    "fact_reliability", Verdict.UNCERTAIN, "验证异常", 0.0,
                    "质量层验证失败: " + e.getMessage()));
            allDimensions.add(new DimensionResult(
                    "expression_quality", Verdict.UNCERTAIN, "验证异常", 0.0,
                    "质量层验证失败: " + e.getMessage()));
            allDimensions.add(new DimensionResult(
                    "compliant_alternative", Verdict.UNCERTAIN, "验证异常", 0.0,
                    "质量层验证失败: " + e.getMessage()));
        }

        // 4. 汇总计算综合 outcome 和 confidence
        TrajectoryOutcome outcome = computeOutcome(allDimensions);
        double confidence = computeConfidence(allDimensions);
        String summary = buildSummary(allDimensions, outcome);

        return new VerificationResult(
                trajectory.id(),
                Instant.now(),
                outcome,
                allDimensions,
                summary,
                confidence
        );
    }

    /**
     * 根据各维度判定结果计算综合 outcome。
     * <p>
     * 规则：
     * <ul>
     *   <li>任一否决维度 FAIL → FAILURE</li>
     *   <li>非否决维度有 FAIL 但否决维度全 PASS → PARTIAL_SUCCESS</li>
     *   <li>所有维度 PASS → SUCCESS</li>
     *   <li>存在 UNCERTAIN 且无 FAIL → UNKNOWN</li>
     * </ul>
     */
    private TrajectoryOutcome computeOutcome(List<DimensionResult> dimensions) {
        if (dimensions.isEmpty()) {
            return TrajectoryOutcome.UNKNOWN;
        }

        boolean hasVetoFail = false;
        boolean hasNonVetoFail = false;
        boolean hasUncertain = false;

        for (DimensionResult d : dimensions) {
            boolean isVeto = VETO_DIMENSIONS.contains(d.dimension());
            if (d.verdict() == Verdict.FAIL) {
                if (isVeto) {
                    hasVetoFail = true;
                } else {
                    hasNonVetoFail = true;
                }
            } else if (d.verdict() == Verdict.UNCERTAIN) {
                hasUncertain = true;
            }
        }

        if (hasVetoFail) {
            return TrajectoryOutcome.FAILURE;
        }
        if (hasNonVetoFail) {
            return TrajectoryOutcome.PARTIAL_SUCCESS;
        }
        if (hasUncertain) {
            return TrajectoryOutcome.UNKNOWN;
        }
        return TrajectoryOutcome.SUCCESS;
    }

    /**
     * 计算综合置信度（所有维度 score 的平均值）。
     */
    private double computeConfidence(List<DimensionResult> dimensions) {
        if (dimensions.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (DimensionResult d : dimensions) {
            sum += d.score();
        }
        return sum / dimensions.size();
    }

    /**
     * 构建验证总结。
     */
    private String buildSummary(List<DimensionResult> dimensions, TrajectoryOutcome outcome) {
        StringBuilder sb = new StringBuilder();
        sb.append("综合验证结果: ").append(outcome).append(". ");

        long passCount = dimensions.stream().filter(d -> d.verdict() == Verdict.PASS).count();
        long failCount = dimensions.stream().filter(d -> d.verdict() == Verdict.FAIL).count();
        long uncertainCount = dimensions.stream().filter(d -> d.verdict() == Verdict.UNCERTAIN).count();
        sb.append(String.format("通过: %d, 失败: %d, 不确定: %d. ", passCount, failCount, uncertainCount));

        // 列出失败维度
        List<String> failedDims = dimensions.stream()
                .filter(d -> d.verdict() == Verdict.FAIL)
                .map(DimensionResult::dimension)
                .toList();
        if (!failedDims.isEmpty()) {
            sb.append("失败维度: ").append(String.join(", ", failedDims)).append(". ");
        }

        // 列出不确定维度
        List<String> uncertainDims = dimensions.stream()
                .filter(d -> d.verdict() == Verdict.UNCERTAIN)
                .map(DimensionResult::dimension)
                .toList();
        if (!uncertainDims.isEmpty()) {
            sb.append("不确定维度: ").append(String.join(", ", uncertainDims)).append(". ");
        }

        return sb.toString();
    }
}
