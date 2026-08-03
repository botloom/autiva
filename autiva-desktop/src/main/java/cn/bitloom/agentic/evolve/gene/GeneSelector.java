package cn.bitloom.agentic.evolve.gene;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基因选择器，基于 EvoMap 算法对基因进行打分排序。
 * <p>
 * 评分公式：totalScore = signalsMatchScore * 0.4 + epigeneticBoost * 0.6
 * <ul>
 *   <li>signalsMatchScore（0.0-1.0）= taskType 匹配(0.4) + toolsUsed 交集比例(0.3 × 比例) + errorPatterns 匹配(0.3)</li>
 *   <li>epigeneticBoost：表观遗传增强因子，默认 1.0</li>
 * </ul>
 */
@Slf4j
@Component
public class GeneSelector {

    private static final int DEFAULT_TOP_N = 5;
    private static final double SIGNALS_WEIGHT = 0.4;
    private static final double BOOST_WEIGHT = 0.6;

    private final GeneRepository geneRepository;

    public GeneSelector(GeneRepository geneRepository) {
        this.geneRepository = geneRepository;
    }

    /**
     * 选择与当前上下文最匹配的 top-N 基因。
     *
     * @param context 基因选择上下文，为 null 时使用默认值
     * @param topN    返回数量上限，&lt;=0 时使用默认值 5
     * @return 按总分降序排列的基因列表
     */
    public List<Gene> select(GeneSelectionContext context, int topN) {
        if (context == null) {
            context = GeneSelectionContext.defaults();
        }
        if (topN <= 0) {
            topN = DEFAULT_TOP_N;
        }

        final GeneSelectionContext ctx = context;
        List<Gene> candidates = geneRepository.findAll().stream()
                .filter(Gene::enabled)
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return List.of();
        }

        return candidates.stream()
                .map(gene -> Map.entry(gene, scoreGene(gene, ctx)))
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 使用默认 topN=5 选择基因。
     */
    public List<Gene> select(GeneSelectionContext context) {
        return select(context, DEFAULT_TOP_N);
    }

    private double scoreGene(Gene gene, GeneSelectionContext ctx) {
        double signalsScore = computeSignalsScore(gene, ctx);
        double boost = gene.epigeneticBoost();
        return signalsScore * SIGNALS_WEIGHT + boost * BOOST_WEIGHT;
    }

    /**
     * 计算信号匹配分（0.0-1.0）。
     */
    private double computeSignalsScore(Gene gene, GeneSelectionContext ctx) {
        Map<String, Object> signals = gene.signalsMatch();
        if (signals == null || signals.isEmpty()) {
            return 0.0;
        }

        double score = 0.0;

        // taskType 匹配 → 0.4
        if (taskTypeMatches(signals.get("taskType"), ctx.taskType())) {
            score += 0.4;
        }

        // toolsUsed 交集比例 → 0.3 × 比例
        double toolsRatio = computeToolsOverlap(signals.get("toolsUsed"), ctx.toolsUsed());
        score += 0.3 * toolsRatio;

        // errorPatterns 匹配 → 0.3
        if (errorPatternsMatch(signals.get("errorPatterns"), ctx.errorPatterns())) {
            score += 0.3;
        }

        return score;
    }

    private boolean taskTypeMatches(Object geneTaskType, String ctxTaskType) {
        if (ctxTaskType == null || geneTaskType == null) {
            return false;
        }
        if (geneTaskType instanceof List<?> list) {
            return list.stream().anyMatch(t -> ctxTaskType.equalsIgnoreCase(String.valueOf(t)));
        }
        return ctxTaskType.equalsIgnoreCase(String.valueOf(geneTaskType));
    }

    private double computeToolsOverlap(Object geneToolsValue, List<String> ctxTools) {
        if (ctxTools == null || ctxTools.isEmpty() || geneToolsValue == null) {
            return 0.0;
        }

        Set<String> geneTools = new HashSet<>();
        if (geneToolsValue instanceof List<?> list) {
            for (Object item : list) {
                geneTools.add(String.valueOf(item).toLowerCase());
            }
        } else {
            geneTools.add(String.valueOf(geneToolsValue).toLowerCase());
        }

        if (geneTools.isEmpty()) {
            return 0.0;
        }

        Set<String> ctxSet = new HashSet<>();
        for (String t : ctxTools) {
            ctxSet.add(t.toLowerCase());
        }

        // 交集比例 = 交集大小 / 当前任务工具数
        long intersection = ctxSet.stream().filter(geneTools::contains).count();
        return (double) intersection / ctxSet.size();
    }

    private boolean errorPatternsMatch(Object geneErrorsValue, List<String> ctxErrors) {
        if (ctxErrors == null || ctxErrors.isEmpty() || geneErrorsValue == null) {
            return false;
        }

        Set<String> geneErrors = new HashSet<>();
        if (geneErrorsValue instanceof List<?> list) {
            for (Object item : list) {
                geneErrors.add(String.valueOf(item).toLowerCase());
            }
        } else {
            geneErrors.add(String.valueOf(geneErrorsValue).toLowerCase());
        }

        for (String e : ctxErrors) {
            if (geneErrors.contains(e.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
