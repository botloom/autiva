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
 * 将指定 Gene 回滚到历史版本。
 * <p>
 * 基于 JGit 提交 hash 回滚，回滚后自动生成新版本（version +1），
 * 原历史保留。建议先用 gene_query 查看版本历史，再选择目标 commit。
 */
@Slf4j
public class GeneRollbackTool extends AbstractTool<GeneRollbackTool.Input> {

    private static final String DESCRIPTION = """
            将指定 Gene 回滚到历史版本（基于 JGit 提交 hash）。
            回滚后自动生成新版本（version+1），原历史保留可再次回滚。
            建议先用 gene_query 查看版本历史，再选择目标 commit hash。""";

    private final EvolutionEngine evolutionEngine;

    public GeneRollbackTool(EvolutionEngine evolutionEngine) {
        super("gene_rollback", DESCRIPTION, Input.class);
        this.evolutionEngine = evolutionEngine;
    }

    public record Input(
            @ToolParam(description = "基因ID") String gene_id,
            @ToolParam(description = "目标 JGit 提交 hash（完整或前 7 位）") String commit_hash
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        log.info("[ToolCall] gene_rollback - geneId={} commit={}", input.gene_id(), input.commit_hash());
        if (input.gene_id() == null || input.gene_id().isBlank()) {
            return ToolResult.error("gene_id 不能为空");
        }
        if (input.commit_hash() == null || input.commit_hash().isBlank()) {
            return ToolResult.error("commit_hash 不能为空");
        }
        try {
            String message = evolutionEngine.revertGene(input.gene_id(), input.commit_hash());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("gene_id", input.gene_id());
            data.put("commit_hash", input.commit_hash());
            return ToolResult.success(message, data);
        } catch (Exception e) {
            log.warn("[ToolCall] gene_rollback 失败 geneId={} commit={}",
                    input.gene_id(), input.commit_hash(), e);
            return ToolResult.error("回滚基因失败: " + e.getMessage());
        }
    }
}
