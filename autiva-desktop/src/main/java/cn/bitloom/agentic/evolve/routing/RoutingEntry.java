package cn.bitloom.agentic.evolve.routing;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoutingEntry(
        String pattern,
        String geneId,
        double weight,
        long createdAt,
        String source
) {}
