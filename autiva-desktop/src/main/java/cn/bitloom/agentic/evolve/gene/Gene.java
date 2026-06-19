package cn.bitloom.agentic.evolve.gene;

import java.util.List;
import java.util.Map;

public record Gene(
        String id,
        GeneCategory category,
        List<String> signalsMatch,
        List<String> preconditions,
        List<String> strategy,
        Map<String, Object> constraints,
        List<String> validation,
        double epigeneticBoost,
        String summary,
        List<String> antiPatterns,
        boolean enabled
) {
    public Gene {
        if (Double.isNaN(epigeneticBoost) || epigeneticBoost < 0) {
            epigeneticBoost = 1.0;
        }
    }

    public Gene withEpigeneticBoost(double newBoost) {
        return new Gene(id, category, signalsMatch, preconditions, strategy,
                constraints, validation, newBoost, summary, antiPatterns, enabled);
    }

    public Gene withEnabled(boolean newEnabled) {
        return new Gene(id, category, signalsMatch, preconditions, strategy,
                constraints, validation, epigeneticBoost, summary, antiPatterns, newEnabled);
    }
}
