package cn.bitloom.agentic.tool.task.repository;

import lombok.Getter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 使用CompletableFuture管理后台任务的执行。此类提供线程安全的任务状态、
 * 结果和错误信息访问，同时利用Java的现代并发工具。
 *
 * <p>
 * 任务在构造时使用提供的ExecutorService自动启动。调用者可以通过公共API
 * 检查完成状态、等待完成、取消任务以及检索结果或错误。
 * </p>
 *
 */
@Getter
public class BackgroundTask {

    /**
     * -- GETTER --
     *  获取任务ID。
     *
     */
    private final String taskId;

    /**
     * -- GETTER --
     *  获取底层的CompletableFuture用于高级操作。
     *
     */
    private final CompletableFuture<String> future;

	/**
	 * 使用现有Future创建BackgroundTask的内部构造函数。
	 * @param taskId 任务标识符
	 * @param future 要包装的CompletableFuture
	 */
	public BackgroundTask(String taskId, CompletableFuture<String> future) {
		this.taskId = taskId;
		this.future = future;
	}

	/**
	 * 检查任务是否已完成执行。
	 * @return 如果任务已完成（成功或出错）则返回true，否则返回false
	 */
	public boolean isCompleted() {
		return this.future.isDone();
	}

	/**
	 * 设置任务执行的结果。使用给定结果完成Future。
	 * @param result 要设置的结果
	 */
	public void setResult(String result) {
		this.future.complete(result);
	}

	/**
	 * 获取任务执行的结果。此方法阻塞直到结果可用。
	 * @return 任务结果，如果尚未完成或发生错误则返回null
	 */
	public String getResult() {
		try {
			return this.future.getNow(null);
		}
		catch (Exception e) {
			return null;
		}
	}

	/**
	 * 获取任务执行期间发生的错误（如果有）。
	 * @return 发生的异常，如果没有错误则返回null
	 */
	public Exception getError() {
		if (this.future.isCompletedExceptionally()) {
			try {
				this.future.getNow(null);
			}
			catch (Exception e) {
				if (e.getCause() instanceof Exception) {
					return (Exception) e.getCause();
				}
				return e;
			}
		}
		return null;
	}

	/**
	 * 获取错误消息（如果发生错误）。
	 * @return 错误消息，如果没有错误则返回null
	 */
	public String getErrorMessage() {
		Exception error = getError();
		return error != null ? error.getMessage() : null;
	}

	/**
	 * 检查任务是否有错误。
	 * @return 如果发生错误则返回true，否则返回false
	 */
	public boolean hasError() {
		return this.future.isCompletedExceptionally();
	}

	/**
	 * 获取任务的可读状态描述。
	 * @return 状态字符串："运行中"、"已完成"或"失败: [错误消息]"
	 */
	public String getStatus() {
		if (this.future.isCompletedExceptionally()) {
			Exception error = getError();
			return "失败: " + (error != null ? error.getMessage() : "未知错误");
		}
		return this.future.isDone() ? "已完成" : "运行中";
	}

	/**
	 * 在指定超时时间内等待任务完成。
	 *
	 * @param timeoutMs 最大等待时间，单位毫秒
	 * @throws InterruptedException 如果当前线程在等待时被中断
	 */
	public void waitForCompletion(long timeoutMs) throws InterruptedException {
		if (this.future.isDone()) {
			return;
		}
		try {
			this.future.get(timeoutMs, TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException e) {
			throw e;
		}
		catch (Exception ignored) {
		}
	}

	/**
	 * 如果任务尚未完成，则取消任务。
	 * @param mayInterruptIfRunning 如果应中断执行任务的线程则为true
	 * @return 如果任务被取消则返回true，如果已完成则返回false
	 */
	public boolean cancel(boolean mayInterruptIfRunning) {
		return this.future.cancel(mayInterruptIfRunning);
	}

	/**
	 * 检查任务是否被取消。
	 * @return 如果任务在完成前被取消则返回true
	 */
	public boolean isCancelled() {
		return this.future.isCancelled();
	}

}