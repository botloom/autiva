package cn.bitloom.agentic.evolve.experience;

import java.util.List;

public record Experience(
        String id,
        long timestamp,
        String pattern,
        String rootCause,
        String fix,
        ExperienceTarget target,
        String targetId,
        double confidence,
        List<String> sourceLogIds
) {
    public boolean isActionable() {
        return confidence >= 0.7;
    }
}
