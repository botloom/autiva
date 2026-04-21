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
 * 将子代理解析器与其执行器配对以用于特定类型。
 *
 * @param resolver 将引用解析为定义
 * @param executor 为此子代理类型执行任务
 * @author Christian Tzolov
 */
public record SubagentType(SubagentResolver resolver, SubagentExecutor executor) {

	/** 从执行器返回类型标识符。 */
	public String kind() {
		return executor.getKind();
	}

}
