package cn.bitloom.agentic.evolve.gene;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
        boolean enabled,
        GeneRuntimeType runtimeType,
        String code,
        int version,
        String parentId,
        long createdAt,
        long updatedAt
) {
    public Gene {
        if (Double.isNaN(epigeneticBoost) || epigeneticBoost < 0) {
            epigeneticBoost = 1.0;
        }
        if (runtimeType == null) {
            runtimeType = GeneRuntimeType.STRATEGY;
        }
        if (version <= 0) {
            version = 1;
        }
        if (createdAt <= 0) {
            createdAt = System.currentTimeMillis();
        }
        if (updatedAt <= 0) {
            updatedAt = System.currentTimeMillis();
        }
    }

    public Gene withEpigeneticBoost(double newBoost) {
        return new Gene(id, category, signalsMatch, preconditions, strategy,
                constraints, validation, newBoost, summary, antiPatterns, enabled,
                runtimeType, code, version, parentId, createdAt, System.currentTimeMillis());
    }

    public Gene withEnabled(boolean newEnabled) {
        return new Gene(id, category, signalsMatch, preconditions, strategy,
                constraints, validation, epigeneticBoost, summary, antiPatterns, newEnabled,
                runtimeType, code, version, parentId, createdAt, System.currentTimeMillis());
    }

    public Gene withCode(String newCode) {
        return new Gene(id, category, signalsMatch, preconditions, strategy,
                constraints, validation, epigeneticBoost, summary, antiPatterns, enabled,
                runtimeType, newCode, version, parentId, createdAt, System.currentTimeMillis());
    }

    public Gene withVersion(int newVersion) {
        return new Gene(id, category, signalsMatch, preconditions, strategy,
                constraints, validation, epigeneticBoost, summary, antiPatterns, enabled,
                runtimeType, code, newVersion, parentId, createdAt, System.currentTimeMillis());
    }

    public Gene withRuntimeType(GeneRuntimeType newRuntimeType) {
        return new Gene(id, category, signalsMatch, preconditions, strategy,
                constraints, validation, epigeneticBoost, summary, antiPatterns, enabled,
                newRuntimeType, code, version, parentId, createdAt, System.currentTimeMillis());
    }
}
