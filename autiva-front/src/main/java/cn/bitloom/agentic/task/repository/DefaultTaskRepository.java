
package cn.bitloom.agentic.task.repository;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * TaskRepository的默认实现，使用线程池管理后台任务。
 *
 */
public class DefaultTaskRepository implements TaskRepository {

	private final Map<String, BackgroundTask> backgroundTasks = new ConcurrentHashMap<>();

	private final ExecutorService executor;

	private final boolean ownsExecutor;

	/**
	 * 使用默认缓存线程池执行器创建仓库。
	 */
	public DefaultTaskRepository() {
		this(Executors.newCachedThreadPool(r -> {
			Thread thread = new Thread(r);
			thread.setDaemon(true);
			thread.setName("background-task-" + thread.getId());
			return thread;
		}), true);
	}

	/**
	 * 使用自定义执行器服务创建仓库。
	 * @param executor 用于运行任务的执行器服务
	 */
	public DefaultTaskRepository(ExecutorService executor) {
		this(executor, false);
	}

	/**
	 * 指定执行器所有权的内部构造函数。
	 * @param executor 执行器服务
	 * @param ownsExecutor 此仓库是否拥有执行器并应关闭它
	 */
	public DefaultTaskRepository(ExecutorService executor, boolean ownsExecutor) {
		this.executor = executor;
		this.ownsExecutor = ownsExecutor;
	}

	@Override
	public BackgroundTask getTasks(String taskId) {
		return this.backgroundTasks.get(taskId);
	}

	@Override
	public BackgroundTask putTask(String taskId, Supplier<String> taskExecution) {
		var future = CompletableFuture.supplyAsync(taskExecution, this.executor);
		BackgroundTask backgroundTask = new BackgroundTask(taskId, future);
		this.backgroundTasks.put(taskId, backgroundTask);
		return backgroundTask;
	}

	@Override
	public void removeTask(String taskId) {
		this.backgroundTasks.remove(taskId);
	}

	@Override
	public void clear() {
		this.backgroundTasks.clear();
	}

	
	public void clearCompletedTasks() {
		this.backgroundTasks.entrySet().removeIf(entry -> entry.getValue().isCompleted());
	}

	/**
	 * 如果此仓库拥有执行器，则关闭执行器服务。当不再需要仓库时应调用此方法，
	 * 以确保正确清理线程。
	 */
	public void shutdown() {
		if (this.ownsExecutor && this.executor != null) {
			this.executor.shutdown();
			try {
				if (!this.executor.awaitTermination(60, TimeUnit.SECONDS)) {
					this.executor.shutdownNow();
				}
			}
			catch (InterruptedException e) {
				this.executor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
	}

}
