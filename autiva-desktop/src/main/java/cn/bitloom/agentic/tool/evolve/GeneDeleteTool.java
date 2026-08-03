package cn.bitloom.agentic.tool.evolve;

import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneRepository;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 删除基因的工具。
 * <p>
 * 删除会移除基因目录及所有历史版本，操作不可逆。
 * 为保护稳定基因，successCount + failureCount ≥ 100 的基因不允许直接删除，
 * 必须先通过 GeneActivate 禁用，再由定时清理任务处理。
 */
@Slf4j
public class GeneDeleteTool extends AbstractTool<GeneDeleteTool.Input> {

    /** 稳定基因保护阈值 */
    private static final int STABLE_GENE_THRESHOLD = 100;

    private static final String DESCRIPTION = """
            删除指定基因。操作不可逆，会移除基因目录及所有历史版本。
            为保护稳定基因，调用次数 ≥ 100 的基因不允许直接删除，请先通过 GeneActivate 禁用。
            """;

    private final GeneRepository geneRepository;

    private GeneDeleteTool(GeneRepository geneRepository) {
        super("GeneDelete", DESCRIPTION, Input.class);
        Assert.notNull(geneRepository, "geneRepository 不能为 null");
        this.geneRepository = geneRepository;
    }

    public record Input(
            @ToolParam(description = "基因 ID（gene-xxx）") String geneId,
            @ToolParam(description = "确认删除（必须为 true 才会执行）") Boolean confirm
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        log.info("[ToolCall] GeneDelete - 删除基因: geneId={}", input.geneId());

        if (input.geneId() == null || input.geneId().isBlank()) {
            return ToolResult.error("geneId 不能为空");
        }
        if (!Boolean.TRUE.equals(input.confirm())) {
            return ToolResult.error("删除基因是不可逆操作，请显式传 confirm=true 确认。");
        }

        Gene existing = geneRepository.findById(input.geneId().trim());
        if (existing == null) {
            return ToolResult.error("基因不存在: " + input.geneId());
        }

        // 保护稳定基因
        if (existing.successCount() + existing.failureCount() >= STABLE_GENE_THRESHOLD) {
            return ToolResult.error("基因 " + input.geneId() + " 已是稳定基因（调用次数 ≥ "
                    + STABLE_GENE_THRESHOLD + "），不允许直接删除。请先通过 GeneActivate 禁用。");
        }

        geneRepository.delete(existing.id());

        log.info("[ToolCall] GeneDelete - 基因删除成功: geneId={}", existing.id());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("geneId", existing.id());
        data.put("name", existing.name());

        String rawOutput = "基因删除成功\n\n" +
                "- Gene ID: " + existing.id() + "\n" +
                "- 名称: " + nullToEmpty(existing.name()) + "\n";

        return ToolResult.builder()
                .status(ToolResult.Status.SUCCESS)
                .message("基因已删除: " + existing.id())
                .data(data)
                .rawOutput(rawOutput)
                .build();
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private GeneRepository geneRepository;

        private Builder() {}

        public Builder geneRepository(GeneRepository geneRepository) {
            this.geneRepository = geneRepository;
            return this;
        }

        public GeneDeleteTool build() {
            return new GeneDeleteTool(this.geneRepository);
        }
    }
}
