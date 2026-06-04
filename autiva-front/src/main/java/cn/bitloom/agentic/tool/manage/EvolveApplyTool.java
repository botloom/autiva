package cn.bitloom.agentic.tool.manage;

import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.evolve.EvolutionEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

@Slf4j
public class EvolveApplyTool {

    private final EvolutionEngine evolutionEngine;

    private EvolveApplyTool(EvolutionEngine evolutionEngine) {
        Assert.notNull(evolutionEngine, "evolutionEngine不能为null");
        this.evolutionEngine = evolutionEngine;
    }

    @Tool(name = "evolve_apply", description = """
            应用选定的基因或胶囊，获取策略步骤和约束条件指导行动。

            何时使用（重要）：
            - evolve_query 返回了相关基因时 → 应用获取具体策略指导
            - 面对复杂问题没有头绪时 → 先查询再应用
            - 需要系统化的行动方案时 → 应用基因获取策略步骤

            应用后会返回：策略步骤、约束条件、反模式警告等指导信息。
            """)
    public ToolResult apply(
            @ToolParam(description = "要应用的基因ID（与capsuleId二选一）", required = false) String geneId,
            @ToolParam(description = "要应用的胶囊ID（与geneId二选一）", required = false) String capsuleId,
            @ToolParam(description = "当前遇到的问题描述") String context
    ) {
        log.info("[ToolCall] evolve_apply - geneId={}, capsuleId={}, context={}", geneId, capsuleId, context);

        if ((geneId == null || geneId.isBlank()) && (capsuleId == null || capsuleId.isBlank())) {
            return ToolResult.error("请提供geneId或capsuleId参数（二选一）");
        }

        if (geneId != null && !geneId.isBlank()) {
            String result = evolutionEngine.applyGene(geneId, context);
            return ToolResult.builder().status(ToolResult.Status.SUCCESS)
                    .message("应用基因 " + geneId + " 的策略指导")
                    .rawOutput(result)
                    .build();
        }

        String result = evolutionEngine.applyCapsule(capsuleId, context);
        return ToolResult.builder().status(ToolResult.Status.SUCCESS)
                .message("应用胶囊 " + capsuleId + " 的策略指导")
                .rawOutput(result)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EvolutionEngine evolutionEngine;

        public Builder evolutionEngine(EvolutionEngine evolutionEngine) {
            this.evolutionEngine = evolutionEngine;
            return this;
        }

        public EvolveApplyTool build() {
            return new EvolveApplyTool(evolutionEngine);
        }
    }
}
