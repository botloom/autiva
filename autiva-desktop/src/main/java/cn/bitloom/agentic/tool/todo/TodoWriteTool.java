package cn.bitloom.agentic.tool.todo;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Map;

/**
 * 创建和管理AI编码会话的结构化任务列表。
 * <p>
 * 这是Claude Code类似TodoWrite工具的Spring AI实现，使AI代理能够
 * 跟踪进度、组织复杂任务并提供任务执行可见性。该工具验证任务状态，
 * 确保一次只有一个任务处于进行中，并且所有任务数据格式正确。
 *
 */
@Slf4j
public class TodoWriteTool extends AbstractTool<TodoWriteTool.Input> {

	private static final String DESCRIPTION = """
		管理当前会话的结构化任务列表。多步骤任务时创建待办事项，开始前标记 in_progress，完成后标记 completed。
		单个简单任务不要使用。用 TaskCreate 添加任务，TaskUpdate 更新状态(pending/in_progress/completed)。
		""";

	@FunctionalInterface
	public interface TodoEventHandler {
		void handle(Todos todos, String sessionId);
	}

	public record Todos(List<TodoItem> todos) {
	}

	public record TodoItem(
			@ToolParam(description = "任务内容描述") String content,
			@ToolParam(description = "任务状态：pending、in_progress、completed") Status status,
			@ToolParam(description = "执行期间显示的现在进行时形式") String activeForm) {
	}

	public enum Status {
		pending, in_progress, completed
	}

	/**
	 * 输入参数 record
	 */
	public record Input(
			@ToolParam(description = "待办事项列表") List<TodoItem> todos) {
	}

	private final TodoEventHandler todoListConsumer;

	private TodoWriteTool(TodoEventHandler todoListConsumer) {
		super("TodoWrite", DESCRIPTION, Input.class);
		this.todoListConsumer = todoListConsumer;
	}

	@Override
	public @NonNull ToolResult execute(Input input, ToolContext context) {
		List<TodoItem> todos = input.todos();

		// 包装为 Todos record
		Todos todosRecord = new Todos(todos);

		// 验证待办事项
		this.validateTodos(todosRecord);

		String sessionId = context != null ? (String) context.getContext().get("sessionId") : null;
		this.todoListConsumer.handle(todosRecord, sessionId);

		return ToolResult.success("待办事项已成功修改", Map.of("count", todos.size()));
	}

	/**
	 * 根据以下规则验证待办列表： - 最多只能有一个任务处于in_progress状态（允许为0） - 任务内容和activeForm不能为空或空白 -
	 * 所有任务必须具有有效的状态值
	 * @param todos 要验证的待办列表
	 * @throws IllegalArgumentException 如果验证失败
	 */
	private void validateTodos(Todos todos) {
		if (todos == null || todos.todos() == null) {
			throw new IllegalArgumentException("Todos不能为null");
		}

		List<TodoItem> items = todos.todos();

		// 首先验证每个任务（在计算in_progress任务之前）
		for (int i = 0; i < items.size(); i++) {
			TodoItem item = items.get(i);

			if (item == null) {
				throw new IllegalArgumentException("索引 " + i + " 处的任务为null");
			}

			if (item.content() == null || item.content().isBlank()) {
				throw new IllegalArgumentException(
						"索引 " + i + " 处的任务内容为空或空白。所有任务必须有有意义的内容。");
			}

			if (item.activeForm() == null || item.activeForm().isBlank()) {
				throw new IllegalArgumentException("索引 " + i + " 处的任务activeForm为空或空白。"
						+ "所有任务必须有一个activeForm（现在进行时）。");
			}

			if (item.status() == null) {
				throw new IllegalArgumentException("索引 " + i
						+ " 处的任务状态为null。状态必须是以下之一：pending、in_progress、completed");
			}
		}

		// 计算in_progress任务数量
		long inProgressCount = items.stream().filter(item -> item.status() == Status.in_progress).count();

		// 检查是否有任何任务
		if (items.isEmpty()) {
			throw new IllegalArgumentException("待办列表不能为空。至少需要一个任务。");
		}

		// 允许所有任务完成时 in_progress 为 0，但不能超过 1 个
		if (inProgressCount > 1) {
			throw new IllegalArgumentException("一次只能有一个任务处于in_progress状态。当前有 " + inProgressCount
					+ " 个任务处于in_progress状态。请更新任务状态，确保最多只有一个任务处于in_progress状态。");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private TodoEventHandler todoEventHandler = (todos, sessionId) -> log.debug("Updated Todos: {}", todos);

		public Builder todoEventHandler(TodoEventHandler todoEventHandler) {
			this.todoEventHandler = todoEventHandler;
			return this;
		}

		public TodoWriteTool build() {
			return new TodoWriteTool(this.todoEventHandler);
		}

	}

}
