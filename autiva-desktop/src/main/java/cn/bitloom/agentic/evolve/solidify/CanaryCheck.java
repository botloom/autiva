package cn.bitloom.agentic.evolve.solidify;

import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.trajectory.Trajectory;
import cn.bitloom.agentic.evolve.trajectory.TrajectoryOutcome;
import cn.bitloom.agentic.evolve.trajectory.TrajectoryStep;
import cn.bitloom.agentic.evolve.verify.TrajectoryVerifier;
import cn.bitloom.agentic.evolve.verify.VerificationContext;
import cn.bitloom.agentic.evolve.verify.VerificationResult;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 金丝雀检查 — 在小样本上验证候选基因不造成退化。
 * <p>
 * 由于无法真正重放（需要重新执行 Agent），采用简化策略：
 * <ol>
 *   <li>对每条测试轨迹运行验证器，生成"无基因"基线 VerificationResult</li>
 *   <li>检查候选基因 epigeneticBoost 是否 &gt;= 0.5（低于 0.5 直接拒绝）</li>
 *   <li>检查候选基因 constraints 是否与测试轨迹的工具使用冲突</li>
 *   <li>检查候选基因 signalsMatch 是否与测试轨迹的特征匹配</li>
 * </ol>
 * 最终返回 {@link CanaryResult}，含通过/失败标志与预估改进分。
 */
@Slf4j
public class CanaryCheck {

    /** 最低 epigeneticBoost 门槛 */
    private static final double MIN_BOOST = 0.5;

    private final TrajectoryVerifier verifier;
    private final VerificationContext context;

    public CanaryCheck(TrajectoryVerifier verifier, VerificationContext context) {
        this.verifier = verifier;
        this.context = context;
    }

    /**
     * 对候选基因执行金丝雀检查。
     *
     * @param candidate         候选基因
     * @param testTrajectories  测试轨迹样本
     * @return 金丝雀检查结果
     */
    public CanaryResult check(Gene candidate, List<Trajectory> testTrajectories) {
        if (candidate == null) {
            return new CanaryResult(false, "候选基因为 null", 0.0);
        }

        // 1. epigeneticBoost 门槛
        double boost = candidate.epigeneticBoost();
        if (boost < MIN_BOOST) {
            return new CanaryResult(false,
                    "epigeneticBoost 低于门槛 " + MIN_BOOST + ": " + boost, 0.0);
        }

        // 2. 生成"无基因"基线 VerificationResult（取平均置信度作为基线参考）
        double baselineConfidence = computeBaselineConfidence(testTrajectories);

        // 3. 无测试轨迹时，仅基于 boost 通过
        if (testTrajectories == null || testTrajectories.isEmpty()) {
            return new CanaryResult(true,
                    "无测试轨迹，基于 epigeneticBoost 通过", boost);
        }

        // 4. 收集测试轨迹的工具使用
        Set<String> trajectoryTools = collectTools(testTrajectories);

        // 5. constraints 冲突检查
        String conflict = checkConstraintsConflict(candidate, trajectoryTools);
        if (conflict != null) {
            return new CanaryResult(false, conflict, 0.0);
        }

        // 6. signalsMatch 匹配度
        double matchScore = computeSignalsMatch(candidate, testTrajectories);
        if (matchScore <= 0.0) {
            return new CanaryResult(false,
                    "signalsMatch 与测试轨迹特征不匹配", 0.0);
        }

        // 7. 综合改进分（匹配度 × boost，基线作为参考记录在 reason 中）
        double improvementScore = matchScore * Math.min(boost, 1.0);
        return new CanaryResult(true,
                "金丝雀检查通过（基线置信度=" + String.format("%.2f", baselineConfidence)
                        + ", 匹配度=" + String.format("%.2f", matchScore) + ")",
                improvementScore);
    }

    /**
     * 对每条测试轨迹运行验证器，返回平均置信度作为基线。
     */
    private double computeBaselineConfidence(List<Trajectory> trajectories) {
        if (trajectories == null || trajectories.isEmpty()) {
            return 0.5;
        }
        double sum = 0.0;
        int count = 0;
        for (Trajectory t : trajectories) {
            try {
                VerificationResult vr = verifier.verify(t, context);
                sum += vr.confidence();
                count++;
            } catch (Exception e) {
                log.warn("[CanaryCheck] 基线验证失败: trajectoryId={}", t.id(), e);
            }
        }
        return count > 0 ? sum / count : 0.5;
    }

    /**
     * 收集测试轨迹中使用的全部工具名（小写）。
     */
    private Set<String> collectTools(List<Trajectory> trajectories) {
        Set<String> tools = new HashSet<>();
        for (Trajectory t : trajectories) {
            if (t.steps() == null) {
                continue;
            }
            for (TrajectoryStep step : t.steps()) {
                if (step.toolName() != null && !step.toolName().isBlank()) {
                    tools.add(step.toolName().toLowerCase());
                }
            }
        }
        return tools;
    }

    /**
     * 检查基因 constraints 是否与测试轨迹工具使用冲突。
     * <p>
     * 简化策略：若某条 constraint 含"禁止/禁用/forbidden"且包含被使用的工具名，则视为冲突。
     *
     * @return 冲突描述，null 表示无冲突
     */
    private String checkConstraintsConflict(Gene candidate, Set<String> trajectoryTools) {
        List<String> constraints = candidate.constraints();
        if (constraints == null || constraints.isEmpty() || trajectoryTools.isEmpty()) {
            return null;
        }
        for (String constraint : constraints) {
            if (constraint == null || constraint.isBlank()) {
                continue;
            }
            String lower = constraint.toLowerCase();
            if (!lower.contains("禁止") && !lower.contains("禁用") && !lower.contains("forbidden")) {
                continue;
            }
            for (String tool : trajectoryTools) {
                if (lower.contains(tool)) {
                    return "约束与测试轨迹工具使用冲突: 约束='" + constraint + "', 工具=" + tool;
                }
            }
        }
        return null;
    }

    /**
     * 计算候选基因 signalsMatch 与测试轨迹特征的综合匹配度（0.0-1.0）。
     * <p>
     * 分别检查 taskType / toolsUsed / errorPatterns 三类信号，
     * 任一信号命中则计为命中，最终匹配度 = 命中信号数 / 启用信号数。
     */
    private double computeSignalsMatch(Gene candidate, List<Trajectory> trajectories) {
        Map<String, Object> signals = candidate.signalsMatch();
        if (signals == null || signals.isEmpty()) {
            // 无 signalsMatch 视为中性匹配
            return 0.5;
        }

        int hit = 0;
        int enabled = 0;

        // taskType 匹配
        Object geneTaskType = signals.get("taskType");
        if (geneTaskType != null) {
            enabled++;
            if (anyTaskTypeMatches(geneTaskType, trajectories)) {
                hit++;
            }
        }

        // toolsUsed 匹配
        Object geneTools = signals.get("toolsUsed");
        if (geneTools != null) {
            enabled++;
            Set<String> geneToolSet = toStringSet(geneTools);
            if (!geneToolSet.isEmpty() && anyToolsOverlap(geneToolSet, trajectories)) {
                hit++;
            }
        }

        // errorPatterns 匹配（简化：存在失败轨迹即视为命中）
        Object geneErrors = signals.get("errorPatterns");
        if (geneErrors != null) {
            enabled++;
            if (anyFailureTrajectory(trajectories)) {
                hit++;
            }
        }

        if (enabled == 0) {
            return 0.5;
        }
        return (double) hit / enabled;
    }

    private boolean anyTaskTypeMatches(Object geneTaskType, List<Trajectory> trajectories) {
        Set<String> geneTaskTypes = toStringSet(geneTaskType);
        for (Trajectory t : trajectories) {
            String trajTaskType = extractTaskType(t);
            if (trajTaskType != null && geneTaskTypes.contains(trajTaskType.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean anyToolsOverlap(Set<String> geneTools, List<Trajectory> trajectories) {
        for (Trajectory t : trajectories) {
            Set<String> trajTools = new HashSet<>();
            if (t.steps() != null) {
                for (TrajectoryStep step : t.steps()) {
                    if (step.toolName() != null && !step.toolName().isBlank()) {
                        trajTools.add(step.toolName().toLowerCase());
                    }
                }
            }
            for (String tool : trajTools) {
                if (geneTools.contains(tool)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean anyFailureTrajectory(List<Trajectory> trajectories) {
        for (Trajectory t : trajectories) {
            if (t.outcome() == TrajectoryOutcome.FAILURE) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从轨迹提取 taskType：优先 metadata.taskFamily，回退 agentName。
     */
    private String extractTaskType(Trajectory t) {
        if (t.metadata() != null) {
            Object tf = t.metadata().get("taskFamily");
            if (tf instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return t.agentName();
    }

    /**
     * 将信号值（单值或列表）转为小写字符串集合。
     */
    private Set<String> toStringSet(Object value) {
        Set<String> set = new HashSet<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    set.add(String.valueOf(item).toLowerCase());
                }
            }
        } else if (value != null) {
            set.add(String.valueOf(value).toLowerCase());
        }
        return set;
    }

    /**
     * 金丝雀检查结果。
     *
     * @param passed            是否通过
     * @param reason            通过/失败原因
     * @param improvementScore  预估改进分（0.0-1.0）
     */
    public record CanaryResult(boolean passed, String reason, double improvementScore) {
    }
}
