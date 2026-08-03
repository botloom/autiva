package cn.bitloom.agentic.evolve.experience;

import cn.bitloom.util.JsonUtils;
import org.yaml.snakeyaml.Yaml;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 经验 — 从轨迹中提炼的可迁移知识。
 * <p>
 * 一条经验记录了特定任务族下的适用场景、推荐策略、常见误区与例外条件，
 * 并通过 {@link #sourceTrajectoryIds} 保持对来源轨迹的可追溯性。
 * <p>
 * 经验以带 YAML frontmatter 的 Markdown 文档形式持久化到 {@code AgentMemoryStore}，
 * 序列化/反序列化通过 {@link #toMarkdown()} 和 {@link #fromMarkdown(String)} 完成。
 *
 * @param id                   经验唯一标识
 * @param taskFamily           任务族标识（如 "coding-refactor"）
 * @param capabilities         所需能力列表（如 ["search","edit","test"]）
 * @param appliesWhen          适用场景描述
 * @param recommendedStrategy  推荐策略
 * @param commonPitfall        常见误区
 * @param exceptionCondition   例外条件
 * @param sourceTrajectoryIds  来源轨迹 ID 列表（可追溯）
 * @param createdAt            创建时间
 * @param lastVerifiedAt       最近一次验证时间
 * @param supportCount         支持轨迹数
 * @param status               经验状态
 */
public record Experience(
        String id,
        String taskFamily,
        List<String> capabilities,
        String appliesWhen,
        String recommendedStrategy,
        String commonPitfall,
        String exceptionCondition,
        List<String> sourceTrajectoryIds,
        Instant createdAt,
        Instant lastVerifiedAt,
        int supportCount,
        ExperienceStatus status
) {

    private static final String FRONTMATTER_SEPARATOR = "---";

    /** 多条经验拼接时的分隔标记（用于单文件存储多条经验） */
    private static final String EXPERIENCE_SPLIT_MARKER = "<!-- EXPERIENCE_SPLIT -->";

    /** 已知的 body 段落标题 */
    private static final String SECTION_STRATEGY = "## 推荐策略";
    private static final String SECTION_PITFALL = "## 常见误区";
    private static final String SECTION_EXCEPTION = "## 例外条件";

    public Experience {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        sourceTrajectoryIds = sourceTrajectoryIds == null ? List.of() : List.copyOf(sourceTrajectoryIds);
    }

    // ===================== 序列化 =====================

    /**
     * 将经验序列化为带 YAML frontmatter 的 Markdown 文档。
     * <p>
     * frontmatter 存储结构化元数据（用于快速检索），body 存储详细内容（策略/误区/例外条件）。
     */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();

        // --- frontmatter ---
        sb.append(FRONTMATTER_SEPARATOR).append("\n");
        sb.append("id: ").append(quote(id)).append("\n");
        sb.append("taskFamily: ").append(quote(taskFamily)).append("\n");
        sb.append("capabilities: ").append(toJsonArray(capabilities)).append("\n");
        sb.append("appliesWhen: ").append(quote(appliesWhen)).append("\n");
        sb.append("createdAt: ").append(quote(formatInstant(createdAt))).append("\n");
        sb.append("lastVerifiedAt: ").append(quote(formatInstant(lastVerifiedAt))).append("\n");
        sb.append("supportCount: ").append(supportCount).append("\n");
        sb.append("status: ").append(quote(status != null ? status.name().toLowerCase() : "")).append("\n");
        sb.append("sources: ").append(toJsonArray(sourceTrajectoryIds)).append("\n");
        sb.append(FRONTMATTER_SEPARATOR).append("\n\n");

        // --- body ---
        String title = taskFamily != null && !taskFamily.isBlank() ? taskFamily + " 经验" : "经验";
        sb.append("# ").append(title).append("\n\n");
        sb.append(SECTION_STRATEGY).append("\n").append(nullToEmpty(recommendedStrategy)).append("\n\n");
        sb.append(SECTION_PITFALL).append("\n").append(nullToEmpty(commonPitfall)).append("\n\n");
        sb.append(SECTION_EXCEPTION).append("\n").append(nullToEmpty(exceptionCondition)).append("\n");

        return sb.toString();
    }

    // ===================== 反序列化 =====================

    /**
     * 从 Markdown 文档反序列化为 Experience 对象。
     * <p>
     * 解析 frontmatter 获取结构化元数据，解析 body 获取策略/误区/例外条件。
     *
     * @param md Markdown 文档内容
     * @return Experience 实例
     * @throws IllegalArgumentException 文档格式非法时抛出
     */
    public static Experience fromMarkdown(String md) {
        if (md == null || md.isBlank()) {
            throw new IllegalArgumentException("Markdown 内容为空");
        }

        // 清理 BOM 与零宽字符
        String content = md.replaceAll("[\\uFEFF\\u200B\\u200C\\u200D]", "");

        // 分离 frontmatter 和 body
        String frontMatterSection = "";
        String body = content;

        if (content.startsWith(FRONTMATTER_SEPARATOR)) {
            int endIndex = content.indexOf(FRONTMATTER_SEPARATOR, 3);
            if (endIndex != -1) {
                frontMatterSection = content.substring(3, endIndex).trim();
                body = content.substring(endIndex + 3).trim();
            }
        }

        // 解析 frontmatter
        Map<String, Object> frontmatter = parseFrontmatter(frontMatterSection);

        // 解析 body 各段落
        String recommendedStrategy = extractSection(body, SECTION_STRATEGY);
        String commonPitfall = extractSection(body, SECTION_PITFALL);
        String exceptionCondition = extractSection(body, SECTION_EXCEPTION);

        return new Experience(
                getAsString(frontmatter, "id"),
                getAsString(frontmatter, "taskFamily"),
                getAsStringList(frontmatter, "capabilities"),
                getAsString(frontmatter, "appliesWhen"),
                recommendedStrategy,
                commonPitfall,
                exceptionCondition,
                getAsStringList(frontmatter, "sources"),
                getAsInstant(frontmatter, "createdAt"),
                getAsInstant(frontmatter, "lastVerifiedAt"),
                getAsInt(frontmatter, "supportCount"),
                getAsStatus(frontmatter, "status")
        );
    }

    // ===================== frontmatter 解析辅助 =====================

    /**
     * 使用 SnakeYAML 解析 frontmatter 段为 Map。
     * 失败时回退到简单 key:value 解析。
     */
    private static Map<String, Object> parseFrontmatter(String section) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (section == null || section.isEmpty()) {
            return result;
        }
        try {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(section);
            if (loaded instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        } catch (Exception e) {
            // SnakeYAML 解析失败，回退到简单解析
            result.putAll(parseSimpleFrontmatter(section));
        }
        return result;
    }

    /**
     * 简单 key:value 解析（SnakeYAML 失败时的回退方案）。
     */
    private static Map<String, Object> parseSimpleFrontmatter(String section) {
        Map<String, Object> result = new LinkedHashMap<>();
        String[] lines = section.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                // 去除外层引号
                if (value.length() >= 2 &&
                        ((value.startsWith("\"") && value.endsWith("\"")) ||
                                (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * 从 body 中提取指定段落的内容。
     * 段落以 {@code ## 标题} 开始，到下一个 {@code ## 标题} 或文档末尾结束。
     */
    private static String extractSection(String body, String sectionMarker) {
        int start = body.indexOf(sectionMarker);
        if (start == -1) {
            return "";
        }
        int contentStart = start + sectionMarker.length();
        // 查找下一个 ## 标题
        int end = body.indexOf("\n## ", contentStart);
        if (end == -1) {
            end = body.length();
        }
        return body.substring(contentStart, end).trim();
    }

    // ===================== 类型转换辅助 =====================

    private static String getAsString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> getAsStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        // 单值情况
        return List.of(String.valueOf(value));
    }

    private static Instant getAsInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof java.util.Date d) {
            return d.toInstant();
        }
        try {
            return Instant.parse(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private static int getAsInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static ExperienceStatus getAsStatus(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return ExperienceStatus.CANDIDATE;
        }
        String s = String.valueOf(value).toUpperCase().trim();
        try {
            return ExperienceStatus.valueOf(s);
        } catch (IllegalArgumentException e) {
            return ExperienceStatus.CANDIDATE;
        }
    }

    // ===================== 序列化辅助 =====================

    /**
     * 将字符串用双引号包裹，转义内部特殊字符。
     */
    private static String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        String escaped = value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
        return "\"" + escaped + "\"";
    }

    /**
     * 将字符串列表序列化为 JSON 数组字符串（如 ["a","b","c"]）。
     */
    private static String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        return JsonUtils.toJson(list);
    }

    /**
     * 格式化 Instant 为 ISO 8601 字符串。
     */
    private static String formatInstant(Instant instant) {
        return instant != null ? instant.toString() : "";
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    /**
     * 多条经验拼接时的分隔标记（供 ExperienceEngine 使用）。
     */
    public static String splitMarker() {
        return EXPERIENCE_SPLIT_MARKER;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Experience that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
