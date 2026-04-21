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
 * 为特定子代理类型执行子代理任务。
 *
 * @author Christian Tzolov
 */
public interface SubagentExecutor {

	/** 返回此执行器处理的子代理类型。 */
	String getKind();

	/** 使用指定的子代理定义执行任务。 */
	String execute(TaskCall taskCall, SubagentDefinition subagent);

}
