package cn.bitloom.agentic.evolve.verify;

/**
 * 单个验证维度的评估结果。
 *
 * @param dimension 维度名称（如 task_result、rule_compliance 等）
 * @param verdict   判定结果
 * @param evidence  支撑判定的证据描述
 * @param score     维度评分（0.0 - 1.0）
 * @param reason    判定理由
 */
public record DimensionResult(
        String dimension,
        Verdict verdict,
        String evidence,
        double score,
        String reason
) {
}
