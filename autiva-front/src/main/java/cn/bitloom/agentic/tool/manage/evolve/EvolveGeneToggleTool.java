package cn.bitloom.agentic.tool.manage.evolve;

import cn.bitloom.agentic.evolve.gene.GeneStore;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

/**
 * 启用或禁用指定基因。
 */
@Slf4j
public class EvolveGeneToggleTool extends AbstractTool<EvolveGeneToggleTool.Input> {

    private static final String DESCRIPTION = "启用或禁用指定基因";

    private final GeneStore geneStore;

    private EvolveGeneToggleTool(GeneStore geneStore) {
        super("evolve_gene_toggle", DESCRIPTION, Input.class);
        Assert.notNull(geneStore, "geneStore不能为null");
        this.geneStore = geneStore;
    }

    public record Input(
            @ToolParam(description = "基因ID") String geneId,
            @ToolParam(description = "true=启用, false=禁用") boolean enabled
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        String geneId = input.geneId();
        boolean enabled = input.enabled();
        log.info("[ToolCall] evolve_gene_toggle - geneId={}, enabled={}", geneId, enabled);
        geneStore.toggleGene(geneId, enabled);
        return ToolResult.success("基因 " + geneId + " 已" + (enabled ? "启用" : "禁用"));
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

        public EvolveGeneToggleTool build() {
            return new EvolveGeneToggleTool(geneStore);
        }
    }
}
