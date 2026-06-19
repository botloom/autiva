package cn.bitloom.agentic.agent;

import cn.bitloom.agentic.util.MarkdownParser;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 统一的智能体定义模型，从 agent.md 的 YAML frontmatter + markdown body 解析而来。
 * 替代原先的 SubagentDefinition + WorkspaceConfig 双轨配置。
 */
public record AgentDefinition(
        @NonNull String name,
        @NonNull String description,
        @NonNull AgentKind kind,
        @Nullable String model,
        @NonNull List<String> tools,
        @NonNull List<String> disallowedTools,
        @NonNull List<String> skills,
        @NonNull String permissionMode,
        @NonNull String content
) {

    private static final String FRONTMATTER_NAME_KEY = "name";
    private static final String FRONTMATTER_DESCRIPTION_KEY = "description";
    private static final String FRONTMATTER_KIND_KEY = "kind";
    private static final String FRONTMATTER_MODEL_KEY = "model";
    private static final String FRONTMATTER_TOOLS_KEY = "tools";
    private static final String FRONTMATTER_DISALLOWED_TOOLS_KEY = "disallowedTools";
    private static final String FRONTMATTER_SKILLS_KEY = "skills";
    private static final String FRONTMATTER_PERMISSION_MODE_KEY = "permissionMode";

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
        String model = getString(frontMatter, FRONTMATTER_MODEL_KEY, null);
        List<String> tools = parseList(frontMatter, FRONTMATTER_TOOLS_KEY);
        List<String> disallowedTools = parseList(frontMatter, FRONTMATTER_DISALLOWED_TOOLS_KEY);
        List<String> skills = parseList(frontMatter, FRONTMATTER_SKILLS_KEY);
        String permissionMode = getString(frontMatter, FRONTMATTER_PERMISSION_MODE_KEY, "default");

        return new AgentDefinition(name, description, kind, model, tools, disallowedTools,
                skills, permissionMode, content);
    }

    /**
     * 格式化注册信息，用于 Task 工具的描述
     */
    public String toRegistrationText() {
        StringBuilder sb = new StringBuilder();
        sb.append("- **%s**: %s".formatted(name, description));
        if (!tools.isEmpty()) {
            sb.append(" (工具: %s)".formatted(String.join(", ", tools)));
        }
        return sb.toString();
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
