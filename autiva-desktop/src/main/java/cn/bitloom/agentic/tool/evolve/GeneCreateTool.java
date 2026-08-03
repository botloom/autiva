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
import java.util.UUID;

/**
 * 创建新基因（候选状态）的工具。
 * <p>
 * 生成 gene-{uuid} 作为 ID，写入 GENES_DIR/{geneId}/gene.md，
 * 初始 version=1, epigeneticBoost=1.0, enabled=true。
 */
@Slf4j
public class GeneCreateTool extends AbstractTool<GeneCreateTool.Input> {

    private static final String DESCRIPTION = """
            创建一个新的进化基因（候选状态）。
            需要提供 name、category、content，可选 description/signalsMatchJson/constraintsJson。
            新基因初始 version=1, epigeneticBoost=1.0, enabled=true。
            """;

    private final GeneRepository geneRepository;

    private GeneCreateTool(GeneRepository geneRepository) {
        super("GeneCreate", DESCRIPTION, Input.class);
        Assert.notNull(geneRepository, "geneRepository 不能为 null");
        this.geneRepository = geneRepository;
    }

    public record Input(
            @ToolParam(description = "基因名称（同分类下唯一）") String name,
            @ToolParam(description = "分类：STRATEGY / RULE / CONSTRAINT / PROCEDURE") String category,
            @ToolParam(description = "基因 content（Markdown 形式的 LLM 指令）") String content,
            @ToolParam(description = "基因描述", required = false) String description,
            @ToolParam(description = "signalsMatch 的 JSON 字符串，如 {\"taskType\":\"coding\",\"toolsUsed\":[\"Read\",\"Edit\"]}",
                    required = false) String signalsMatchJson,
            @ToolParam(description = "constraints 的 JSON 数组字符串，如 [\"不允许删除文件\",\"必须先备份\"]",
                    required = false) String constraintsJson
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        log.info("[ToolCall] GeneCreate - 创建基因: name={}, category={}", input.name(), input.category());

        if (input.name() == null || input.name().isBlank()) {
            return ToolResult.error("name 不能为空");
        }
        if (input.content() == null || input.content().isBlank()) {
            return ToolResult.error("content 不能为空");
        }

        GeneCategory category = parseCategory(input.category());

        // 重名校验
        Gene existing = geneRepository.findByName(input.name().trim());
        if (existing != null) {
            return ToolResult.error("基因名称已存在: " + input.name() + " (geneId=" + existing.id() + ")");
        }

        String geneId = "gene-" + UUID.randomUUID();

        Map<String, Object> frontmatter = new LinkedHashMap<>();
        frontmatter.put("name", input.name().trim());
        frontmatter.put("description", input.description() != null ? input.description() : "");
        frontmatter.put("category", category.name().toLowerCase());
        frontmatter.put("version", 1);
        frontmatter.put("epigeneticBoost", 1.0);
        frontmatter.put("enabled", true);
        frontmatter.put("successCount", 0);
        frontmatter.put("failureCount", 0);
        frontmatter.put("lastVerifiedAt", Instant.now().toString());
        frontmatter.put("createdAt", Instant.now().toString());

        Map<String, Object> signals = parseJsonMap(input.signalsMatchJson());
        if (signals != null) {
            frontmatter.put("signalsMatch", signals);
        } else {
            frontmatter.put("signalsMatch", Map.of());
        }

        List<String> constraints = parseJsonList(input.constraintsJson());
        frontmatter.put("constraints", constraints != null ? constraints : List.of());

        Gene gene = new Gene(geneId, null, frontmatter, input.content());
        geneRepository.save(gene);

        log.info("[ToolCall] GeneCreate - 基因创建成功: geneId={}, name={}", geneId, input.name());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("geneId", geneId);
        data.put("name", input.name());
        data.put("category", category.name());
        data.put("version", 1);
        data.put("epigeneticBoost", 1.0);

        String rawOutput = "基因创建成功\n\n" +
                "- Gene ID: " + geneId + "\n" +
                "- 名称: " + input.name() + "\n" +
                "- 分类: " + category + "\n" +
                "- 版本: v1\n" +
                "- epigeneticBoost: 1.00\n" +
                "- 路径: " + geneRepository.findById(geneId).basePath() + "\n";

        return ToolResult.builder()
                .status(ToolResult.Status.SUCCESS)
                .message("基因创建成功: " + geneId)
                .data(data)
                .rawOutput(rawOutput)
                .build();
    }

    private GeneCategory parseCategory(String category) {
        if (category == null || category.isBlank()) {
            return GeneCategory.STRATEGY;
        }
        try {
            return GeneCategory.valueOf(category.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return GeneCategory.STRATEGY;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return cn.bitloom.util.JsonUtils.fromJson(json, Map.class);
        } catch (Exception e) {
            log.warn("[GeneCreateTool] 解析 signalsMatchJson 失败: {}", json, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return cn.bitloom.util.JsonUtils.fromJson(json, List.class);
        } catch (Exception e) {
            log.warn("[GeneCreateTool] 解析 constraintsJson 失败: {}", json, e);
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

        public GeneCreateTool build() {
            return new GeneCreateTool(this.geneRepository);
        }
    }
}
