package cn.bitloom.agentic.evolve.signal;

import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.solidify.EvolutionEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class SignalHistory {

    private final EvolveConfig config;

    public SignalHistory(EvolveConfig config) {
        this.config = config;
    }

    public HistoryAnalysis analyze(List<EvolutionEvent> recentEvents) {
        int consecutiveRepairs = 0;
        int emptyCycles = 0;
        int failureStreak = 0;
        int successCount = 0;
        int totalWithOutcome = 0;

        for (int i = recentEvents.size() - 1; i >= 0; i--) {
            EvolutionEvent event = recentEvents.get(i);
            if (event.outcome() == null) continue;

            totalWithOutcome++;
            boolean isSuccess = event.isSuccess();

            if (isSuccess) {
                successCount++;
                break;
            }

            failureStreak++;

            if ("repair".equals(event.intent()) || (event.geneId() != null && event.geneId().contains("repair"))) {
                consecutiveRepairs++;
            }

            if (event.outcome().blastRadius() == 0) {
                emptyCycles++;
            }
        }

        double failureRate = totalWithOutcome > 0
                ? (double) (totalWithOutcome - successCount) / totalWithOutcome
                : 0;

        boolean repairLoop = consecutiveRepairs >= config.getRepairLoopThreshold();
        boolean stagnation = emptyCycles >= config.getSaturationThreshold();
        boolean highFailure = failureRate >= config.getHighFailureRate();

        return new HistoryAnalysis(
                consecutiveRepairs,
                emptyCycles,
                failureStreak,
                failureRate,
                repairLoop,
                stagnation,
                highFailure
        );
    }

    public record HistoryAnalysis(
            int consecutiveRepairs,
            int emptyCycles,
            int failureStreak,
            double failureRate,
            boolean repairLoop,
            boolean stagnation,
            boolean highFailure
    ) {
    }
}
