package cn.bitloom.agentic.evolve.config;

import cn.bitloom.agentic.evolve.strategy.StrategyPreset;
import cn.bitloom.constant.AppConstants;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Slf4j
@Getter
@Component
public class EvolveConfig {

    private final Path evolveDir = AppConstants.APP_DIR.resolve("evolve");
    private final Path genesFile = evolveDir.resolve("genes.json");
    private final Path capsulesFile = evolveDir.resolve("capsules.json");
    private final Path eventsFile = evolveDir.resolve("events.jsonl");
    private final Path candidatesFile = evolveDir.resolve("candidates.jsonl");

    private StrategyPreset strategyPreset = StrategyPreset.BALANCED;
    private int signalDedupWindow = 8;
    private int signalDedupThreshold = 3;
    private int repairLoopThreshold = 3;
    private int emptyCycleThreshold = 4;
    private int failureStreakThreshold = 5;
    private int saturationThreshold = 5;
    private int maxPromptLength = 24000;
    private int recentEventsLimit = 20;
    private double highFailureRate = 0.75;
    private double epigeneticDecay = 0.95;
    private double epigeneticBoostOnSuccess = 1.2;

    public void setStrategyPreset(StrategyPreset preset) {
        this.strategyPreset = preset;
        log.info("[Evolve] 策略预设已更新: {}", preset);
    }
}
