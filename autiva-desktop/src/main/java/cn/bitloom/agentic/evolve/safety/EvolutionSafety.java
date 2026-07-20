package cn.bitloom.agentic.evolve.safety;

import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.gene.Gene;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进化安全系统，防止 L4 爬山循环"改坏"配置。
 * <p>
 * 检查项：
 * - 基因ID一致性
 * - 突变后内容非空
 * - 内容长度增长限制
 * - 突变频率限制
 */
@Slf4j
@Component
public class EvolutionSafety {

    private final EvolveConfig config;
    private final Map<String, Long> lastMutationTime = new ConcurrentHashMap<>();

    public EvolutionSafety(EvolveConfig config) {
        this.config = config;
    }

    public SafetyCheckResult check(Gene original, Gene mutated) {
        if (original.id() == null || !original.id().equals(mutated.id())) {
            return new SafetyCheckResult(false, "基因ID不一致");
        }

        if (mutated.content() == null || mutated.content().isEmpty()) {
            return new SafetyCheckResult(false, "突变后配置内容为空");
        }

        // 内容长度增长限制
        if (original.content() != null && !original.content().isEmpty()) {
            double lengthRatio = (double) mutated.content().length() / Math.max(original.content().length(), 1);
            if (lengthRatio > config.getMaxComplexityIncrease()) {
                return new SafetyCheckResult(false, "配置内容长度增长过大: " + String.format("%.1f", lengthRatio) + "x");
            }
        }

        // 突变频率限制
        long now = System.currentTimeMillis();
        long hourMs = 3600_000L;
        long mutationsInLastHour = lastMutationTime.entrySet().stream()
                .filter(e -> e.getKey().startsWith(original.id()))
                .filter(e -> now - e.getValue() < hourMs)
                .count();
        if (mutationsInLastHour >= config.getMutationFrequencyLimitPerHour()) {
            return new SafetyCheckResult(false, "突变频率超限: " + mutationsInLastHour + "/小时");
        }

        lastMutationTime.put(original.id() + "_" + mutated.version(), now);
        return new SafetyCheckResult(true, "安全检查通过");
    }

    public record SafetyCheckResult(boolean passed, String message) {}
}
