package cn.bitloom.agentic.tool.manage.evolve;

import cn.bitloom.agentic.evolve.gene.GeneStore;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

/**
 * 删除指定基因（破坏性操作，不可恢复）。
 */
@Slf4j
public class EvolveGeneDeleteTool extends AbstractTool<EvolveGeneDeleteTool.Input> {

    private static final String DESCRIPTION = "删除指定基因（破坏性操作，不可恢复）";

    private final GeneStore geneStore;

    private EvolveGeneDeleteTool(GeneStore geneStore) {
        super("evolve_gene_delete", DESCRIPTION, Input.class);
        Assert.notNull(geneStore, "geneStore不能为null");
        this.geneStore = geneStore;
    }

    public record Input(
            @ToolParam(description = "要删除的基因ID") String geneId
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        String geneId = input.geneId();
        log.info("[ToolCall] evolve_gene_delete - geneId={}", geneId);
        geneStore.deleteGene(geneId);
        return ToolResult.success("基因 " + geneId + " 已删除");
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

        public EvolveGeneDeleteTool build() {
            return new EvolveGeneDeleteTool(geneStore);
        }
    }
}
