package cn.bitloom.agentic.tool.manage.evolve;

import cn.bitloom.agentic.evolve.gene.GeneStore;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

/**
 * 删除指定胶囊（破坏性操作，不可恢复）。
 */
@Slf4j
public class EvolveCapsuleDeleteTool extends AbstractTool<EvolveCapsuleDeleteTool.Input> {

    private static final String DESCRIPTION = "删除指定胶囊（破坏性操作，不可恢复）";

    private final GeneStore geneStore;

    private EvolveCapsuleDeleteTool(GeneStore geneStore) {
        super("evolve_capsule_delete", DESCRIPTION, Input.class);
        Assert.notNull(geneStore, "geneStore不能为null");
        this.geneStore = geneStore;
    }

    public record Input(
            @ToolParam(description = "要删除的胶囊ID") String capsuleId
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        String capsuleId = input.capsuleId();
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

        public EvolveCapsuleDeleteTool build() {
            return new EvolveCapsuleDeleteTool(geneStore);
        }
    }
}
