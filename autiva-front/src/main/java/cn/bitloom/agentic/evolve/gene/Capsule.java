package cn.bitloom.agentic.evolve.gene;

import java.util.List;
import java.util.Map;

public record Capsule(
        String id,
        List<String> geneIds,
        Map<String, Object> context,
        double score,
        long createdAt
) {
    public Capsule {
        if (createdAt == 0) {
            createdAt = System.currentTimeMillis();
        }
    }
}
