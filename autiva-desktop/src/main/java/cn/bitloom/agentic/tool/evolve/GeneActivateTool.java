package cn.bitloom.agentic.tool.evolve;

import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneRepository;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 激活/禁用基因的工具。
 * <p>
 * 通过修改 frontmatter 的 enabled 字段切换基因是否参与选择。
 * 禁用的基因不会被 GeneSelector 选中，但保留历史版本与统计信息，可随时重新启用。
 * <p>
 * 安全保护：epigeneticBoost < 0.5 的基因会自动判定为应禁用状态，
 * 但本工具允许手动重新启用以便人工干预（不强制阻止）。
 */
@Slf4j
public class GeneActivateTool extends AbstractTool<GeneActivateTool.Input> {

    private static final String DESCRIPTION = """
            激活或禁用指定基因。禁用的基因不会参与选择，但保留所有历史记录。
            可用于人工干预：暂时下线表现不佳的基因，或重新启用已修复的基因。
            """;

    private final GeneRepository geneRepository;

    private GeneActivateTool(GeneRepository geneRepository) {
        super("GeneActivate", DESCRIPTION, Input.class);
        Assert.notNull(geneRepository, "geneRepository 不能为 null");
        this.geneRepository = geneRepository;
    }

    public record Input(
            @ToolParam(description = "基因 ID（gene-xxx）") String geneId,
            @ToolParam(description = "目标启用状态：true=激活, false=禁用") Boolean enabled
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        log.info("[ToolCall] GeneActivate - 切换基因状态: geneId={}, enabled={}", input.geneId(), input.enabled());

        if (input.geneId() == null || input.geneId().isBlank()) {
            return ToolResult.error("geneId 不能为空");
        }
        if (input.enabled() == null) {
            return ToolResult.error("enabled 不能为空（true=激活, false=禁用）");
        }

        Gene existing = geneRepository.findById(input.geneId().trim());
        if (existing == null) {
            return ToolResult.error("基因不存在: " + input.geneId());
        }

        boolean oldEnabled = existing.enabled();
        boolean newEnabled = input.enabled();

        if (oldEnabled == newEnabled) {
            return ToolResult.builder()
                    .status(ToolResult.Status.WARNING)
                    .message("基因已是目标状态: enabled=" + newEnabled)
                    .rawOutput("基因 " + existing.id() + " 当前 enabled=" + newEnabled + "，无需切换。\n")
                    .build();
        }

        // 安全提示：启用 boost 过低的基因
        String warning = "";
        if (newEnabled && existing.epigeneticBoost() < 0.5) {
            warning = "⚠️ 警告：该基因的 epigeneticBoost=" + String.format("%.4f", existing.epigeneticBoost())
                    + " 低于安全阈值 0.5，启用后可能影响进化质量。建议先通过进化周期自动调整。\n\n";
            log.warn("[GeneActivate] 启用低 boost 基因: geneId={}, boost={}",
                    existing.id(), existing.epigeneticBoost());
        }

        Map<String, Object> frontmatter = new LinkedHashMap<>(existing.frontmatter());
        frontmatter.put("enabled", newEnabled);
        frontmatter.put("lastVerifiedAt", Instant.now().toString());

        Gene updated = new Gene(existing.id(), existing.basePath(), frontmatter, existing.content());
        geneRepository.save(updated);

        log.info("[ToolCall] GeneActivate - 基因状态切换成功: geneId={}, enabled={} -> {}",
                existing.id(), oldEnabled, newEnabled);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("geneId", existing.id());
        data.put("name", existing.name());
        data.put("oldEnabled", oldEnabled);
        data.put("newEnabled", newEnabled);
        data.put("epigeneticBoost", existing.epigeneticBoost());

        String rawOutput = warning + "基因状态切换成功\n\n" +
                "- Gene ID: " + existing.id() + "\n" +
                "- 名称: " + nullToEmpty(existing.name()) + "\n" +
                "- 状态: " + (oldEnabled ? "启用" : "禁用") + " -> " + (newEnabled ? "启用" : "禁用") + "\n" +
                "- epigeneticBoost: " + String.format("%.4f", existing.epigeneticBoost()) + "\n";

        ToolResult.Status status = warning.isEmpty()
                ? ToolResult.Status.SUCCESS : ToolResult.Status.WARNING;

        return ToolResult.builder()
                .status(status)
                .message("基因已" + (newEnabled ? "激活" : "禁用") + ": " + existing.id())
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

        public GeneActivateTool build() {
            return new GeneActivateTool(this.geneRepository);
        }
    }
}
