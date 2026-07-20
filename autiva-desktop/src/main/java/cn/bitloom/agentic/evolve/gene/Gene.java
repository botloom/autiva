package cn.bitloom.agentic.evolve.gene;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Gene - L4 爬山循环可优化的配置单元。
 * <p>
 * 一个 Gene 对应一份可被 HillClimbingEngine 优化的配置片段。
 * 不再是"可执行技能"，而是 Agent 配置的一部分（Prompt/工具描述/Rubric/技能配置）。
 * <p>
 * 版本通过 JGit 管理，epigeneticBoost 基于 L2 校验通过率动态调整。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Gene(
        String id,
        GeneType type,
        String targetId,
        String name,
        String content,
        String description,
        double epigeneticBoost,
        boolean enabled,
        int version,
        String parentId,
        long createdAt,
        long updatedAt
) {
    public Gene {
        if (Double.isNaN(epigeneticBoost) || epigeneticBoost < 0) {
            epigeneticBoost = 1.0;
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
        return new Gene(id, type, targetId, name, content, description,
                newBoost, enabled, version, parentId, createdAt, System.currentTimeMillis());
    }

    public Gene withEnabled(boolean newEnabled) {
        return new Gene(id, type, targetId, name, content, description,
                epigeneticBoost, newEnabled, version, parentId, createdAt, System.currentTimeMillis());
    }

    public Gene withContent(String newContent) {
        return new Gene(id, type, targetId, name, newContent, description,
                epigeneticBoost, enabled, version, parentId, createdAt, System.currentTimeMillis());
    }

    public Gene withVersion(int newVersion) {
        return new Gene(id, type, targetId, name, content, description,
                epigeneticBoost, enabled, newVersion, parentId, createdAt, System.currentTimeMillis());
    }
}
