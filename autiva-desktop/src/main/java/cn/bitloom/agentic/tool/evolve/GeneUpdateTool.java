package cn.bitloom.agentic.tool.evolve;

import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneCategory;
import cn.bitloom.agentic.evolve.gene.GeneRepository;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 更新基因内容的工具（创建新版本）。
 * <p>
 * 更新会触发 GeneRepository.save 的版本备份机制：旧版本写入 versions/v{n}.md。
 * 仅允许更新非系统关键基因（即 successCount + failureCount < 100 的基因），保护积累充分的稳定基因。
 */
@Slf4j
public class GeneUpdateTool extends AbstractTool<GeneUpdateTool.Input> {

    /** 系统关键基因的调用次数阈值，超过则视为稳定基因，不允许直接更新 */
    private static final int STABLE_GENE_THRESHOLD = 100;

    private static final String DESCRIPTION = """
            更新指定基因的内容（创建新版本）。
            必须提供 geneId；可选择更新 name/description/category/content/signalsMatchJson/constraintsJson/enabled。
            旧版本自动备份到 versions/v{n}.md。
            """;

    private final GeneRepository geneRepository;

    private GeneUpdateTool(GeneRepository geneRepository) {
        super("GeneUpdate", DESCRIPTION, Input.class);
        Assert.notNull(geneRepository, "geneRepository 不能为 null");
        this.geneRepository = geneRepository;
    }

    public record Input(
            @ToolParam(description = "基因 ID（gene-xxx）") String geneId,
            @ToolParam(description = "新的基因名称", required = false) String name,
            @ToolParam(description = "新的描述", required = false) String description,
            @ToolParam(description = "新的分类：STRATEGY/RULE/CONSTRAINT/PROCEDURE", required = false) String category,
            @ToolParam(description = "新的 content（LLM 指令）", required = false) String content,
            @ToolParam(description = "新的 signalsMatch 的 JSON 字符串", required = false) String signalsMatchJson,
            @ToolParam(description = "新的 constraints 的 JSON 数组字符串", required = false) String constraintsJson,
            @ToolParam(description = "是否启用", required = false) Boolean enabled
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        log.info("[ToolCall] GeneUpdate - 更新基因: geneId={}", input.geneId());

        if (input.geneId() == null || input.geneId().isBlank()) {
            return ToolResult.error("geneId 不能为空");
        }

        Gene existing = geneRepository.findById(input.geneId().trim());
        if (existing == null) {
            return ToolResult.error("基因不存在: " + input.geneId());
        }

        // 保护系统关键稳定基因
        if (existing.successCount() + existing.failureCount() >= STABLE_GENE_THRESHOLD) {
            return ToolResult.error("基因 " + input.geneId() + " 已是稳定基因（调用次数 ≥ " + STABLE_GENE_THRESHOLD
                    + "），不允许直接更新。请通过进化周期自动迭代。");
        }

        // 重名校验（如果修改了 name）
        if (input.name() != null && !input.name().isBlank() && !input.name().equals(existing.name())) {
            Gene conflict = geneRepository.findByName(input.name().trim());
            if (conflict != null && !conflict.id().equals(existing.id())) {
                return ToolResult.error("基因名称已被占用: " + input.name() + " (geneId=" + conflict.id() + ")");
            }
        }

        Map<String, Object> frontmatter = new LinkedHashMap<>(existing.frontmatter());
        int oldVersion = existing.version();
        frontmatter.put("version", oldVersion + 1);
        frontmatter.put("lastVerifiedAt", Instant.now().toString());

        if (input.name() != null && !input.name().isBlank()) {
            frontmatter.put("name", input.name().trim());
        }
        if (input.description() != null) {
            frontmatter.put("description", input.description());
        }
        if (input.category() != null && !input.category().isBlank()) {
            GeneCategory category = parseCategory(input.category());
            frontmatter.put("category", category.name().toLowerCase());
        }
        if (input.enabled() != null) {
            frontmatter.put("enabled", input.enabled());
        }
        if (input.signalsMatchJson() != null && !input.signalsMatchJson().isBlank()) {
            Map<String, Object> signals = parseJsonMap(input.signalsMatchJson());
            if (signals != null) {
                frontmatter.put("signalsMatch", signals);
            }
        }
        if (input.constraintsJson() != null && !input.constraintsJson().isBlank()) {
            List<String> constraints = parseJsonList(input.constraintsJson());
            if (constraints != null) {
                frontmatter.put("constraints", constraints);
            }
        }

        String newContent = (input.content() != null && !input.content().isBlank())
                ? input.content() : existing.content();

        Gene updated = new Gene(existing.id(), existing.basePath(), frontmatter, newContent);
        geneRepository.save(updated);

        log.info("[ToolCall] GeneUpdate - 基因更新成功: geneId={}, version={} -> {}",
                existing.id(), oldVersion, oldVersion + 1);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("geneId", existing.id());
        data.put("oldVersion", oldVersion);
        data.put("newVersion", oldVersion + 1);

        String rawOutput = "基因更新成功\n\n" +
                "- Gene ID: " + existing.id() + "\n" +
                "- 版本: v" + oldVersion + " -> v" + (oldVersion + 1) + "\n" +
                "- 旧版本已备份到 versions/v" + oldVersion + ".md\n";

        return ToolResult.builder()
                .status(ToolResult.Status.SUCCESS)
                .message("基因更新成功: " + existing.id() + " (v" + oldVersion + " -> v" + (oldVersion + 1) + ")")
                .data(data)
                .rawOutput(rawOutput)
                .build();
    }

    private GeneCategory parseCategory(String category) {
        try {
            return GeneCategory.valueOf(category.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return GeneCategory.STRATEGY;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String json) {
        try {
            return cn.bitloom.util.JsonUtils.fromJson(json, Map.class);
        } catch (Exception e) {
            log.warn("[GeneUpdateTool] 解析 signalsMatchJson 失败: {}", json, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseJsonList(String json) {
        try {
            return cn.bitloom.util.JsonUtils.fromJson(json, List.class);
        } catch (Exception e) {
            log.warn("[GeneUpdateTool] 解析 constraintsJson 失败: {}", json, e);
            return null;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private GeneRepository geneRepository;

        private Builder() {}

        public Builder geneRepository(GeneRepository geneRepository) {
            this.geneRepository = geneRepository;
            return this;
        }

        public GeneUpdateTool build() {
            return new GeneUpdateTool(this.geneRepository);
        }
    }
}
