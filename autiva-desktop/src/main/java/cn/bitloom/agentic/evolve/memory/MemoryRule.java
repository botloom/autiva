package cn.bitloom.agentic.evolve.memory;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemoryRule(
        String id,
        String pattern,
        String action,
        double confidence,
        int hitCount,
        long createdAt,
        String source
) {
    public MemoryRule withHitCount(int newHitCount) {
        return new MemoryRule(id, pattern, action, confidence, newHitCount, createdAt, source);
    }
}
