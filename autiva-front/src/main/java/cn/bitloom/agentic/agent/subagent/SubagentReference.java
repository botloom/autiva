
package cn.bitloom.agentic.agent.subagent;

import java.util.Map;

/**
 * 子代理定义资源的引用（例如，markdown文件URI）。
 *
 * @param uri 资源URI（classpath或文件路径）
 * @param kind 子代理类型（例如，"CLAUDE"）
 * @param metadata 可选的键值元数据
 */
public record SubagentReference(String uri, String kind, Map<String, String> metadata) {

	public SubagentReference(String uri, String kind) {
		this(uri, kind, Map.of());
	}
}
