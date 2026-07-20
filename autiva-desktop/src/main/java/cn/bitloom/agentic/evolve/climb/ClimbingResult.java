package cn.bitloom.agentic.evolve.climb;

import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.solidify.EvolutionEvent;

import java.util.List;

/**
 * L4 爬山循环的运行结果。
 *
 * @param agentId        分析的 Agent ID
 * @param traceCount     分析的 Trace 数量
 * @param analysisText   LLM 原始分析文本（供 UI 展示）
 * @param suggestions    全部优化建议（含未应用的）
 * @param appliedEvents  已应用的进化事件（安全检查通过的）
 * @param skippedCount   跳过的建议数量（置信度低/安全检查未通过/突变失败）
 * @param passRateBefore 优化前的 L2 校验通过率
 */
public record ClimbingResult(
        String agentId,
        int traceCount,
        String analysisText,
        List<OptimizationSuggestion> suggestions,
        List<EvolutionEvent> appliedEvents,
        int skippedCount,
        double passRateBefore
) {
    public int appliedCount() {
        return appliedEvents != null ? appliedEvents.size() : 0;
    }

    public boolean hasImprovement() {
        return appliedCount() > 0;
    }
}
