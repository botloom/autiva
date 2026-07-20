package cn.bitloom.agentic.tool.manage.evolve;

import cn.bitloom.agentic.evolve.EvolutionEngine;
import cn.bitloom.agentic.evolve.gene.GeneType;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 查询 Gene 列表或详情。
 * <p>
 * - 传 gene_id 时返回单条详情；<br>
 * - 传 type 时返回该类型的列表；<br>
 * - 两者都缺省时返回全部启用基因摘要。
 */
@Slf4j
public class GeneQueryTool extends AbstractTool<GeneQueryTool.Input> {

    private static final String DESCRIPTION = """
            查询 Gene（L4 配置单元）列表或详情。
            Gene 类型：PROMPT（Agent 提示词片段）/ TOOL_DESC（工具描述）/ RUBRIC（L2 评分规则）/ SKILL_CONFIG（技能配置）。
            传 gene_id 返回单条详情；传 type 返回该类型的列表；都缺省返回全部启用基因。""";

    private final EvolutionEngine evolutionEngine;

    public GeneQueryTool(EvolutionEngine evolutionEngine) {
        super("gene_query", DESCRIPTION, Input.class);
        this.evolutionEngine = evolutionEngine;
    }

    public record Input(
            @ToolParam(description = "基因ID，可选。指定时返回单条详情",
                    required = false) String gene_id,
            @ToolParam(description = "基因类型筛选，可选。PROMPT/TOOL_DESC/RUBRIC/SKILL_CONFIG",
                    required = false) String type
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        log.info("[ToolCall] gene_query - geneId={} type={}", input.gene_id(), input.type());
        try {
            if (input.gene_id() != null && !input.gene_id().isBlank()) {
                String detail = evolutionEngine.queryGeneDetail(input.gene_id());
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("gene_id", input.gene_id());
                return ToolResult.success("基因详情: " + input.gene_id(), data, detail);
            }
            GeneType filter = parseType(input.type());
            String summary = evolutionEngine.queryGenes(filter);
            Map<String, Object> data = new LinkedHashMap<>();
            if (filter != null) data.put("type", filter.name());
            return ToolResult.success(filter == null ? "全部启用基因" : filter.name() + " 类基因",
                    data, summary);
        } catch (Exception e) {
            log.warn("[ToolCall] gene_query 失败", e);
            return ToolResult.error("查询基因失败: " + e.getMessage());
        }
    }

    private GeneType parseType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return GeneType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
