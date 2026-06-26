package cn.bitloom.agentic.agent;

import cn.bitloom.agentic.util.MarkdownParser;
import lombok.Data;
import org.jspecify.annotations.NonNull;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 统一的智能体定义模型，从 agent.md 的 YAML frontmatter + markdown body 解析而来。
 * 替代原先的 SubagentDefinition + WorkspaceConfig 双轨配置。
 * <p>
 * MAIN 智能体在加载时会合并 config.json（通过 WorkspaceConfig 内部类），
 * 合并后 tools/skills/subagents/mcpServers 以 config.json 为准（非空覆盖）。
 */
public record AgentDefinition(
        @NonNull String name,
        @NonNull String description,
        @NonNull AgentKind kind,
        @NonNull List<String> tools,
        @NonNull List<String> skills,
        @NonNull List<String> subagents,
        @NonNull Map<String, Object> mcpServers,
        @NonNull String content
) {

    private static final String FRONTMATTER_NAME_KEY = "name";
    private static final String FRONTMATTER_DESCRIPTION_KEY = "description";
    private static final String FRONTMATTER_KIND_KEY = "kind";
    private static final String FRONTMATTER_TOOLS_KEY = "tools";
    private static final String FRONTMATTER_SKILLS_KEY = "skills";
    private static final String FRONTMATTER_SUBAGENTS_KEY = "subagents";

    /**
     * 对应 config.json 的模型类，仅用于 MAIN 智能体。
     * 加载后通过 merge() 合并到 AgentDefinition 中。
     */
    @Data
    public static class WorkspaceConfig {
        private List<String> tools = new ArrayList<>();
        private Map<String, Object> mcpServers = new LinkedHashMap<>();
        private List<String> skills = new ArrayList<>();
        private List<String> subagents = new ArrayList<>();
    }

    /**
     * 从 agent.md 文件解析 AgentDefinition
     */
    public static AgentDefinition fromMarkdown(Path agentMdPath) {
        try {
            String markdown = Files.readString(agentMdPath, StandardCharsets.UTF_8);
            return fromMarkdown(markdown);
        } catch (java.io.IOException e) {
            throw new RuntimeException("读取 agent.md 失败: " + agentMdPath, e);
        }
    }

    /**
     * 从 markdown 字符串解析 AgentDefinition
     */
    public static AgentDefinition fromMarkdown(String markdown) {
        MarkdownParser parser = new MarkdownParser(markdown);
        Map<String, Object> frontMatter = parser.getFrontMatter();
        String content = parser.getContent();

        String name = getString(frontMatter, FRONTMATTER_NAME_KEY, "");
        String description = getString(frontMatter, FRONTMATTER_DESCRIPTION_KEY, "");
        AgentKind kind = parseKind(getString(frontMatter, FRONTMATTER_KIND_KEY, "subagent"));
        List<String> tools = parseList(frontMatter, FRONTMATTER_TOOLS_KEY);
        List<String> skills = parseList(frontMatter, FRONTMATTER_SKILLS_KEY);
        List<String> subagents = parseList(frontMatter, FRONTMATTER_SUBAGENTS_KEY);

        return new AgentDefinition(name, description, kind, tools, skills, subagents, Map.of(), content);
    }

    /**
     * 将 WorkspaceConfig 合并到当前 AgentDefinition 中。
     * config.json 中非空的字段覆盖 frontmatter 中的值。
     */
    public AgentDefinition merge(WorkspaceConfig config) {
        if (config == null) {
            return this;
        }
        return new AgentDefinition(
                name,
                description,
                kind,
                config.getTools() != null && !config.getTools().isEmpty() ? config.getTools() : tools,
                config.getSkills() != null && !config.getSkills().isEmpty() ? config.getSkills() : skills,
                config.getSubagents() != null && !config.getSubagents().isEmpty() ? config.getSubagents() : subagents,
                config.getMcpServers() != null && !config.getMcpServers().isEmpty() ? config.getMcpServers() : mcpServers,
                content
        );
    }

    private static String getString(Map<String, Object> frontMatter, String key, String defaultValue) {
        Object value = frontMatter.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private static AgentKind parseKind(String kindStr) {
        if ("main".equalsIgnoreCase(kindStr)) {
            return AgentKind.MAIN;
        }
        return AgentKind.SUBAGENT;
    }

    private static List<String> parseList(Map<String, Object> frontMatter, String key) {
        if (!frontMatter.containsKey(key)) {
            return List.of();
        }
        String value = frontMatter.get(key).toString();
        return Stream.of(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
