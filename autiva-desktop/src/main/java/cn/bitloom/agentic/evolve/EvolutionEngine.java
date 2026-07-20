package cn.bitloom.agentic.evolve;

import cn.bitloom.agentic.evolve.climb.ClimbingResult;
import cn.bitloom.agentic.evolve.climb.HillClimbingEngine;
import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneStore;
import cn.bitloom.agentic.evolve.gene.GeneType;
import cn.bitloom.agentic.evolve.solidify.EvolutionEvent;
import cn.bitloom.agentic.evolve.solidify.Solidifier;
import cn.bitloom.agentic.trace.TraceRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 进化引擎主类，L4 爬山循环的编排器。
 * <p>
 * 职责：
 * 1. 提供基本的 Gene 查询和管理能力（query/toggle/revert）
 * 2. 委托 {@link HillClimbingEngine} 执行 L4 爬山分析
 * 3. 委托 {@link Solidifier} 根据 L2 校验结果更新表观遗传值
 * 4. 暴露 {@link TraceRecorder} 的统计能力供 UI 使用
 */
@Slf4j
@Component
public class EvolutionEngine {

    private final EvolveConfig config;
    private final GeneStore geneStore;
    private final Solidifier solidifier;
    private final HillClimbingEngine hillClimbingEngine;
    private final TraceRecorder traceRecorder;

    public EvolutionEngine(EvolveConfig config,
                           GeneStore geneStore,
                           Solidifier solidifier,
                           HillClimbingEngine hillClimbingEngine,
                           TraceRecorder traceRecorder) {
        this.config = config;
        this.geneStore = geneStore;
        this.solidifier = solidifier;
        this.hillClimbingEngine = hillClimbingEngine;
        this.traceRecorder = traceRecorder;
    }

    /**
     * 执行 L4 爬山分析。
     */
    public ClimbingResult climb(String agentId) {
        log.info("[EvolutionEngine] 启动 L4 爬山分析 agentId={}", agentId);
        ClimbingResult result = hillClimbingEngine.climb(agentId);
        log.info("[EvolutionEngine] L4 爬山完成 agentId={} applied={} skipped={}",
                agentId, result.appliedCount(), result.skippedCount());
        return result;
    }

    /**
     * 获取指定 Agent 的 L2 校验通过率统计。
     */
    public TraceRecorder.VerificationStats verificationStats(String agentId, int recentLimit) {
        return traceRecorder.stats(agentId, recentLimit);
    }

    public Solidifier.SolidifyResult solidify(EvolutionEvent event) {
        return solidifier.solidify(event);
    }

    public EvolutionEvent createEvent(String geneId, String intent, EvolutionEvent.Outcome outcome) {
        return new EvolutionEvent(
                "evt_" + UUID.randomUUID().toString().substring(0, 8),
                System.currentTimeMillis(),
                List.of(),
                geneId,
                intent,
                "",
                outcome,
                java.util.Map.of()
        );
    }

    public String queryGenes(GeneType type) {
        List<Gene> genes = geneStore.loadEnabledGenes();
        if (type != null) {
            genes = genes.stream().filter(g -> g.type() == type).toList();
        }
        if (genes.isEmpty()) {
            return "没有找到" + (type != null ? type.name() + "类" : "") + "基因";
        }
        return genes.stream()
                .map(this::formatGeneSummary)
                .collect(Collectors.joining("\n"));
    }

    public String queryGeneDetail(String geneId) {
        Gene gene = geneStore.findById(geneId);
        if (gene == null) {
            return "基因不存在: " + geneId;
        }
        return formatGeneDetail(gene);
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

    public String applyGene(String geneId) {
        Gene gene = geneStore.findById(geneId);
        if (gene == null) {
            return "基因不存在: " + geneId;
        }
        if (!gene.enabled()) {
            return "基因已禁用: " + geneId;
        }
        return formatGeneDetail(gene);
    }

    public String toggleGene(String geneId, boolean enabled) {
        geneStore.toggleGene(geneId, enabled);
        return "基因 " + geneId + " 已" + (enabled ? "启用" : "禁用");
    }

    public String revertGene(String geneId, String commitHash) {
        geneStore.revertGene(geneId, commitHash);
        return "基因 " + geneId + " 已回滚到 " + commitHash;
    }

    /**
     * 清理旧 Trace 文件。
     */
    public int cleanupOldTraces() {
        return traceRecorder.cleanupOldTraces(config.getTraceRetentionDays());
    }

    private String formatGeneSummary(Gene gene) {
        return String.format("- [%s] type=%s target=%s v%d boost=%.2f %s",
                gene.id(), gene.type(), gene.targetId(),
                gene.version(), gene.epigeneticBoost(),
                gene.enabled() ? "启用" : "禁用");
    }

    private String formatGeneDetail(Gene gene) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 基因: ").append(gene.id()).append("\n");
        sb.append("- 类型: ").append(gene.type()).append("\n");
        sb.append("- 目标: ").append(gene.targetId()).append("\n");
        sb.append("- 名称: ").append(gene.name()).append("\n");
        sb.append("- 版本: v").append(gene.version()).append("\n");
        sb.append("- 表观遗传值: ").append(String.format("%.2f", gene.epigeneticBoost())).append("\n");
        sb.append("- 状态: ").append(gene.enabled() ? "启用" : "禁用").append("\n");
        if (gene.description() != null) {
            sb.append("- 说明: ").append(gene.description()).append("\n");
        }
        sb.append("\n### 配置内容\n").append(gene.content()).append("\n");
        return sb.toString();
    }
}
