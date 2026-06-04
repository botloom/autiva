
package cn.bitloom.agentic.agent.subagent;

/**
 * 将子代理引用解析为完整定义。
 *
 */
public interface SubagentResolver {

	/** 检查此解析器是否可以处理给定的引用。 */
	boolean canResolve(SubagentReference subagentRef);

	/** 将引用解析为完整的子代理定义。 */
	SubagentDefinition resolve(SubagentReference subagentRef);

}
