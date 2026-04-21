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
package cn.bitloom.agentic.task.repository;

import java.util.function.Supplier;

/**
 * 后台任务管理仓库接口。
 *
 * @author Christian Tzolov
 */
public interface TaskRepository {

	/**
	 * 根据ID获取后台任务。
	 * @param taskId 任务标识符
	 * @return 后台任务，如果未找到则返回null
	 */
	BackgroundTask getTasks(String taskId);
	
	/**
	 * 向仓库中添加新的后台任务。
	 * @param taskId 任务标识符
	 * @param taskExecution 执行任务并返回其输出的供应器
	 * @return 创建的后台任务
	 */
	BackgroundTask putTask(String taskId, Supplier<String> taskExecution);

	/**
	 * 从仓库中移除后台任务。
	 * @param taskId 任务标识符
	 */
	void removeTask(String taskId);

	/**
	 * 清除仓库中的所有任务。
	 */
	void clear();
}
