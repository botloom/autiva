package cn.bitloom.agentic.evolve.gene;

import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.signal.Signal;
import cn.bitloom.agentic.evolve.signal.SignalHistory;
import cn.bitloom.agentic.evolve.strategy.StrategyPreset;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class GeneSelector {

    private final EvolveConfig config;

    public GeneSelector(EvolveConfig config) {
        this.config = config;
    }

    public GeneSelectionResult select(
            List<Signal> signals,
            List<Gene> genes,
            StrategyPreset preset,
            SignalHistory.HistoryAnalysis historyAnalysis
    ) {
        List<Gene> enabledGenes = genes.stream().filter(Gene::enabled).toList();

        if (enabledGenes.isEmpty()) {
            return GeneSelectionResult.empty("没有可用的基因");
        }

        List<ScoredGene> scored = enabledGenes.stream()
                .map(gene -> new ScoredGene(gene, computeScore(gene, signals, preset, historyAnalysis)))
                .sorted(Comparator.comparingDouble(ScoredGene::score).reversed())
                .toList();

        if (historyAnalysis.repairLoop()) {
            Optional<ScoredGene> innovate = scored.stream()
                    .filter(sg -> sg.gene().category() == cn.bitloom.agentic.evolve.gene.GeneCategory.INNOVATE)
                    .findFirst();
            if (innovate.isPresent()) {
                log.info("[Evolve] 检测到修复循环，强制选择创新基因: {}", innovate.get().gene().id());
                return new GeneSelectionResult(innovate.get().gene(), "修复循环强制创新", innovate.get().score());
            }
        }

        if (historyAnalysis.stagnation()) {
            Optional<ScoredGene> explore = scored.stream()
                    .filter(sg -> sg.gene().category() == cn.bitloom.agentic.evolve.gene.GeneCategory.INNOVATE
                            || sg.gene().category() == cn.bitloom.agentic.evolve.gene.GeneCategory.OPTIMIZE)
                    .findFirst();
            if (explore.isPresent()) {
                log.info("[Evolve] 检测到停滞，选择探索基因: {}", explore.get().gene().id());
                return new GeneSelectionResult(explore.get().gene(), "停滞探索模式", explore.get().score());
            }
        }

        ScoredGene best = scored.get(0);
        return new GeneSelectionResult(best.gene(), "最佳匹配", best.score());
    }

    private double computeScore(Gene gene, List<Signal> signals, StrategyPreset preset,
                                SignalHistory.HistoryAnalysis history) {
        double score = 0;

        Set<String> signalCodes = signals.stream()
                .map(s -> s.type().code())
                .collect(Collectors.toSet());

        long matchCount = gene.signalsMatch().stream()
                .filter(signalCodes::contains)
                .count();

        if (!gene.signalsMatch().isEmpty()) {
            score += (double) matchCount / gene.signalsMatch().size() * 40;
        }

        score += gene.epigeneticBoost() * 20;

        double strategyWeight = preset.weightFor(gene.category().code());
        score += strategyWeight * 30;

        if (history.highFailure() && gene.category() == cn.bitloom.agentic.evolve.gene.GeneCategory.REPAIR) {
            score += 10;
        }

        return score;
    }

    public record GeneSelectionResult(Gene gene, String reason, double score) {
        public static GeneSelectionResult empty(String reason) {
            return new GeneSelectionResult(null, reason, 0);
        }

        public boolean hasGene() {
            return gene != null;
        }
    }

    private record ScoredGene(Gene gene, double score) {
    }
}
