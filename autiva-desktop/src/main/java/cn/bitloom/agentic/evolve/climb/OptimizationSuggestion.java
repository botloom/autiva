package cn.bitloom.agentic.evolve.climb;

import cn.bitloom.agentic.evolve.gene.GeneType;

/**
 * L4 爬山循环的优化建议。
 *
 * <p>由 {@link HillClimbingEngine} 通过 LLM 分析 Trace 后输出，
 * 每个 suggestion 对应一条 Gene 的优化方向。</p>
 *
 * @param geneId     目标 Gene ID
 * @param geneType   Gene 类型
 * @param targetId   目标对象 ID（agent 名 / 工具名 / grader 名 / 技能名）
 * @param issue      分析发现的问题（具体、可量化）
 * @param suggestion 优化建议（具体、可执行）
 * @param confidence 置信度 [0,1]，低于阈值的建议不自动应用
 */
public record OptimizationSuggestion(
        String geneId,
        GeneType geneType,
        String targetId,
        String issue,
        String suggestion,
        double confidence
) {
    public boolean isHighConfidence(double threshold) {
        return confidence >= threshold;
    }
}
