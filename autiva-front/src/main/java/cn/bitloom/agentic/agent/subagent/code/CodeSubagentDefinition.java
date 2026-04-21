package cn.bitloom.agentic.agent.subagent.code;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import cn.bitloom.agentic.agent.subagent.SubagentDefinition;
import cn.bitloom.agentic.agent.subagent.SubagentReference;

import org.springframework.util.StringUtils;

public class CodeSubagentDefinition implements SubagentDefinition {

	public static final String KIND = "CODE";

	private static final String FRONTMATTER_NAME_KEY = "name";

	private static final String FRONTMATTER_DESCRIPTION_KEY = "description";

	private static final String FRONTMATTER_MODEL_KEY = "model";

	private static final String FRONTMATTER_TOOLS_KEY = "tools";

	private static final String FRONTMATTER_DISALLOWED_TOOLS_KEY = "disallowedTools";

	private static final String FRONTMATTER_SKILLS_KEY = "skills";

	private static final String FRONTMATTER_PERMISSION_MODE_KEY = "permissionMode";

	private final Map<String, Object> frontMatter;

	private final String content;

	private final SubagentReference reference;

	public CodeSubagentDefinition(SubagentReference reference, Map<String, Object> frontMatter, String content) {
		this.reference = reference;
		this.frontMatter = frontMatter;
		this.content = content;
	}

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
		return Stream.of(toolNames).map(String::trim).filter(tn -> StringUtils.hasText(tn)).toList();
	}

	public List<String> disallowedTools() {
		if (!this.frontMatter.containsKey(FRONTMATTER_DISALLOWED_TOOLS_KEY)) {
			return List.of();
		}
		String[] toolNames = this.frontMatter.get(FRONTMATTER_DISALLOWED_TOOLS_KEY).toString().split(",");
		return Stream.of(toolNames).map(String::trim).filter(tn -> StringUtils.hasText(tn)).toList();
	}

	public List<String> skills() {
		if (!this.frontMatter.containsKey(FRONTMATTER_SKILLS_KEY)) {
			return List.of();
		}
		String[] skillNames = this.frontMatter.get(FRONTMATTER_SKILLS_KEY).toString().split(",");
		return Stream.of(skillNames).map(String::trim).filter(tn -> StringUtils.hasText(tn)).toList();
	}

	public String permissionMode() {
		if (!this.frontMatter.containsKey(FRONTMATTER_PERMISSION_MODE_KEY)) {
			return "default";
		}
		return this.frontMatter.get(FRONTMATTER_PERMISSION_MODE_KEY).toString();
	}

	@Override
	public SubagentReference getReference() {
		return this.reference;
	}

	public Map<String, Object> getFrontMatter() {
		return frontMatter;
	}

	public String getContent() {
		return content;
	}

}
