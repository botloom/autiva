/*
* Copyright 2025 - 2025 the original author or authors.
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
 * 定义子代理的身份和配置元数据。
 *
 * @author Christian Tzolov
 */
public interface SubagentDefinition {

	/** 返回此子代理的唯一名称。 */
	String getName();

	/** 返回此子代理能力的描述。 */
	String getDescription();

	/** 返回类型/类型标识符（例如，"CLAUDE"）。 */
	String getKind();

	/** 返回用于解析此定义的引用。 */
	SubagentReference getReference();

	/** 格式化此子代理以用于注册显示。 */
	default public String toSubagentRegistrations() {
		return "-%s: /%s".formatted(getName(), getDescription());
	}

}
