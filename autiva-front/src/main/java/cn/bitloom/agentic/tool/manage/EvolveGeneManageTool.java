package cn.bitloom.agentic.tool.manage;

import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.evolve.gene.GeneStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

@Slf4j
public class EvolveGeneManageTool {

    private final GeneStore geneStore;

    private EvolveGeneManageTool(GeneStore geneStore) {
        Assert.notNull(geneStore, "geneStore不能为null");
        this.geneStore = geneStore;
    }

    @Tool(name = "evolve_gene_toggle", description = "启用或禁用指定基因")
    public ToolResult toggleGene(
            @ToolParam(description = "基因ID") String geneId,
            @ToolParam(description = "true=启用, false=禁用") boolean enabled
    ) {
        log.info("[ToolCall] evolve_gene_toggle - geneId={}, enabled={}", geneId, enabled);
        geneStore.toggleGene(geneId, enabled);
        return ToolResult.success("基因 " + geneId + " 已" + (enabled ? "启用" : "禁用"));
    }

    @Tool(name = "evolve_gene_delete", description = "删除指定基因（破坏性操作，不可恢复）")
    public ToolResult deleteGene(
            @ToolParam(description = "要删除的基因ID") String geneId
    ) {
        log.info("[ToolCall] evolve_gene_delete - geneId={}", geneId);
        geneStore.deleteGene(geneId);
        return ToolResult.success("基因 " + geneId + " 已删除");
    }

    @Tool(name = "evolve_capsule_delete", description = "删除指定胶囊（破坏性操作，不可恢复）")
    public ToolResult deleteCapsule(
            @ToolParam(description = "要删除的胶囊ID") String capsuleId
    ) {
        log.info("[ToolCall] evolve_capsule_delete - capsuleId={}", capsuleId);
        geneStore.deleteCapsule(capsuleId);
        return ToolResult.success("胶囊 " + capsuleId + " 已删除");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private GeneStore geneStore;

        public Builder geneStore(GeneStore geneStore) {
            this.geneStore = geneStore;
            return this;
        }

        public EvolveGeneManageTool build() {
            return new EvolveGeneManageTool(geneStore);
        }
    }
}
