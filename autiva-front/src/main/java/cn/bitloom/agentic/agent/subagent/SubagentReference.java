/*
* Copyright 2026 - 2026 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package cn.bitloom.agentic.agent.subagent;

import java.util.Map;

/**
 * 子代理定义资源的引用（例如，markdown文件URI）。
 *
 * @param uri 资源URI（classpath或文件路径）
 * @param kind 子代理类型（例如，"CLAUDE"）
 * @param metadata 可选的键值元数据
 * @author Christian Tzolov
 */
public record SubagentReference(String uri, String kind, Map<String, String> metadata) {

	public SubagentReference(String uri, String kind) {
		this(uri, kind, Map.of());
	}
}
