package cn.bitloom.agentic.evolve.solidify;

import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.gene.Capsule;
import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class Solidifier {

    private final GeneStore geneStore;
    private final EvolveConfig config;
    private final CanaryCheck canaryCheck;

    public Solidifier(GeneStore geneStore, EvolveConfig config) {
        this.geneStore = geneStore;
        this.config = config;
        this.canaryCheck = new CanaryCheck();
    }

    public SolidifyResult solidify(EvolutionEvent event) {
        CanaryCheck.CanaryResult canary = canaryCheck.check();
        if (!canary.passed()) {
            log.warn("[Evolve] 固化失败: 金丝雀检查未通过 - {}", canary.message());
            return new SolidifyResult(false, "金丝雀检查未通过: " + canary.message());
        }

        if (!event.isSuccess()) {
            geneStore.appendEvent(event);
            updateEpigeneticScore(event.geneId(), false);
            return new SolidifyResult(false, "事件未成功，不进行固化");
        }

        geneStore.appendEvent(event);
        updateEpigeneticScore(event.geneId(), true);

        List<EvolutionEvent> recentEvents = geneStore.readRecentEvents(5);
        long recentSuccesses = recentEvents.stream()
                .filter(EvolutionEvent::isSuccess)
                .filter(e -> e.geneId() != null && e.geneId().equals(event.geneId()))
                .count();

        if (recentSuccesses >= 3) {
            createCapsule(event);
        }

        log.info("[Evolve] 固化成功: 事件={}, 基因={}", event.id(), event.geneId());
        return new SolidifyResult(true, "固化成功");
    }

    private void updateEpigeneticScore(String geneId, boolean success) {
        if (geneId == null) return;

        List<Gene> genes = geneStore.loadGenes();
        for (Gene gene : genes) {
            if (gene.id().equals(geneId)) {
                double newBoost;
                if (success) {
                    newBoost = gene.epigeneticBoost() * config.getEpigeneticBoostOnSuccess();
                    newBoost = Math.min(newBoost, 5.0);
                } else {
                    newBoost = gene.epigeneticBoost() * config.getEpigeneticDecay();
                    newBoost = Math.max(newBoost, 0.1);
                }
                geneStore.upsertGene(gene.withEpigeneticBoost(newBoost));
                break;
            }
        }
    }

    private void createCapsule(EvolutionEvent event) {
        String capsuleId = "caps_" + UUID.randomUUID().toString().substring(0, 8);
        Capsule capsule = new Capsule(
                capsuleId,
                List.of(event.geneId()),
                Map.of(
                        "intent", event.intent() != null ? event.intent() : "",
                        "source", "auto_solidify"
                ),
                event.outcome() != null ? event.outcome().score() : 0.5,
                System.currentTimeMillis()
        );

        List<Capsule> existing = geneStore.loadCapsules();
        List<Capsule> updated = new ArrayList<>(existing);
        updated.add(capsule);
        log.info("[Evolve] 自动创建胶囊: {}", capsuleId);
    }

    public record SolidifyResult(boolean success, String message) {
    }
}
