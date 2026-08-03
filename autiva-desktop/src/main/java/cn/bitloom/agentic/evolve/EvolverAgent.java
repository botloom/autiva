package cn.bitloom.agentic.evolve;

import cn.bitloom.agentic.evolve.experience.Experience;
import cn.bitloom.agentic.evolve.experience.ExperienceEngine;
import cn.bitloom.agentic.evolve.experience.ExperienceStatus;
import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneCategory;
import cn.bitloom.agentic.evolve.gene.GeneRepository;
import cn.bitloom.agentic.evolve.gene.GeneSelectionContext;
import cn.bitloom.agentic.evolve.gene.GeneSelector;
import cn.bitloom.agentic.evolve.routing.RoutingEngine;
import cn.bitloom.agentic.evolve.routing.RoutingEntry;
import cn.bitloom.agentic.evolve.safety.EvolutionSafety;
import cn.bitloom.agentic.evolve.solidify.Solidifier;
import cn.bitloom.agentic.evolve.trajectory.Trajectory;
import cn.bitloom.agentic.evolve.trajectory.TrajectoryOutcome;
import cn.bitloom.agentic.evolve.trajectory.TrajectoryRepository;
import cn.bitloom.agentic.evolve.verify.TrajectoryVerifier;
import cn.bitloom.agentic.evolve.verify.VerificationContext;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 进化智能体 — chapter8 完整进化周期：Cycle → Route → Execute → Solidify → Record。
 * <p>
 * 单次进化周期内：
 * <ol>
 *   <li>Cycle：记录进化周期开始</li>
 *   <li>Route：从 {@link ExperienceEngine} 提取经验，将 VERIFIED 经验转化为候选基因</li>
 *   <li>Execute：对候选基因运行 {@link EvolutionSafety} 安全检查与冲突检查</li>
 *   <li>Solidify：通过安全检查的候选基因调用 {@link Solidifier#solidify}</li>
 *   <li>Record：记录进化结果，返回 {@link EvolutionResult}</li>
 * </ol>
 */
@Slf4j
public class EvolverAgent {

    /** 经验升级为 VERIFIED 所需的最小支持轨迹数 */
    private static final int MIN_SUPPORT = 2;

    private final TrajectoryRepository trajectoryRepository;
    /**
     * 轨迹验证器，保留用于扩展（如对固化结果做独立验证）。
     * 经验提取时的验证由 {@link ExperienceEngine} 内部完成。
     */
    private final TrajectoryVerifier verifier;
    private final ExperienceEngine experienceEngine;
    private final GeneRepository geneRepository;
    private final GeneSelector geneSelector;
    private final Solidifier solidifier;
    private final EvolutionSafety safety;
    private final RoutingEngine routingEngine;
    private final VerificationContext verificationContext;

    public EvolverAgent(TrajectoryRepository trajectoryRepository,
                        TrajectoryVerifier verifier,
                        ExperienceEngine experienceEngine,
                        GeneRepository geneRepository,
                        GeneSelector geneSelector,
                        Solidifier solidifier,
                        EvolutionSafety safety,
                        RoutingEngine routingEngine,
                        VerificationContext verificationContext) {
        this.trajectoryRepository = trajectoryRepository;
        this.verifier = verifier;
        this.experienceEngine = experienceEngine;
        this.geneRepository = geneRepository;
        this.geneSelector = geneSelector;
        this.solidifier = solidifier;
        this.safety = safety;
        this.routingEngine = routingEngine;
        this.verificationContext = verificationContext;
    }

    /**
     * 执行一次完整进化周期。
     *
     * @param taskFamily 任务族标识
     * @return 进化结果
     */
    public EvolutionResult runEvolutionCycle(String taskFamily) {
        List<String> messages = new ArrayList<>();

        // 1. Cycle：记录进化周期开始
        log.info("[EvolverAgent] 进化周期开始: taskFamily={}", taskFamily);
        messages.add("Cycle: 进化周期开始 taskFamily=" + taskFamily);

        // 2. Route：查询当前路由 + 提取经验 + 生成候选基因
        List<RoutingEntry> currentRouting = routingEngine.route(taskFamily, List.of(), List.of());
        messages.add("Route: 当前激活路由数=" + currentRouting.size());

        List<Experience> experiences = experienceEngine.extractExperiences(
                taskFamily, MIN_SUPPORT, verificationContext);
        int experiencesExtracted = experiences.size();
        messages.add("Route: 提取经验数=" + experiencesExtracted);

        int trajectoriesAnalyzed = countTrajectories(taskFamily);
        messages.add("Route: 分析轨迹数=" + trajectoriesAnalyzed);

        List<Gene> candidates = generateCandidateGenes(experiences, taskFamily);
        messages.add("Route: 生成候选基因数=" + candidates.size());

        // 3. Execute：安全检查 + Solidify
        List<Gene> existingGenes = new ArrayList<>(geneRepository.findAll());

        List<Gene> activeGenes = geneSelector.select(
                new GeneSelectionContext(taskFamily, List.of(), List.of(), null));
        messages.add("Execute: 当前激活基因数=" + activeGenes.size());

        List<Trajectory> testTrajectories = findTestTrajectories(taskFamily);

        int genesSolidified = 0;
        int genesRolledBack = 0;

        for (Gene candidate : candidates) {
            // 安全检查
            if (!safety.validateGene(candidate)) {
                messages.add("Execute: 基因安全校验失败 geneId=" + candidate.id());
                log.warn("[EvolverAgent] 基因安全校验失败: geneId={}", candidate.id());
                continue;
            }
            if (safety.checkConflict(candidate, existingGenes)) {
                messages.add("Execute: 基因与现有基因冲突 geneId=" + candidate.id());
                log.warn("[EvolverAgent] 基因冲突: geneId={}", candidate.id());
                continue;
            }

            // 4. Solidify
            Solidifier.SolidifyResult result = solidifier.solidify(candidate, testTrajectories);
            if (result.solidified()) {
                genesSolidified++;
                messages.add("Solidify: 基因固化成功 geneId=" + candidate.id()
                        + " version=" + result.newVersion());
                existingGenes.add(candidate);
            } else {
                genesRolledBack++;
                messages.add("Solidify: 基因回滚 geneId=" + candidate.id()
                        + " reason=" + result.reason());
            }
        }

        // 5. Record
        messages.add("Record: 进化周期完成 固化=" + genesSolidified + " 回滚=" + genesRolledBack);
        log.info("[EvolverAgent] 进化周期完成: taskFamily={}, 固化={}, 回滚={}",
                taskFamily, genesSolidified, genesRolledBack);

        return new EvolutionResult(taskFamily, trajectoriesAnalyzed, experiencesExtracted,
                genesSolidified, genesRolledBack, messages);
    }

    /**
     * 候选基因生成策略 — 从 VERIFIED 经验转化为基因。
     * <p>
     * - 将 recommendedStrategy 转化为基因 content<br>
     * - 从 capabilities 和 taskFamily 构建 signalsMatch<br>
     * - 初始 epigeneticBoost = 1.0，version = 1
     */
    private List<Gene> generateCandidateGenes(List<Experience> experiences, String taskFamily) {
        List<Gene> candidates = new ArrayList<>();
        if (experiences == null || experiences.isEmpty()) {
            return candidates;
        }
        for (Experience exp : experiences) {
            if (exp.status() != ExperienceStatus.VERIFIED) {
                continue;
            }
            String geneId = "gene-" + UUID.randomUUID();

            Map<String, Object> frontmatter = new LinkedHashMap<>();
            frontmatter.put("name", taskFamily + "-" + exp.id());
            frontmatter.put("description", "从经验 " + exp.id() + " 提炼的策略基因");
            frontmatter.put("category", GeneCategory.STRATEGY.name().toLowerCase());
            frontmatter.put("version", 1);
            frontmatter.put("epigeneticBoost", 1.0);
            frontmatter.put("enabled", true);
            frontmatter.put("successCount", 0);
            frontmatter.put("failureCount", 0);
            frontmatter.put("lastVerifiedAt", Instant.now().toString());

            Map<String, Object> signalsMatch = new LinkedHashMap<>();
            signalsMatch.put("taskType", taskFamily);
            signalsMatch.put("toolsUsed", exp.capabilities());
            frontmatter.put("signalsMatch", signalsMatch);

            String content = buildGeneContent(exp);
            candidates.add(new Gene(geneId, null, frontmatter, content));
        }
        return candidates;
    }

    /**
     * 将经验的推荐策略转化为基因 content（Markdown 形式）。
     */
    private String buildGeneContent(Experience exp) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 策略基因\n\n");
        sb.append("## 适用场景\n").append(nullToEmpty(exp.appliesWhen())).append("\n\n");
        sb.append("## 推荐策略\n").append(nullToEmpty(exp.recommendedStrategy())).append("\n\n");
        if (exp.commonPitfall() != null && !exp.commonPitfall().isBlank()) {
            sb.append("## 常见误区\n").append(exp.commonPitfall()).append("\n\n");
        }
        if (exp.exceptionCondition() != null && !exp.exceptionCondition().isBlank()) {
            sb.append("## 例外条件\n").append(exp.exceptionCondition()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 统计指定任务族的轨迹数量。
     */
    private int countTrajectories(String taskFamily) {
        int count = 0;
        for (TrajectoryOutcome outcome : TrajectoryOutcome.values()) {
            for (Trajectory t : trajectoryRepository.findByOutcome(outcome)) {
                if (matchesTaskFamily(t, taskFamily)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 查询指定任务族的全部轨迹（用作金丝雀检查的测试样本）。
     */
    private List<Trajectory> findTestTrajectories(String taskFamily) {
        List<Trajectory> result = new ArrayList<>();
        for (TrajectoryOutcome outcome : TrajectoryOutcome.values()) {
            for (Trajectory t : trajectoryRepository.findByOutcome(outcome)) {
                if (matchesTaskFamily(t, taskFamily)) {
                    result.add(t);
                }
            }
        }
        return result;
    }

    /**
     * 判断轨迹是否属于指定任务族：优先 metadata.taskFamily，回退 agentName。
     */
    private boolean matchesTaskFamily(Trajectory t, String taskFamily) {
        if (t.metadata() != null) {
            Object tf = t.metadata().get("taskFamily");
            if (tf instanceof String s && !s.isBlank()) {
                return taskFamily.equals(s);
            }
        }
        return taskFamily.equals(t.agentName());
    }

    private String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    /**
     * 进化周期结果。
     *
     * @param taskFamily           任务族标识
     * @param trajectoriesAnalyzed 分析的轨迹数
     * @param experiencesExtracted 提取的经验数
     * @param genesSolidified      固化成功的基因数
     * @param genesRolledBack      回滚的基因数
     * @param messages             周期内的过程消息
     */
    public record EvolutionResult(String taskFamily,
                                  int trajectoriesAnalyzed,
                                  int experiencesExtracted,
                                  int genesSolidified,
                                  int genesRolledBack,
                                  List<String> messages) {
    }
}
