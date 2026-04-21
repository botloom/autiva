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
package cn.bitloom.agentic.tool;

import cn.bitloom.agentic.task.repository.BackgroundTask;
import cn.bitloom.agentic.task.repository.TaskRepository;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.Assert;

import java.util.function.Function;

/**
 * 用于从正在运行或已完成的后台任务获取输出的工具。支持阻塞和非阻塞模式，可配置超时。
 *
 * @author Christian Tzolov
 */
public class TaskOutputTool {

	private static final String TASK_DESCRIPTION_TEMPLATE = """
			- 从正在运行或已完成的任务（后台代理）获取输出
			- 接受一个标识任务的task_id参数
			- 返回任务输出及状态信息
			- 使用block=true（默认）等待任务完成
			- 使用block=false进行非阻塞的当前状态检查
			- 任务ID可以通过/tasks命令找到
			""";

	public static record TaskOutputCall( // @formatter:off
		@ToolParam(description = "要获取输出的任务ID") String task_id,
		@ToolParam(description = "是否等待完成", required = false) Boolean block,
		@ToolParam(description = "最大等待时间（毫秒）", required = false) Long timeout ) { // @formatter:on
	}

	public static class TaskOutputFunction implements Function<TaskOutputCall, String> {

		// 后台任务存储
		private final TaskRepository taskRepository;

		public TaskOutputFunction(TaskRepository taskRepository) {
			this.taskRepository = taskRepository;
		}

		@Override
		public String apply(TaskOutputCall taskOutputCall) {

			BackgroundTask bgTask = taskRepository.getTasks(taskOutputCall.task_id());

			if (bgTask == null) {
				return "错误：未找到ID为 " + taskOutputCall.task_id() + " 的后台任务";
			}

			boolean shouldBlock = taskOutputCall.block() == null || taskOutputCall.block();
			long timeoutMs = taskOutputCall.timeout() != null ? Math.min(taskOutputCall.timeout(), 600000) : 30000;
			if (shouldBlock && !bgTask.isCompleted()) {
				try {
					bgTask.waitForCompletion(timeoutMs);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return "错误：等待任务被中断";
				}
			}

			StringBuilder result = new StringBuilder();
			result.append("任务ID: ").append(taskOutputCall.task_id()).append("\n");
			result.append("状态: ").append(bgTask.getStatus()).append("\n\n");

			if (bgTask.isCompleted() && bgTask.getResult() != null) {
				result.append("结果:\n").append(bgTask.getResult());
			}
			else if (bgTask.getError() != null) {
				result.append("错误:\n").append(bgTask.getError().getMessage());
				if (bgTask.getError().getCause() != null) {
					result.append("\n原因: ").append(bgTask.getError().getCause().getMessage());
				}
			}
			else if (!bgTask.isCompleted()) {
				result.append("任务仍在运行...");
			}

			return result.toString();
		}

	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String taskDescriptionTemplate = TASK_DESCRIPTION_TEMPLATE;

		private TaskRepository taskRepository;

		private Builder() {
		}

		public Builder taskDescriptionTemplate(String template) {
			this.taskDescriptionTemplate = template;
			return this;
		}

		public Builder taskRepository(TaskRepository taskRepository) {
			Assert.notNull(taskRepository, "taskRepository不能为null");
			this.taskRepository = taskRepository;
			return this;
		}

		public ToolCallback build() {
			Assert.notNull(this.taskRepository, "必须提供taskRepository");
			return FunctionToolCallback.builder("TaskOutput", new TaskOutputFunction(this.taskRepository))
				.description(this.taskDescriptionTemplate)
				.inputType(TaskOutputCall.class)
				.build();
		}

	}

}
