package cn.bitloom.agentic.agent.subagent.generic;

import cn.bitloom.agentic.agent.subagent.SubagentDefinition;
import cn.bitloom.agentic.agent.subagent.SubagentReference;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public record GenericSubagentDefinition(SubagentReference reference, Map<String, Object> frontMatter, String content) implements SubagentDefinition {

	public static final String KIND = "GENERIC";

	private static final String FRONTMATTER_NAME_KEY = "name";

	private static final String FRONTMATTER_DESCRIPTION_KEY = "description";

	private static final String FRONTMATTER_MODEL_KEY = "model";

	private static final String FRONTMATTER_TOOLS_KEY = "tools";

	private static final String FRONTMATTER_DISALLOWED_TOOLS_KEY = "disallowedTools";

	private static final String FRONTMATTER_SKILLS_KEY = "skills";

	private static final String FRONTMATTER_PERMISSION_MODE_KEY = "permissionMode";

	@Override
	public String getName() {
		return this.frontMatter.get(FRONTMATTER_NAME_KEY).toString();
	}

	@Override
	public String getDescription() {
		return this.frontMatter.get(FRONTMATTER_DESCRIPTION_KEY).toString();
	}

	@Override
	public String getKind() {
		return KIND;
	}

	public String getModel() {
		Object model = this.frontMatter.get(FRONTMATTER_MODEL_KEY);
		return model != null ? model.toString() : null;
	}

	public List<String> tools() {
		if (!this.frontMatter.containsKey(FRONTMATTER_TOOLS_KEY)) {
			return List.of();
		}
		String[] toolNames = this.frontMatter.get(FRONTMATTER_TOOLS_KEY).toString().split(",");
		return Stream.of(toolNames).map(String::trim).filter(StringUtils::hasText).toList();
	}

	public List<String> disallowedTools() {
		if (!this.frontMatter.containsKey(FRONTMATTER_DISALLOWED_TOOLS_KEY)) {
			return List.of();
		}
		String[] toolNames = this.frontMatter.get(FRONTMATTER_DISALLOWED_TOOLS_KEY).toString().split(",");
		return Stream.of(toolNames).map(String::trim).filter(StringUtils::hasText).toList();
	}

	public List<String> skills() {
		if (!this.frontMatter.containsKey(FRONTMATTER_SKILLS_KEY)) {
			return List.of();
		}
		String[] skillNames = this.frontMatter.get(FRONTMATTER_SKILLS_KEY).toString().split(",");
		return Stream.of(skillNames).map(String::trim).filter(StringUtils::hasText).toList();
	}

	public String permissionMode() {
		if (!this.frontMatter.containsKey(FRONTMATTER_PERMISSION_MODE_KEY)) {
			return "default";
		}
		return this.frontMatter.get(FRONTMATTER_PERMISSION_MODE_KEY).toString();
	}


	@Override
	public String toSubagentRegistrations() {
		StringBuilder sb = new StringBuilder();
		sb.append("- **%s**: %s".formatted(getName(), getDescription()));
		if (!tools().isEmpty()) {
			sb.append(" (工具: %s)".formatted(String.join(", ", tools())));
		}
		return sb.toString();
	}

}
