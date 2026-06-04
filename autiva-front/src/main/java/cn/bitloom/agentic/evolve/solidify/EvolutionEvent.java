package cn.bitloom.agentic.evolve.solidify;

import java.util.List;
import java.util.Map;

public record EvolutionEvent(
        String id,
        long timestamp,
        List<String> signals,
        String geneId,
        String intent,
        String prompt,
        Outcome outcome,
        Map<String, Object> meta
) {

    public record Outcome(
            String status,
            double score,
            int blastRadius
    ) {
    }

    public boolean isSuccess() {
        return outcome != null && "success".equals(outcome.status());
    }

    public boolean isPending() {
        return outcome != null && "pending".equals(outcome.status());
    }
}
