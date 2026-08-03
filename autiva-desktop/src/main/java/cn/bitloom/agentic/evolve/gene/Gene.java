package cn.bitloom.agentic.evolve.gene;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基因 record，folder-based，包含 YAML frontmatter（机器控制信号）+ Markdown body（LLM 指令）。
 * <p>
 * frontmatter 中存储结构化控制信号（name/category/signalsMatch/epigeneticBoost 等），
 * content 为注入给 LLM 的自然语言指令。
 */
public record Gene(String id, String basePath, Map<String, Object> frontmatter, String content) {

    public String name() {
        Object value = frontmatter.get("name");
        return value != null ? value.toString() : null;
    }

    public String description() {
        Object value = frontmatter.get("description");
        return value != null ? value.toString() : "";
    }

    public GeneCategory category() {
        Object value = frontmatter.get("category");
        if (value == null) {
            return GeneCategory.STRATEGY;
        }
        try {
            return GeneCategory.valueOf(value.toString().toUpperCase());
        } catch (IllegalArgumentException e) {
            return GeneCategory.STRATEGY;
        }
    }

    /**
     * 信号匹配映射，描述该基因适配的任务特征（taskType/toolsUsed/errorPatterns）。
     * 默认返回空 Map。
     */
    public Map<String, Object> signalsMatch() {
        Object value = frontmatter.get("signalsMatch");
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return Map.of();
    }

    /**
     * 表观遗传增强因子，影响基因被选中的概率。默认 1.0。
     */
    public double epigeneticBoost() {
        Object value = frontmatter.get("epigeneticBoost");
        if (value == null) {
            return 1.0;
        }
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    /**
     * 约束列表。兼容 List 和 Map 两种 frontmatter 写法。
     * 默认返回空 List。
     */
    public List<String> constraints() {
        Object value = frontmatter.get("constraints");
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            List<String> result = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.add(entry.getKey() + ": " + entry.getValue());
            }
            return result;
        }
        return List.of();
    }

    public int version() {
        return readInt("version", 1);
    }

    public int successCount() {
        return readInt("successCount", 0);
    }

    public int failureCount() {
        return readInt("failureCount", 0);
    }

    public String lastVerifiedAt() {
        Object value = frontmatter.get("lastVerifiedAt");
        return value != null ? value.toString() : "";
    }

    /**
     * 是否启用。从 frontmatter 的 "enabled" 字段读取，默认 true。
     */
    public boolean enabled() {
        Object value = frontmatter.get("enabled");
        if (value == null) {
            return true;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private int readInt(String key, int defaultValue) {
        Object value = frontmatter.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number num) {
            return num.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
