package cn.bitloom.agentic.tool.manage.evolve;

import cn.bitloom.agentic.evolve.EvolutionEngine;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 启用或禁用指定 Gene。
 * <p>
 * 禁用后 Gene 不再注入到 SystemMessage / 工具描述 / Grader Rubric，
 * 但版本历史与表观遗传值保留，可随时重新启用。
 */
@Slf4j
public class GeneToggleTool extends AbstractTool<GeneToggleTool.Input> {

    private static final String DESCRIPTION = """
            启用或禁用指定 Gene（L4 配置单元）。
            禁用后该 Gene 不再注入到 Agent Prompt / 工具描述 / Grader 校验规则 / 技能配置。
            版本历史保留，可随时重新启用。""";

    private final EvolutionEngine evolutionEngine;

    public GeneToggleTool(EvolutionEngine evolutionEngine) {
        super("gene_toggle", DESCRIPTION, Input.class);
        this.evolutionEngine = evolutionEngine;
    }

    public record Input(
            @ToolParam(description = "基因ID") String gene_id,
            @ToolParam(description = "true=启用，false=禁用") boolean enabled
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        log.info("[ToolCall] gene_toggle - geneId={} enabled={}", input.gene_id(), input.enabled());
        if (input.gene_id() == null || input.gene_id().isBlank()) {
            return ToolResult.error("gene_id 不能为空");
        }
        try {
            String message = evolutionEngine.toggleGene(input.gene_id(), input.enabled());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("gene_id", input.gene_id());
            data.put("enabled", input.enabled());
            return ToolResult.success(message, data);
        } catch (Exception e) {
            log.warn("[ToolCall] gene_toggle 失败 geneId={}", input.gene_id(), e);
            return ToolResult.error("切换基因状态失败: " + e.getMessage());
        }
    }
}
