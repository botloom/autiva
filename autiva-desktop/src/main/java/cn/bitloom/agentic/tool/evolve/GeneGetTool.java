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
import java.util.List;
import java.util.Map;

/**
 * 获取单个基因详情的工具。
 * <p>
 * 支持按 geneId 或 name 查询，返回完整 frontmatter、content 及历史版本列表。
 */
@Slf4j
public class GeneGetTool extends AbstractTool<GeneGetTool.Input> {

    private static final String DESCRIPTION = """
            获取指定基因的完整详情，包括 frontmatter（结构化控制信号）、content（LLM 指令）和历史版本列表。
            必须提供 geneId 或 name 之一。
            """;

    private final GeneRepository geneRepository;

    private GeneGetTool(GeneRepository geneRepository) {
        super("GeneGet", DESCRIPTION, Input.class);
        Assert.notNull(geneRepository, "geneRepository 不能为 null");
        this.geneRepository = geneRepository;
    }

    public record Input(
            @ToolParam(description = "基因 ID（gene-xxx）", required = false) String geneId,
            @ToolParam(description = "基因名称（与 geneId 二选一）", required = false) String name
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        log.info("[ToolCall] GeneGet - 获取基因详情: geneId={}, name={}", input.geneId(), input.name());

        Gene gene = resolveGene(input);
        if (gene == null) {
            return ToolResult.error("基因不存在: geneId=" + input.geneId() + ", name=" + input.name());
        }

        List<Integer> versions = geneRepository.getVersions(gene.id());

        StringBuilder sb = new StringBuilder();
        sb.append("# 基因详情\n\n");
        sb.append("- **Gene ID**: ").append(gene.id()).append("\n");
        sb.append("- **名称**: ").append(nullToEmpty(gene.name())).append("\n");
        sb.append("- **描述**: ").append(nullToEmpty(gene.description())).append("\n");
        sb.append("- **分类**: ").append(gene.category()).append("\n");
        sb.append("- **版本**: v").append(gene.version()).append("\n");
        sb.append("- **表观遗传增强因子**: ").append(String.format("%.4f", gene.epigeneticBoost())).append("\n");
        sb.append("- **启用状态**: ").append(gene.enabled() ? "启用" : "禁用").append("\n");
        sb.append("- **成功计数**: ").append(gene.successCount()).append("\n");
        sb.append("- **失败计数**: ").append(gene.failureCount()).append("\n");
        sb.append("- **最后验证时间**: ").append(nullToEmpty(gene.lastVerifiedAt())).append("\n");
        sb.append("- **路径**: ").append(nullToEmpty(gene.basePath())).append("\n\n");

        sb.append("## signalsMatch\n");
        Map<String, Object> signals = gene.signalsMatch();
        if (signals.isEmpty()) {
            sb.append("(空)\n");
        } else {
            for (Map.Entry<String, Object> entry : signals.entrySet()) {
                sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        sb.append("\n");

        sb.append("## constraints\n");
        List<String> constraints = gene.constraints();
        if (constraints.isEmpty()) {
            sb.append("(空)\n");
        } else {
            for (String c : constraints) {
                sb.append("- ").append(c).append("\n");
            }
        }
        sb.append("\n");

        sb.append("## content（LLM 指令）\n");
        sb.append(nullToEmpty(gene.content())).append("\n\n");

        sb.append("## 历史版本\n");
        if (versions.isEmpty()) {
            sb.append("(无历史版本)\n");
        } else {
            for (Integer v : versions) {
                sb.append("- v").append(v).append("\n");
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("geneId", gene.id());
        data.put("name", gene.name());
        data.put("category", gene.category().name());
        data.put("version", gene.version());
        data.put("epigeneticBoost", gene.epigeneticBoost());
        data.put("enabled", gene.enabled());
        data.put("successCount", gene.successCount());
        data.put("failureCount", gene.failureCount());
        data.put("lastVerifiedAt", gene.lastVerifiedAt());
        data.put("signalsMatch", signals);
        data.put("constraints", constraints);
        data.put("versions", versions);

        return ToolResult.builder()
                .status(ToolResult.Status.SUCCESS)
                .message("基因详情: " + gene.id())
                .data(data)
                .rawOutput(sb.toString())
                .build();
    }

    private Gene resolveGene(Input input) {
        if (input.geneId() != null && !input.geneId().isBlank()) {
            return geneRepository.findById(input.geneId().trim());
        }
        if (input.name() != null && !input.name().isBlank()) {
            return geneRepository.findByName(input.name().trim());
        }
        return null;
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

        public GeneGetTool build() {
            return new GeneGetTool(this.geneRepository);
        }
    }
}
