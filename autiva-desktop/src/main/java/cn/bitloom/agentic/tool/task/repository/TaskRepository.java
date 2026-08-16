
package cn.bitloom.agentic.tool.task.repository;

import java.util.function.Supplier;

/**
 * 后台任务管理仓库接口。
 *
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

	/**
	 * 检查是否有仍在运行中的后台任务（Goal Loop defer 判定用）。
	 * @return 如果存在未完成任务则返回true
	 */
	default boolean hasRunningTasks() {
		return false;
	}
}
