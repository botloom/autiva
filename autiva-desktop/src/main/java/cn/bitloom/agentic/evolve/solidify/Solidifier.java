package cn.bitloom.agentic.evolve.solidify;

import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 固化器。
 * <p>
 * 根据 L2 校验结果更新 Gene 的表观遗传值：
 * - 校验通过率提升 → epigeneticBoost 增强
 * - 校验通过率下降 → epigeneticBoost 衰减
 */
@Slf4j
@Component
public class Solidifier {

    private final GeneStore geneStore;
    private final EvolveConfig config;

    public Solidifier(GeneStore geneStore, EvolveConfig config) {
        this.geneStore = geneStore;
        this.config = config;
    }

    public SolidifyResult solidify(EvolutionEvent event) {
        geneStore.appendEvent(event);

        if (event.geneId() == null) {
            return new SolidifyResult(false, "事件无关联基因");
        }

        if (event.isSuccess()) {
            updateEpigeneticScore(event.geneId(), true);
            log.info("[Evolve] 固化成功: 事件={}, 基因={}", event.id(), event.geneId());
            return new SolidifyResult(true, "固化成功");
        } else {
            updateEpigeneticScore(event.geneId(), false);
            return new SolidifyResult(false, "事件未成功，已衰减表观遗传值");
        }
    }

    private void updateEpigeneticScore(String geneId, boolean success) {
        Gene gene = geneStore.findById(geneId);
        if (gene == null) {
            return;
        }

        double newBoost;
        if (success) {
            newBoost = gene.epigeneticBoost() * config.getEpigeneticBoostOnSuccess();
            newBoost = Math.min(newBoost, 5.0);
        } else {
            newBoost = gene.epigeneticBoost() * config.getEpigeneticDecay();
            newBoost = Math.max(newBoost, 0.1);
        }

        geneStore.upsertGene(gene.withEpigeneticBoost(newBoost));
        log.info("[Evolve] 表观遗传值更新: {} -> {} ({})", geneId, String.format("%.2f", newBoost), success ? "增强" : "衰减");
    }

    public record SolidifyResult(boolean success, String message) {
    }
}
