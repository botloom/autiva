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

/**
 * 将子代理引用解析为完整定义。
 *
 * @author Christian Tzolov
 */
public interface SubagentResolver {

	/** 检查此解析器是否可以处理给定的引用。 */
	boolean canResolve(SubagentReference subagentRef);

	/** 将引用解析为完整的子代理定义。 */
	SubagentDefinition resolve(SubagentReference subagentRef);

}
