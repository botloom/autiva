package cn.bitloom.agentic.tool.manage;

import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.evolve.EvolutionEngine;
import cn.bitloom.agentic.evolve.gene.GeneCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

@Slf4j
public class EvolveQueryTool {

    private final EvolutionEngine evolutionEngine;

    private EvolveQueryTool(EvolutionEngine evolutionEngine) {
        Assert.notNull(evolutionEngine, "evolutionEngine不能为null");
        this.evolutionEngine = evolutionEngine;
    }

    @Tool(name = "evolve_query", description = """
            查询进化引擎的基因库和胶囊库，获取自适应行为建议。

            查询类型：
            - genes: 查看所有基因
            - capsules: 查看所有胶囊
            - recommend: 获取当前最推荐的基因（基于信号分析）
            - events: 查看最近进化事件
            - gene_detail: 查看特定基因详情

            何时使用（重要）：
            - 遇到错误、失败或异常时 → 查询修复类基因
            - 感觉当前方法效率不高时 → 查询优化类基因
            - 需要新思路或新能力时 → 查询创新类基因
            - 用户表达不满或提出改进建议时 → 查询相关基因
            - 连续多次尝试未成功时 → 获取推荐基因
            - 不确定如何处理某个问题时 → 先查询进化建议

            不要等到用户要求才查询，主动查询是好的行为。
            """)
    public ToolResult query(
            @ToolParam(description = "查询类型: genes=查看所有基因, capsules=查看所有胶囊, recommend=获取推荐基因, events=查看最近进化事件, gene_detail=查看特定基因详情") String query,
            @ToolParam(description = "按类别筛选基因（可选）: REPAIR/OPTIMIZE/INNOVATE", required = false) String category,
            @ToolParam(description = "基因ID（query=gene_detail时必填）", required = false) String geneId
    ) {
        log.info("[ToolCall] evolve_query - query={}, category={}, geneId={}", query, category, geneId);

        return switch (query.toLowerCase()) {
            case "genes" -> {
                GeneCategory cat = category != null ? GeneCategory.fromCode(category) : null;
                String result = evolutionEngine.queryGenes(cat);
                yield ToolResult.builder().status(ToolResult.Status.SUCCESS)
                        .message("基因列表")
                        .rawOutput(result)
                        .build();
            }
            case "capsules" -> {
                String result = evolutionEngine.queryCapsules();
                yield ToolResult.builder().status(ToolResult.Status.SUCCESS)
                        .message("胶囊列表")
                        .rawOutput(result)
                        .build();
            }
            case "recommend" -> {
                String result = evolutionEngine.recommendGenes(java.util.Collections.emptyList());
                yield ToolResult.builder().status(ToolResult.Status.SUCCESS)
                        .message("推荐基因")
                        .rawOutput(result)
                        .build();
            }
            case "events" -> {
                String result = evolutionEngine.queryEvents(10);
                yield ToolResult.builder().status(ToolResult.Status.SUCCESS)
                        .message("最近进化事件")
                        .rawOutput(result)
                        .build();
            }
            case "gene_detail" -> {
                if (geneId == null || geneId.isBlank()) {
                    yield ToolResult.error("请提供geneId参数查看基因详情");
                }
                String result = evolutionEngine.queryGeneDetail(geneId);
                yield ToolResult.builder().status(ToolResult.Status.SUCCESS)
                        .message("基因详情: " + geneId)
                        .rawOutput(result)
                        .build();
            }
            default -> ToolResult.error("不支持的查询类型: " + query + "。可用: genes, capsules, recommend, events, gene_detail");
        };
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

        public EvolveQueryTool build() {
            return new EvolveQueryTool(evolutionEngine);
        }
    }
}
