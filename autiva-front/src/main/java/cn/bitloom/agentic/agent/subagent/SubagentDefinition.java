
package cn.bitloom.agentic.agent.subagent;

/**
 * 定义子代理的身份和配置元数据。
 *
 */
public interface SubagentDefinition {

	/** 返回此子代理的唯一名称。 */
	String getName();

	/** 返回此子代理能力的描述。 */
	String getDescription();

	/** 返回类型/类型标识符（例如，"CLAUDE"）。 */
	String getKind();

	/** 返回用于解析此定义的引用。 */
	SubagentReference reference();

	/** 格式化此子代理以用于注册显示。 */
	default String toSubagentRegistrations() {
		return "- **%s**: %s".formatted(getName(), getDescription());
	}

}
