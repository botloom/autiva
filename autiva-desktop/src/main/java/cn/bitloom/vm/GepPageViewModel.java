package cn.bitloom.vm;

import cn.bitloom.agentic.evolve.EvolutionEngine;
import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.experience.Experience;
import cn.bitloom.agentic.evolve.gene.Capsule;
import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneStore;
import cn.bitloom.agentic.evolve.memory.MemoryEngine;
import cn.bitloom.agentic.evolve.memory.MemoryRule;
import cn.bitloom.agentic.evolve.repository.GeneRepository;
import cn.bitloom.agentic.evolve.routing.RoutingEngine;
import cn.bitloom.agentic.evolve.routing.RoutingEntry;
import cn.bitloom.agentic.evolve.solidify.EvolutionEvent;
import cn.bitloom.agentic.evolve.strategy.StrategyPreset;
import cn.bitloom.util.ExecutorManager;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GepPageViewModel {

    private final GeneStore geneStore;
    private final EvolutionEngine evolutionEngine;
    private final EvolveConfig evolveConfig;
    private final RoutingEngine routingEngine;
    private final MemoryEngine memoryEngine;

    @Getter
    private final ObservableList<Gene> genes = FXCollections.observableArrayList();
    @Getter
    private final ObservableList<RoutingEntry> routes = FXCollections.observableArrayList();
    @Getter
    private final ObservableList<MemoryRule> rules = FXCollections.observableArrayList();
    @Getter
    private final ObservableList<Capsule> capsules = FXCollections.observableArrayList();
    @Getter
    private final ObservableList<EvolutionEvent> events = FXCollections.observableArrayList();

    @Getter
    private final IntegerProperty geneCount = new SimpleIntegerProperty(0);
    @Getter
    private final IntegerProperty enabledGeneCount = new SimpleIntegerProperty(0);
    @Getter
    private final IntegerProperty eventCount = new SimpleIntegerProperty(0);
    @Getter
    private final DoubleProperty successRate = new SimpleDoubleProperty(0);
    @Getter
    private final IntegerProperty routeCount = new SimpleIntegerProperty(0);
    @Getter
    private final IntegerProperty ruleCount = new SimpleIntegerProperty(0);
    @Getter
    private final StringProperty strategyPreset = new SimpleStringProperty("BALANCED");

    public void loadData() {
        List<Gene> geneList = geneStore.loadGenes();
        genes.setAll(geneList);

        List<RoutingEntry> routeList = routingEngine.listRoutes();
        routes.setAll(routeList);

        List<MemoryRule> ruleList = memoryEngine.loadAllRules();
        rules.setAll(ruleList);

        List<Capsule> capsuleList = geneStore.loadCapsules();
        capsules.setAll(capsuleList);

        List<EvolutionEvent> eventList = geneStore.readRecentEvents(50);
        events.setAll(eventList);

        updateStats();
    }

    public void loadDataAsync(Runnable onLoaded) {
        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() {
                loadData();
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            if (onLoaded != null) onLoaded.run();
        });
        task.setOnFailed(e -> log.error("[GEP] 加载数据失败", task.getException()));
        ExecutorManager.getPlatformTaskExecutor().execute(task);
    }

    private void updateStats() {
        geneCount.set(genes.size());
        enabledGeneCount.set((int) genes.stream().filter(Gene::enabled).count());
        routeCount.set(routes.size());
        ruleCount.set(rules.size());
        eventCount.set(events.size());

        if (!events.isEmpty()) {
            long successCount = events.stream()
                    .filter(e -> e.outcome() != null && "success".equalsIgnoreCase(e.outcome().status()))
                    .count();
            successRate.set((double) successCount / events.size());
        } else {
            successRate.set(0);
        }

        strategyPreset.set(evolveConfig.getStrategyPreset().name());
    }

    public void toggleGene(String geneId) {
        Gene gene = genes.stream().filter(g -> g.id().equals(geneId)).findFirst().orElse(null);
        if (gene != null) {
            geneStore.toggleGene(geneId, !gene.enabled());
            loadData();
        }
    }

    public void deleteGene(String geneId) {
        geneStore.deleteGene(geneId);
        loadData();
    }

    public void setStrategyPreset(StrategyPreset preset) {
        evolveConfig.setStrategyPreset(preset);
        strategyPreset.set(preset.name());
    }

    public StrategyPreset getStrategyPreset() {
        return evolveConfig.getStrategyPreset();
    }

    public EvolutionEngine.EvolutionCycleResult runEvolutionCycle() {
        EvolutionEngine.EvolutionCycleResult result = evolutionEngine.runCycle(List.of());
        loadData();
        return result;
    }

    public List<Experience> extractAndEvolve() {
        List<Experience> experiences = evolutionEngine.extractAndEvolve();
        loadData();
        return experiences;
    }

    public void addRoute(String pattern, String geneId, double weight) {
        routingEngine.addRoute(pattern, geneId, weight);
        loadData();
    }

    public void removeRoute(String pattern) {
        routingEngine.removeRoute(pattern);
        loadData();
    }

    public void addRule(String pattern, String action, double confidence) {
        memoryEngine.addManualRule(pattern, action, confidence);
        loadData();
    }

    public void deleteRule(String ruleId) {
        memoryEngine.deleteRule(ruleId);
        loadData();
    }

    public void deleteCapsule(String capsuleId) {
        geneStore.deleteCapsule(capsuleId);
        loadData();
    }

    public List<GeneRepository.CommitInfo> getGeneHistory(String geneId) {
        return geneStore.getGeneHistory(geneId);
    }

    public void revertGene(String geneId, String commitHash) {
        geneStore.revertGene(geneId, commitHash);
        loadData();
    }

    public String getGeneCode(String geneId) {
        return geneStore.loadGeneCode(geneId);
    }
}
