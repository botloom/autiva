package cn.bitloom.agentic.tool.evolve;

import cn.bitloom.agentic.evolve.experience.Experience;
import cn.bitloom.agentic.evolve.experience.ExperienceEngine;
import cn.bitloom.agentic.evolve.experience.ExperienceStatus;
import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneCategory;
import cn.bitloom.agentic.evolve.gene.GeneRepository;
import cn.bitloom.agentic.evolve.trajectory.TrajectoryOutcome;
import cn.bitloom.agentic.evolve.trajectory.TrajectoryRepository;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查看进化系统整体状态的工具。
 * <p>
 * 汇总展示：
 * <ul>
 *   <li>轨迹统计（按 outcome 分组）</li>
 *   <li>经验统计（按 status 分组）</li>
 *   <li>基因统计（总数、启用数、按 category 分组、平均 boost）</li>
 *   <li>最近进化事件（最多 5 条）</li>
 * </ul>
 */
@Slf4j
public class EvolveStatusTool extends AbstractTool<EvolveStatusTool.Input> {

    /** 最近事件展示数量 */
    private static final int RECENT_EVENT_LIMIT = 5;

    private static final String DESCRIPTION = """
            查看进化系统整体状态，包括轨迹统计、经验统计、基因统计和最近进化事件。
            无需参数。
            """;

    private final TrajectoryRepository trajectoryRepository;
    private final ExperienceEngine experienceEngine;
    private final GeneRepository geneRepository;

    private EvolveStatusTool(TrajectoryRepository trajectoryRepository,
                             ExperienceEngine experienceEngine,
                             GeneRepository geneRepository) {
        super("EvolveStatus", DESCRIPTION, Input.class);
        Assert.notNull(trajectoryRepository, "trajectoryRepository 不能为 null");
        Assert.notNull(experienceEngine, "experienceEngine 不能为 null");
        Assert.notNull(geneRepository, "geneRepository 不能为 null");
        this.trajectoryRepository = trajectoryRepository;
        this.experienceEngine = experienceEngine;
        this.geneRepository = geneRepository;
    }

    public record Input(
            @ToolParam(description = "无参数", required = false) String _none
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        log.info("[ToolCall] EvolveStatus - 查询进化系统状态");

        // 1. 轨迹统计
        Map<String, Object> trajectoryStats = collectTrajectoryStats();

        // 2. 经验统计
        Map<String, Object> experienceStats = collectExperienceStats();

        // 3. 基因统计
        Map<String, Object> geneStats = collectGeneStats();

        // 4. 最近进化事件
        List<Map<String, Object>> recentEvents = collectRecentEvents();

        // 组装输出
        StringBuilder sb = new StringBuilder();
        sb.append("# 进化系统状态\n\n");

        sb.append("## 轨迹统计\n");
        sb.append("- 总数: ").append(trajectoryStats.get("total")).append("\n");
        @SuppressWarnings("unchecked")
        Map<String, Long> byOutcome = (Map<String, Long>) trajectoryStats.get("byOutcome");
        for (Map.Entry<String, Long> entry : byOutcome.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        sb.append("\n");

        sb.append("## 经验统计\n");
        sb.append("- 总数: ").append(experienceStats.get("total")).append("\n");
        @SuppressWarnings("unchecked")
        Map<String, Long> byStatus = (Map<String, Long>) experienceStats.get("byStatus");
        for (Map.Entry<String, Long> entry : byStatus.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        sb.append("\n");

        sb.append("## 基因统计\n");
        sb.append("- 总数: ").append(geneStats.get("total")).append("\n");
        sb.append("- 启用: ").append(geneStats.get("enabled")).append("\n");
        sb.append("- 禁用: ").append(geneStats.get("disabled")).append("\n");
        sb.append("- 平均 boost: ").append(geneStats.get("avgBoost")).append("\n");
        sb.append("- 按分类:\n");
        @SuppressWarnings("unchecked")
        Map<String, Long> byCategory = (Map<String, Long>) geneStats.get("byCategory");
        for (Map.Entry<String, Long> entry : byCategory.entrySet()) {
            sb.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        sb.append("\n");

        sb.append("## 最近进化事件（最多 ").append(RECENT_EVENT_LIMIT).append(" 条）\n");
        if (recentEvents.isEmpty()) {
            sb.append("(无)\n");
        } else {
            for (Map<String, Object> event : recentEvents) {
                sb.append("- [").append(event.get("timestamp")).append("] ")
                        .append(event.get("action"))
                        .append(" geneId=").append(event.get("geneId"))
                        .append(" v").append(event.get("fromVersion"))
                        .append(" -> v").append(event.get("toVersion"))
                        .append(" boost=").append(event.get("boostBefore"))
                        .append(" -> ").append(event.get("boostAfter"))
                        .append("\n");
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("trajectory", trajectoryStats);
        data.put("experience", experienceStats);
        data.put("gene", geneStats);
        data.put("recentEvents", recentEvents);

        return ToolResult.builder()
                .status(ToolResult.Status.SUCCESS)
                .message("进化系统状态（轨迹 " + trajectoryStats.get("total")
                        + " / 经验 " + experienceStats.get("total")
                        + " / 基因 " + geneStats.get("total") + "）")
                .data(data)
                .rawOutput(sb.toString())
                .build();
    }

    private Map<String, Object> collectTrajectoryStats() {
        Map<String, Long> byOutcome = new LinkedHashMap<>();
        long total = 0;
        for (TrajectoryOutcome outcome : TrajectoryOutcome.values()) {
            long count = trajectoryRepository.findByOutcome(outcome).size();
            byOutcome.put(outcome.name(), count);
            total += count;
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("byOutcome", byOutcome);
        return stats;
    }

    private Map<String, Object> collectExperienceStats() {
        List<Experience> experiences = experienceEngine.loadAllExperiences();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ExperienceStatus status : ExperienceStatus.values()) {
            byStatus.put(status.name(), 0L);
        }
        for (Experience exp : experiences) {
            String key = exp.status() != null ? exp.status().name() : ExperienceStatus.CANDIDATE.name();
            byStatus.merge(key, 1L, Long::sum);
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", (long) experiences.size());
        stats.put("byStatus", byStatus);
        return stats;
    }

    private Map<String, Object> collectGeneStats() {
        List<Gene> genes = geneRepository.findAll();
        long enabled = genes.stream().filter(Gene::enabled).count();
        long disabled = genes.size() - enabled;
        double avgBoost = genes.isEmpty() ? 0.0
                : genes.stream().mapToDouble(Gene::epigeneticBoost).average().orElse(0.0);

        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (GeneCategory category : GeneCategory.values()) {
            byCategory.put(category.name(), 0L);
        }
        for (Gene gene : genes) {
            byCategory.merge(gene.category().name(), 1L, Long::sum);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", (long) genes.size());
        stats.put("enabled", enabled);
        stats.put("disabled", disabled);
        stats.put("avgBoost", String.format("%.4f", avgBoost));
        stats.put("byCategory", byCategory);
        return stats;
    }

    /**
     * 读取进化事件日志文件，返回最近 N 条事件。
     */
    private List<Map<String, Object>> collectRecentEvents() {
        List<Map<String, Object>> events = new ArrayList<>();
        Path file = AppConstants.Evolve.EVOLUTION_EVENTS_FILE;
        if (!Files.exists(file)) {
            return events;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            // 从后往前取最近 N 条
            int start = Math.max(0, lines.size() - RECENT_EVENT_LIMIT);
            for (int i = lines.size() - 1; i >= start; i--) {
                String line = lines.get(i);
                if (line.isBlank()) {
                    continue;
                }
                try {
                    JsonNode node = JsonUtils.parse(line);
                    Map<String, Object> event = new LinkedHashMap<>();
                    event.put("timestamp", textOrEmpty(node, "timestamp"));
                    event.put("action", textOrEmpty(node, "action"));
                    event.put("geneId", textOrEmpty(node, "geneId"));
                    event.put("fromVersion", node.has("fromVersion") ? node.get("fromVersion").asInt() : 0);
                    event.put("toVersion", node.has("toVersion") ? node.get("toVersion").asInt() : 0);
                    event.put("boostBefore", node.has("boostBefore") ? node.get("boostBefore").asDouble() : 0.0);
                    event.put("boostAfter", node.has("boostAfter") ? node.get("boostAfter").asDouble() : 0.0);
                    events.add(event);
                } catch (Exception e) {
                    log.warn("[EvolveStatusTool] 解析进化事件失败: {}", line);
                }
            }
        } catch (IOException e) {
            log.error("[EvolveStatusTool] 读取进化事件日志失败: {}", file, e);
        }
        return events;
    }

    private static String textOrEmpty(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private TrajectoryRepository trajectoryRepository;
        private ExperienceEngine experienceEngine;
        private GeneRepository geneRepository;

        private Builder() {}

        public Builder trajectoryRepository(TrajectoryRepository trajectoryRepository) {
            this.trajectoryRepository = trajectoryRepository;
            return this;
        }

        public Builder experienceEngine(ExperienceEngine experienceEngine) {
            this.experienceEngine = experienceEngine;
            return this;
        }

        public Builder geneRepository(GeneRepository geneRepository) {
            this.geneRepository = geneRepository;
            return this;
        }

        public EvolveStatusTool build() {
            return new EvolveStatusTool(this.trajectoryRepository, this.experienceEngine, this.geneRepository);
        }
    }
}
