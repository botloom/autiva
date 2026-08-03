package cn.bitloom.agentic.evolve.solidify;

import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneRepository;
import cn.bitloom.agentic.memory.AgentMemoryStore;
import cn.bitloom.agentic.evolve.trajectory.Trajectory;
import cn.bitloom.constant.AppConstants;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 固化器 — chapter8 验证-发布-回滚机制。
 * <p>
 * 流程：
 * <ol>
 *   <li>调用 {@link CanaryCheck#check} 对候选基因做金丝雀检查</li>
 *   <li>通过 → {@link GeneRepository#save} 固化（版本号 +1），epigeneticBoost × 1.2，成功计数 +1</li>
 *   <li>失败 → 回滚（不保存新版本），epigeneticBoost × 0.9，失败计数 +1（仅对已存在基因）</li>
 *   <li>记录 {@link EvolutionEvent}，追加写入 {@link AppConstants.Evolve#EVOLUTION_EVENTS_FILE}</li>
 * </ol>
 */
@Slf4j
public class Solidifier {

    /** 成功时 boost 放大因子 */
    private static final double BOOST_GAIN = 1.2;
    /** 失败时 boost 衰减因子 */
    private static final double BOOST_DECAY = 0.9;
    /** boost 合法范围 */
    private static final double MIN_BOOST = 0.1;
    private static final double MAX_BOOST = 10.0;

    private final GeneRepository geneRepository;
    private final CanaryCheck canaryCheck;
    /**
     * 记忆存储，保留用于扩展（如读取历史进化事件）。
     * 进化事件日志通过 {@link Files} 追加写入 {@link AppConstants.Evolve#EVOLUTION_EVENTS_FILE}。
     */
    private final AgentMemoryStore memoryStore;

    public Solidifier(GeneRepository geneRepository,
                      CanaryCheck canaryCheck,
                      AgentMemoryStore memoryStore) {
        this.geneRepository = geneRepository;
        this.canaryCheck = canaryCheck;
        this.memoryStore = memoryStore;
    }

    /**
     * 固化候选基因。
     *
     * @param candidate          候选基因
     * @param testTrajectories   测试轨迹样本（用于金丝雀检查）
     * @return 固化结果
     */
    public SolidifyResult solidify(Gene candidate, List<Trajectory> testTrajectories) {
        if (candidate == null) {
            return new SolidifyResult(false, 0, 0.0, "候选基因为 null");
        }

        double boostBefore = candidate.epigeneticBoost();
        int fromVersion = candidate.version();

        // 1. 金丝雀检查
        CanaryCheck.CanaryResult canaryResult = canaryCheck.check(candidate, testTrajectories);

        if (canaryResult.passed()) {
            // 2. 通过 → 固化：版本 +1，boost × 1.2，成功计数 +1
            double newBoost = clampBoost(boostBefore * BOOST_GAIN);
            int newVersion = fromVersion + 1;
            Gene solidified = updateGene(candidate, newVersion, newBoost);
            geneRepository.save(solidified);
            geneRepository.incrementSuccess(solidified.id());

            recordEvent(new EvolutionEvent(
                    UUID.randomUUID().toString(),
                    candidate.id(),
                    EvolutionEvent.ACTION_SOLIDIFY,
                    fromVersion,
                    newVersion,
                    boostBefore,
                    newBoost,
                    canaryResult.reason(),
                    Instant.now()
            ));

            log.info("[Solidifier] 基因固化成功: geneId={}, version={} -> {}, boost={} -> {}",
                    candidate.id(), fromVersion, newVersion, boostBefore, newBoost);
            return new SolidifyResult(true, newVersion, newBoost, canaryResult.reason());
        }

        // 3. 失败 → 回滚：不保存新版本，boost × 0.9，失败计数 +1（仅对已存在基因）
        double newBoost = clampBoost(boostBefore * BOOST_DECAY);
        if (geneRepository.findById(candidate.id()) != null) {
            geneRepository.updateBoost(candidate.id(), newBoost);
            geneRepository.incrementFailure(candidate.id());
        }

        recordEvent(new EvolutionEvent(
                UUID.randomUUID().toString(),
                candidate.id(),
                EvolutionEvent.ACTION_ROLLBACK,
                fromVersion,
                fromVersion,
                boostBefore,
                newBoost,
                canaryResult.reason(),
                Instant.now()
        ));

        log.info("[Solidifier] 基因回滚: geneId={}, boost={} -> {}, reason={}",
                candidate.id(), boostBefore, newBoost, canaryResult.reason());
        return new SolidifyResult(false, fromVersion, newBoost, canaryResult.reason());
    }

    /**
     * 构造带新版本号与 boost 的基因副本。
     */
    private Gene updateGene(Gene gene, int newVersion, double newBoost) {
        Map<String, Object> fm = new LinkedHashMap<>(gene.frontmatter());
        fm.put("version", newVersion);
        fm.put("epigeneticBoost", newBoost);
        fm.put("lastVerifiedAt", Instant.now().toString());
        return new Gene(gene.id(), gene.basePath(), fm, gene.content());
    }

    /**
     * 将 boost 限制在合法范围 [0.1, 10.0]。
     */
    private double clampBoost(double boost) {
        return Math.max(MIN_BOOST, Math.min(MAX_BOOST, boost));
    }

    /**
     * 追加写入进化事件到 NDJSON 日志文件。
     */
    private void recordEvent(EvolutionEvent event) {
        Path file = AppConstants.Evolve.EVOLUTION_EVENTS_FILE;
        try {
            Files.createDirectories(file.getParent());
            String line = event.toJson() + System.lineSeparator();
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("[Solidifier] 记录进化事件失败: {}", file, e);
        }
    }

    /**
     * 固化结果。
     *
     * @param solidified 是否成功固化
     * @param newVersion 固化后的版本号（失败时为原版本号）
     * @param newBoost   固化后的表观遗传增强因子
     * @param reason     固化/回滚原因
     */
    public record SolidifyResult(boolean solidified, int newVersion, double newBoost, String reason) {
    }
}
