package cn.bitloom.vm;

import cn.bitloom.agentic.evolve.EvolutionEngine;
import cn.bitloom.agentic.evolve.climb.ClimbingResult;
import cn.bitloom.agentic.evolve.climb.OptimizationSuggestion;
import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneStore;
import cn.bitloom.agentic.evolve.gene.GeneType;
import cn.bitloom.agentic.evolve.repository.GeneRepository;
import cn.bitloom.agentic.evolve.solidify.EvolutionEvent;
import cn.bitloom.agentic.trace.Trace;
import cn.bitloom.agentic.trace.TraceRecorder;
import cn.bitloom.util.ExecutorManager;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GepPageViewModel {

    /**
     * 默认分析的 Agent ID。GEP 页面不区分 Agent，统一分析默认智能体。
     */
    private static final String DEFAULT_AGENT_ID = "default";

    /**
     * 加载最近 Trace 的条数。
     */
    private static final int RECENT_TRACE_LIMIT = 20;

    private final GeneStore geneStore;
    private final EvolutionEngine evolutionEngine;
    private final EvolveConfig evolveConfig;
    private final TraceRecorder traceRecorder;

    @Getter
    private final ObservableList<Gene> genes = FXCollections.observableArrayList();
    @Getter
    private final ObservableList<EvolutionEvent> events = FXCollections.observableArrayList();
    @Getter
    private final ObservableList<Trace> recentTraces = FXCollections.observableArrayList();

    @Getter
    private final IntegerProperty geneCount = new SimpleIntegerProperty(0);
    @Getter
    private final IntegerProperty enabledGeneCount = new SimpleIntegerProperty(0);
    @Getter
    private final IntegerProperty eventCount = new SimpleIntegerProperty(0);
    @Getter
    private final DoubleProperty successRate = new SimpleDoubleProperty(0);
    @Getter
    private final IntegerProperty promptGeneCount = new SimpleIntegerProperty(0);
    @Getter
    private final IntegerProperty rubricGeneCount = new SimpleIntegerProperty(0);
    @Getter
    private final IntegerProperty toolDescGeneCount = new SimpleIntegerProperty(0);
    @Getter
    private final IntegerProperty skillConfigGeneCount = new SimpleIntegerProperty(0);

    // L2 校验统计
    @Getter
    private final IntegerProperty totalTraces = new SimpleIntegerProperty(0);
    @Getter
    private final IntegerProperty passedTraces = new SimpleIntegerProperty(0);
    @Getter
    private final IntegerProperty failedTraces = new SimpleIntegerProperty(0);
    @Getter
    private final DoubleProperty l2PassRate = new SimpleDoubleProperty(0);
    @Getter
    private final IntegerProperty totalToolCalls = new SimpleIntegerProperty(0);
    @Getter
    private final IntegerProperty blockedToolCalls = new SimpleIntegerProperty(0);

    // L4 爬山分析结果
    @Getter
    private final StringProperty lastClimbSummary = new SimpleStringProperty("");
    @Getter
    private final StringProperty lastClimbAnalysis = new SimpleStringProperty("");
    @Getter
    private final IntegerProperty lastClimbSuggestions = new SimpleIntegerProperty(0);
    @Getter
    private final IntegerProperty lastClimbApplied = new SimpleIntegerProperty(0);

    public void loadData() {
        List<Gene> geneList = geneStore.loadGenes();
        genes.setAll(geneList);

        List<EvolutionEvent> eventList = geneStore.readRecentEvents(50);
        events.setAll(eventList);

        updateStats();
        loadVerificationStats();
        loadRecentTraces();
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
        eventCount.set(events.size());

        promptGeneCount.set((int) genes.stream().filter(g -> g.type() == GeneType.PROMPT).count());
        rubricGeneCount.set((int) genes.stream().filter(g -> g.type() == GeneType.RUBRIC).count());
        toolDescGeneCount.set((int) genes.stream().filter(g -> g.type() == GeneType.TOOL_DESC).count());
        skillConfigGeneCount.set((int) genes.stream().filter(g -> g.type() == GeneType.SKILL_CONFIG).count());

        if (!events.isEmpty()) {
            long successCount = events.stream()
                    .filter(e -> e.outcome() != null && "success".equalsIgnoreCase(e.outcome().status()))
                    .count();
            successRate.set((double) successCount / events.size());
        } else {
            successRate.set(0);
        }
    }

    /**
     * 加载 L2 校验通过率统计（默认 default 智能体）。
     */
    private void loadVerificationStats() {
        try {
            TraceRecorder.VerificationStats stats = traceRecorder.stats(DEFAULT_AGENT_ID, RECENT_TRACE_LIMIT);
            totalTraces.set(stats.totalTraces());
            passedTraces.set(stats.passedTraces());
            failedTraces.set(stats.failedTraces());
            l2PassRate.set(stats.passRate());
            totalToolCalls.set(stats.totalToolCalls());
            blockedToolCalls.set(stats.blockedToolCalls());
        } catch (Exception e) {
            log.warn("[GEP] 加载 L2 校验统计失败", e);
        }
    }

    /**
     * 加载最近 Trace 列表。
     */
    private void loadRecentTraces() {
        try {
            List<Trace> traces = traceRecorder.loadRecent(DEFAULT_AGENT_ID, RECENT_TRACE_LIMIT);
            recentTraces.setAll(traces);
        } catch (Exception e) {
            log.warn("[GEP] 加载最近 Trace 失败", e);
        }
    }

    /**
     * 异步触发 L4 爬山分析。
     *
     * @param onDone 分析完成回调（在 FX 线程执行）
     */
    public void climbAsync(Runnable onDone) {
        javafx.concurrent.Task<ClimbingResult> task = new javafx.concurrent.Task<>() {
            @Override
            protected ClimbingResult call() {
                return evolutionEngine.climb(DEFAULT_AGENT_ID);
            }
        };
        task.setOnSucceeded(e -> {
            ClimbingResult result = task.getValue();
            updateClimbingResult(result);
            loadData();
            if (onDone != null) onDone.run();
        });
        task.setOnFailed(e -> {
            log.error("[GEP] L4 爬山分析失败", task.getException());
            lastClimbSummary.set("分析失败: " + (task.getException() != null
                    ? task.getException().getMessage() : "未知错误"));
            if (onDone != null) onDone.run();
        });
        ExecutorManager.getPlatformTaskExecutor().execute(task);
    }

    private void updateClimbingResult(ClimbingResult r) {
        lastClimbSummary.set(String.format(
                "分析 %d 条 Trace，发现 %d 条建议，应用 %d 条，跳过 %d 条（优化前通过率 %.1f%%）",
                r.traceCount(),
                r.suggestions() != null ? r.suggestions().size() : 0,
                r.appliedCount(),
                r.skippedCount(),
                r.passRateBefore() * 100));
        lastClimbSuggestions.set(r.suggestions() != null ? r.suggestions().size() : 0);
        lastClimbApplied.set(r.appliedCount());
        lastClimbAnalysis.set(formatAnalysis(r));
    }

    private String formatAnalysis(ClimbingResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("# L4 爬山分析报告\n\n");
        sb.append("- Agent: ").append(r.agentId()).append("\n");
        sb.append("- 分析 Trace 数: ").append(r.traceCount()).append("\n");
        sb.append("- 优化前通过率: ").append(String.format("%.1f%%", r.passRateBefore() * 100)).append("\n");
        sb.append("- 应用: ").append(r.appliedCount()).append(" 条\n");
        sb.append("- 跳过: ").append(r.skippedCount()).append(" 条\n\n");

        List<OptimizationSuggestion> suggestions = r.suggestions();
        if (suggestions == null || suggestions.isEmpty()) {
            sb.append("暂无优化建议。\n");
        } else {
            sb.append("## 优化建议\n\n");
            for (OptimizationSuggestion s : suggestions) {
                sb.append("- [").append(String.format("%.2f", s.confidence())).append("] ");
                sb.append(s.geneId()).append(" (").append(s.geneType()).append(" / ").append(s.targetId()).append(")\n");
                sb.append("  问题: ").append(s.issue()).append("\n");
                sb.append("  建议: ").append(s.suggestion()).append("\n");
            }
        }

        if (r.analysisText() != null && !r.analysisText().isBlank()) {
            sb.append("\n## LLM 原始分析\n\n").append(r.analysisText()).append("\n");
        }
        return sb.toString();
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

    public List<GeneRepository.CommitInfo> getGeneHistory(String geneId) {
        return geneStore.getGeneHistory(geneId);
    }

    public void revertGene(String geneId, String commitHash) {
        geneStore.revertGene(geneId, commitHash);
        loadData();
    }

    public String queryGeneDetail(String geneId) {
        return evolutionEngine.queryGeneDetail(geneId);
    }
}
