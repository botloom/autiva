
package cn.bitloom.agentic.agent.subagent;

/**
 * 将子代理解析器与其执行器配对以用于特定类型。
 *
 * @param resolver 将引用解析为定义
 * @param executor 为此子代理类型执行任务
 */
public record SubagentType(SubagentResolver resolver, SubagentExecutor executor) {

	/** 从执行器返回类型标识符。 */
	public String kind() {
		return executor.getKind();
	}

}
