package cn.bitloom.agentic.tool.evolve;

import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneRepository;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 列出所有基因及其状态的工具。
 * <p>
 * 展示基因 id、名称、分类、版本、epigeneticBoost、启用状态及成功/失败计数，
 * 支持按 category 过滤，支持只看启用中的基因。
 */
@Slf4j
public class GeneListTool extends AbstractTool<GeneListTool.Input> {

    private static final String DESCRIPTION = """
            列出所有已注册的进化基因，显示基因 ID、名称、分类、版本、表观遗传增强因子、启用状态及成功/失败计数。
            可选按 category 过滤（STRATEGY/RULE/CONSTRAINT/PROCEDURE），可选只看启用中的基因。
            """;

    private final GeneRepository geneRepository;

    private GeneListTool(GeneRepository geneRepository) {
        super("GeneList", DESCRIPTION, Input.class);
        Assert.notNull(geneRepository, "geneRepository 不能为 null");
        this.geneRepository = geneRepository;
    }

    public record Input(
            @ToolParam(description = "按分类过滤（STRATEGY/RULE/CONSTRAINT/PROCEDURE），留空表示全部",
                    required = false) String category,
            @ToolParam(description = "是否只返回启用中的基因，默认 false（返回全部）",
                    required = false) Boolean enabledOnly
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        log.info("[ToolCall] GeneList - 列出所有基因: category={}, enabledOnly={}",
                input.category(), input.enabledOnly());

        List<Gene> all = geneRepository.findAll();
        if (all.isEmpty()) {
            return ToolResult.success("当前没有任何基因。");
        }

        String categoryFilter = input.category() == null || input.category().isBlank()
                ? null : input.category().toUpperCase().trim();
        boolean enabledOnly = Boolean.TRUE.equals(input.enabledOnly());

        List<Gene> filtered = new ArrayList<>();
        for (Gene gene : all) {
            if (categoryFilter != null && !categoryFilter.equals(gene.category().name())) {
                continue;
            }
            if (enabledOnly && !gene.enabled()) {
                continue;
            }
            filtered.add(gene);
        }

        if (filtered.isEmpty()) {
            return ToolResult.success("没有匹配的基因（category=" + categoryFilter + ", enabledOnly=" + enabledOnly + "）。");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("基因列表（共 ").append(filtered.size()).append(" 个）：\n\n");
        sb.append("| Gene ID | 名称 | 分类 | 版本 | Boost | 启用 | 成功/失败 | 最后验证 |\n");
        sb.append("|---------|------|------|------|-------|------|-----------|----------|\n");

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Gene gene : filtered) {
            sb.append(String.format("| %s | %s | %s | v%d | %.2f | %s | %d/%d | %s |%n",
                    gene.id(),
                    nullToEmpty(gene.name()),
                    gene.category(),
                    gene.version(),
                    gene.epigeneticBoost(),
                    gene.enabled() ? "是" : "否",
                    gene.successCount(),
                    gene.failureCount(),
                    nullToEmpty(gene.lastVerifiedAt())));

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("geneId", gene.id());
            row.put("name", gene.name());
            row.put("category", gene.category().name());
            row.put("version", gene.version());
            row.put("epigeneticBoost", gene.epigeneticBoost());
            row.put("enabled", gene.enabled());
            row.put("successCount", gene.successCount());
            row.put("failureCount", gene.failureCount());
            row.put("lastVerifiedAt", gene.lastVerifiedAt());
            rows.add(row);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", filtered.size());
        data.put("total", all.size());
        data.put("genes", rows);

        return ToolResult.builder()
                .status(ToolResult.Status.SUCCESS)
                .message("共 " + filtered.size() + " 个基因（总计 " + all.size() + " 个）")
                .data(data)
                .rawOutput(sb.toString())
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

        public GeneListTool build() {
            return new GeneListTool(this.geneRepository);
        }
    }
}
