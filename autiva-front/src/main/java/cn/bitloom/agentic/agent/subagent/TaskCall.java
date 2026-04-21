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

import org.springframework.ai.tool.annotation.ToolParam;

public record TaskCall( // @formatter:off
		@ToolParam(description = "任务的简短（3-5个词）描述") String description,
		@ToolParam(description = "代理要执行的任务") String prompt,
		@ToolParam(description = "用于此任务的专门代理类型") String subagent_type,
		@ToolParam(description = "用于此代理的可选模型。如果未指定，则从父级继承。对于快速、简单的任务，优先使用小型模型以最小化成本和延迟。", required = false) String model,
		@ToolParam(description = "要从中恢复的可选代理ID。如果提供，代理将从之前的执行记录继续。", required = false) String resume,
		@ToolParam(description = "设置为true以在后台运行此代理。稍后使用TaskOutput读取输出。", required = false) Boolean run_in_background ) { // @formatter:on
}