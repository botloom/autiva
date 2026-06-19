package cn.bitloom.agentic.evolve;

import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.gene.*;
import cn.bitloom.agentic.evolve.prompt.EvolvePromptAssembler;
import cn.bitloom.agentic.evolve.signal.Signal;
import cn.bitloom.agentic.evolve.signal.SignalExtractor;
import cn.bitloom.agentic.evolve.signal.SignalHistory;
import cn.bitloom.agentic.evolve.solidify.EvolutionEvent;
import cn.bitloom.agentic.evolve.solidify.Solidifier;
import cn.bitloom.agentic.evolve.strategy.StrategyEngine;
import cn.bitloom.agentic.evolve.strategy.StrategyPreset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EvolutionEngine {

    private final EvolveConfig config;
    private final GeneStore geneStore;
    private final SignalExtractor signalExtractor;
    private final SignalHistory signalHistory;
    private final GeneSelector geneSelector;
    private final StrategyEngine strategyEngine;
    private final EvolvePromptAssembler promptAssembler;
    private final Solidifier solidifier;

    public EvolutionEngine(EvolveConfig config, GeneStore geneStore, Solidifier solidifier) {
        this.config = config;
        this.geneStore = geneStore;
        this.signalExtractor = new SignalExtractor();
        this.signalHistory = new SignalHistory(config);
        this.geneSelector = new GeneSelector(config);
        this.strategyEngine = new StrategyEngine(config);
        this.promptAssembler = new EvolvePromptAssembler(config);
        this.solidifier = solidifier;
    }

    public EvolutionCycleResult runCycle(List<String> conversationTexts) {
        log.info("[Evolve] 开始进化周期");

        List<Signal> signals = signalExtractor.extract(conversationTexts);
        if (signals.isEmpty()) {
            log.info("[Evolve] 未检测到信号，跳过进化周期");
            return EvolutionCycleResult.empty("未检测到信号");
        }

        log.info("[Evolve] 检测到 {} 个信号: {}", signals.size(),
                signals.stream().map(s -> s.type().code()).collect(Collectors.joining(", ")));

        List<EvolutionEvent> recentEvents = geneStore.readRecentEvents(config.getRecentEventsLimit());
        SignalHistory.HistoryAnalysis analysis = signalHistory.analyze(recentEvents);

        StrategyPreset preset = strategyEngine.resolve(analysis);

        List<Gene> genes = geneStore.loadEnabledGenes();
        GeneSelector.GeneSelectionResult selection = geneSelector.select(signals, genes, preset, analysis);

        if (!selection.hasGene()) {
            log.info("[Evolve] 未找到匹配的基因: {}", selection.reason());
            return EvolutionCycleResult.empty(selection.reason());
        }

        Gene selectedGene = selection.gene();
        String prompt = promptAssembler.assemble(signals, selectedGene, preset, selection.reason());

        log.info("[Evolve] 进化周期完成: 选中基因={}, 策略={}, 理由={}",
                selectedGene.id(), preset, selection.reason());

        return new EvolutionCycleResult(
                true,
                signals,
                selectedGene,
                preset,
                prompt,
                selection.reason(),
                selection.score()
        );
    }

    public String getEvolutionContext(List<String> conversationTexts) {
        EvolutionCycleResult result = runCycle(conversationTexts);
        if (!result.success()) {
            return "";
        }
        return result.prompt();
    }

    public Solidifier.SolidifyResult solidify(EvolutionEvent event) {
        return solidifier.solidify(event);
    }

    public EvolutionEvent createEvent(List<Signal> signals, Gene gene, String intent,
                                       String prompt, EvolutionEvent.Outcome outcome) {
        return new EvolutionEvent(
                "evt_" + UUID.randomUUID().toString().substring(0, 8),
                System.currentTimeMillis(),
                signals.stream().map(s -> s.type().code()).toList(),
                gene.id(),
                intent,
                prompt,
                outcome,
                Map.of("strategy", config.getStrategyPreset().name())
        );
    }

    public String queryGenes(GeneCategory category) {
        List<Gene> genes = geneStore.loadEnabledGenes();
        if (category != null) {
            genes = genes.stream().filter(g -> g.category() == category).toList();
        }
        if (genes.isEmpty()) {
            return "没有找到" + (category != null ? category.code() + "类" : "") + "基因";
        }
        return promptAssembler.assembleGeneSummary(genes);
    }

    public String queryGeneDetail(String geneId) {
        List<Gene> genes = geneStore.loadGenes();
        return genes.stream()
                .filter(g -> g.id().equals(geneId))
                .findFirst()
                .map(promptAssembler::assembleGeneDetail)
                .orElse("基因不存在: " + geneId);
    }

    public String queryCapsules() {
        List<Capsule> capsules = geneStore.loadCapsules();
        if (capsules.isEmpty()) {
            return "没有可用的胶囊";
        }
        return capsules.stream()
                .map(c -> String.format("- [%s] 分数=%.2f, 基因=%s",
                        c.id(), c.score(), String.join(",", c.geneIds())))
                .collect(Collectors.joining("\n"));
    }

    public String queryEvents(int limit) {
        List<EvolutionEvent> events = geneStore.readRecentEvents(limit);
        if (events.isEmpty()) {
            return "没有进化事件记录";
        }
        return events.stream()
                .map(e -> String.format("- [%s] %s %s (基因=%s, 分数=%.2f)",
                        e.id(),
                        e.outcome() != null ? e.outcome().status() : "unknown",
                        e.intent() != null ? e.intent() : "",
                        e.geneId() != null ? e.geneId() : "none",
                        e.outcome() != null ? e.outcome().score() : 0))
                .collect(Collectors.joining("\n"));
    }

    public String recommendGenes(List<String> conversationTexts) {
        EvolutionCycleResult result = runCycle(conversationTexts);
        if (!result.success()) {
            return "当前没有推荐的基因: " + result.reason();
        }
        return String.format("推荐基因: %s (%s)\n类别: %s\n理由: %s\n置信度: %.2f\n\n%s",
                result.gene().id(),
                result.gene().summary(),
                result.gene().category().code(),
                result.reason(),
                result.score(),
                promptAssembler.assembleGeneDetail(result.gene()));
    }

    public String applyGene(String geneId, String context) {
        List<Gene> genes = geneStore.loadGenes();
        Gene gene = genes.stream()
                .filter(g -> g.id().equals(geneId))
                .findFirst()
                .orElse(null);

        if (gene == null) {
            return "基因不存在: " + geneId;
        }

        if (!gene.enabled()) {
            return "基因已禁用: " + geneId;
        }

        return promptAssembler.assembleGeneDetail(gene);
    }

    public String applyCapsule(String capsuleId, String context) {
        List<Capsule> capsules = geneStore.loadCapsules();
        Capsule capsule = capsules.stream()
                .filter(c -> c.id().equals(capsuleId))
                .findFirst()
                .orElse(null);

        if (capsule == null) {
            return "胶囊不存在: " + capsuleId;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 胶囊: ").append(capsuleId).append("\n");
        sb.append("- 分数: ").append(String.format("%.2f", capsule.score())).append("\n");
        sb.append("- 包含基因: ").append(String.join(", ", capsule.geneIds())).append("\n\n");

        List<Gene> genes = geneStore.loadGenes();
        for (String geneId : capsule.geneIds()) {
            genes.stream()
                    .filter(g -> g.id().equals(geneId))
                    .findFirst()
                    .ifPresent(g -> sb.append(promptAssembler.assembleGeneDetail(g)).append("\n"));
        }

        return sb.toString();
    }

    public record EvolutionCycleResult(
            boolean success,
            List<Signal> signals,
            Gene gene,
            StrategyPreset preset,
            String prompt,
            String reason,
            double score
    ) {
        public static EvolutionCycleResult empty(String reason) {
            return new EvolutionCycleResult(false, Collections.emptyList(), null, null, "", reason, 0);
        }
    }
}
