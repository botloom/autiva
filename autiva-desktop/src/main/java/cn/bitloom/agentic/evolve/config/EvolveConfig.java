package cn.bitloom.agentic.evolve.config;

import cn.bitloom.constant.AppConstants;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 进化系统配置。
 * 包含路径配置、表观遗传参数、安全参数、Trace 配置。
 */
@Slf4j
@Getter
@Component
public class EvolveConfig {

    private final Path evolveDir = AppConstants.Evolve.EVOLVE_DIR;
    private final Path genesFile = evolveDir.resolve("genes.json");
    private final Path eventsFile = evolveDir.resolve("events.jsonl");
    private final Path candidatesFile = evolveDir.resolve("candidates.jsonl");
    private final Path genesDir = AppConstants.Evolve.GENES_DIR;
    private final Path executionsDir = AppConstants.Evolve.EXECUTIONS_DIR;

    // 表观遗传参数
    private double epigeneticDecay = 0.95;
    private double epigeneticBoostOnSuccess = 1.2;

    // 安全参数
    private double experienceConfidenceThreshold = 0.7;
    private int mutationFrequencyLimitPerHour = 10;
    private double maxComplexityIncrease = 1.5;

    // Trace 配置
    private int recentEventsLimit = 20;
    private int maxConversationRetries = 1;
    private int traceRetentionDays = 30;
}
