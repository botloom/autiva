package cn.bitloom.agentic.evolve.strategy;

import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.signal.SignalHistory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StrategyEngine {

    private final EvolveConfig config;

    public StrategyEngine(EvolveConfig config) {
        this.config = config;
    }

    public StrategyPreset resolve(SignalHistory.HistoryAnalysis analysis) {
        if (config.getStrategyPreset() != StrategyPreset.AUTO) {
            return config.getStrategyPreset();
        }

        if (analysis.failureStreak() >= config.getFailureStreakThreshold()) {
            log.info("[Evolve] 策略降级: 连续失败 {} 次 → STEADY_STATE", analysis.failureStreak());
            return StrategyPreset.STEADY_STATE;
        }

        if (analysis.repairLoop()) {
            log.info("[Evolve] 策略切换: 修复循环 → INNOVATE");
            return StrategyPreset.INNOVATE;
        }

        if (analysis.stagnation()) {
            log.info("[Evolve] 策略切换: 停滞 → INNOVATE");
            return StrategyPreset.INNOVATE;
        }

        if (analysis.highFailure()) {
            log.info("[Evolve] 策略切换: 高失败率 → HARDEN");
            return StrategyPreset.HARDEN;
        }

        return StrategyPreset.BALANCED;
    }
}
