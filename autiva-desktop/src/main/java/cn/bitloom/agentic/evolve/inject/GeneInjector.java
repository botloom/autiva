package cn.bitloom.agentic.evolve.inject;

import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneStore;
import cn.bitloom.agentic.evolve.gene.GeneType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Gene 配置注入器。
 *
 * <p>从 GeneStore 加载 PROMPT 类型的 Gene，按 targetId=agentId 过滤，
 * 将启用的 Gene 内容拼装为可注入到 SystemMessage 的文本片段。</p>
 *
 * <p>注入策略：</p>
 * <ul>
 *   <li>PROMPT Gene：追加到 SystemMessage 末尾（由 {@code GeneInjectAdvisor} 调用）</li>
 *   <li>TOOL_DESC Gene：在 Agent 构建时注入到工具描述（暂未实现，留待后续阶段）</li>
 *   <li>RUBRIC Gene：在 {@code LlmGrader} 构建时加载（已由 Phase 2 实现）</li>
 *   <li>SKILL_CONFIG Gene：覆盖技能列表（暂未实现）</li>
 * </ul>
 *
 * <p>epigeneticBoost 高的 Gene 优先注入（L4 优化效果好的 Gene 优先应用）。</p>
 */
@Slf4j
@Component
public class GeneInjector {

    private final GeneStore geneStore;

    public GeneInjector(GeneStore geneStore) {
        this.geneStore = geneStore;
    }

    /**
     * 构建指定 Agent 的 PROMPT Gene 注入文本。
     *
     * @param agentId 目标 Agent ID
     * @return 注入文本；无可用 Gene 时返回 null
     */
    public String buildPromptInjection(String agentId) {
        if (agentId == null || agentId.isEmpty()) {
            return null;
        }
        try {
            List<Gene> promptGenes = geneStore.findByTypeAndTarget(GeneType.PROMPT, agentId)
                    .stream()
                    .filter(Gene::enabled)
                    .sorted((a, b) -> Double.compare(b.epigeneticBoost(), a.epigeneticBoost()))
                    .toList();

            if (promptGenes.isEmpty()) {
                return null;
            }

            String geneText = promptGenes.stream()
                    .map(g -> "### " + g.name() + "\n" + g.content())
                    .collect(Collectors.joining("\n\n"));

            return "<genes>\n" + geneText + "\n</genes>";
        } catch (Exception e) {
            log.warn("[GeneInjector] 构建 PROMPT 注入失败 agentId={}: {}", agentId, e.getMessage());
            return null;
        }
    }

    /**
     * 加载指定 Agent 的所有启用 Gene（供 UI 展示和调试）。
     */
    public List<Gene> loadActiveGenes(String agentId) {
        if (agentId == null) return List.of();
        return geneStore.findByTarget(agentId).stream()
                .filter(Gene::enabled)
                .sorted((a, b) -> Double.compare(b.epigeneticBoost(), a.epigeneticBoost()))
                .toList();
    }
}
