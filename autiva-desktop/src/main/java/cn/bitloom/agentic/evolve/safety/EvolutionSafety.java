package cn.bitloom.agentic.evolve.safety;

import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class EvolutionSafety {

    private final EvolveConfig config;
    private final GeneStore geneStore;
    private final Map<String, Long> lastMutationTime = new ConcurrentHashMap<>();

    public EvolutionSafety(EvolveConfig config, GeneStore geneStore) {
        this.config = config;
        this.geneStore = geneStore;
    }

    public SafetyCheckResult check(Gene original, Gene mutated) {
        if (original.id() == null || !original.id().equals(mutated.id())) {
            return new SafetyCheckResult(false, "基因ID不一致");
        }

        if (mutated.code() == null || mutated.code().isEmpty()) {
            return new SafetyCheckResult(false, "突变后代码为空");
        }

        if (original.constraints() != null && mutated.constraints() != null) {
            Object originalBlast = original.constraints().get("blast_radius_limit");
            Object mutatedBlast = mutated.constraints().get("blast_radius_limit");
            if (originalBlast != null && mutatedBlast != null) {
                int origLimit = Integer.parseInt(originalBlast.toString());
                int mutLimit = Integer.parseInt(mutatedBlast.toString());
                if (mutLimit > origLimit * config.getMaxComplexityIncrease()) {
                    return new SafetyCheckResult(false, "爆炸半径增长过大: " + mutLimit + " > " + (origLimit * config.getMaxComplexityIncrease()));
                }
            }
        }

        long now = System.currentTimeMillis();
        Long lastTime = lastMutationTime.get(original.id());
        if (lastTime != null) {
            long hourMs = 3600_000L;
            long mutationsInLastHour = lastMutationTime.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(original.id()))
                    .filter(e -> now - e.getValue() < hourMs)
                    .count();
            if (mutationsInLastHour >= config.getMutationFrequencyLimitPerHour()) {
                return new SafetyCheckResult(false, "突变频率超限: " + mutationsInLastHour + "/小时");
            }
        }

        if (mutated.code() != null && original.code() != null) {
            double lengthRatio = (double) mutated.code().length() / Math.max(original.code().length(), 1);
            if (lengthRatio > config.getMaxComplexityIncrease()) {
                return new SafetyCheckResult(false, "代码长度增长过大: " + String.format("%.1f", lengthRatio) + "x");
            }
        }

        lastMutationTime.put(original.id() + "_" + mutated.version(), now);

        return new SafetyCheckResult(true, "安全检查通过");
    }

    public record SafetyCheckResult(boolean passed, String message) {}
}
