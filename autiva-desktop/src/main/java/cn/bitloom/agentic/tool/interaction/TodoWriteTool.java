package cn.bitloom.agentic.tool.interaction;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
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
		使用此工具为当前编码会话创建和管理结构化任务列表。这有助于你跟踪进度、组织复杂任务并向用户展示细致性。
		它还帮助用户了解任务进度及其请求的整体进度。

		## 何时使用此工具
		在以下情况下主动使用此工具：

		1. 复杂的多步骤任务 - 当任务需要3个或更多不同的步骤或操作时
		2. 非平凡和复杂的任务 - 需要仔细规划或多个操作的任务
		3. 用户明确请求待办列表 - 当用户直接要求你使用待办列表时
		4. 用户提供多个任务 - 当用户提供要完成的事项列表（编号或逗号分隔）时
		5. 收到新指令后 - 立即将用户需求捕获为待办事项
		6. 开始处理任务时 - 在开始工作前将其标记为in_progress。理想情况下，一次只能有一个待办事项处于in_progress状态
		7. 完成任务后 - 将其标记为已完成，并添加在实现过程中发现的任何新后续任务。**即使这是最后一个任务，也必须调用TodoWrite将其标记为completed**

		## 何时不使用此工具

		在以下情况下跳过使用此工具：
		1. 只有单个、简单的任务
		2. 任务微不足道，跟踪它没有组织上的好处
		3. 任务可以在少于3个简单步骤内完成
		4. 任务纯粹是对话性或信息性的

		注意：如果只有一个微不足道的任务要做，就不应该使用此工具。在这种情况下，最好直接完成任务。

		## 验证规则

		此工具验证以下内容：
		- 最多只能有一个任务处于in_progress状态（允许所有任务完成时为0）
		- 任务内容和activeForm不能为空或空白
		- 所有任务必须具有有效的状态值（pending、in_progress、completed）

		## 使用待办列表的示例

		<example>
		User: 我想在应用设置中添加深色模式切换。完成后确保运行测试和构建！
		Assistant: 我将帮助你在应用设置中添加深色模式切换。让我创建一个待办列表来跟踪此实现。
		*创建包含以下项目的待办列表：*
		1. 在设置页面创建深色模式切换组件
		2. 添加深色模式状态管理（context/store）
		3. 为深色主题实现CSS-in-JS样式
		4. 更新现有组件以支持主题切换
		5. 运行测试和构建过程，解决发生的任何失败或错误
		*开始处理第一个任务*

		<reasoning>
		助手使用了待办列表，因为：
		1. 添加深色模式是一个需要UI、状态管理和样式更改的多步骤功能
		2. 用户明确要求之后运行测试和构建
		3. 助手推断测试和构建需要通过，将"确保测试和构建成功"添加为最后一个任务
		</reasoning>
		</example>

		<example>
		User: 帮我在整个项目中将函数getCwd重命名为getCurrentWorkingDirectory
		Assistant: 让我先搜索你的代码库以找到所有'getCwd'的出现位置。
		*使用grep或搜索工具定位代码库中getCwd的所有实例*
		Assistant: 我在8个不同文件中找到了15个'getCwd'实例。让我创建一个待办列表来跟踪这些更改。
		*创建包含每个需要更新的文件的特定项目的待办列表*

		<reasoning>
		助手使用了待办列表，因为：
		1. 首先，助手搜索以了解任务范围
		2. 发现不同文件中有多个出现后，确定这是一个包含多个步骤的复杂任务
		3. 待办列表有助于确保系统地跟踪和更新每个实例
		4. 这种方法可以防止遗漏任何出现并保持代码一致性
		</reasoning>
		</example>


		<example>
		User: 我需要为我的电商网站实现这些功能：用户注册、产品目录、购物车和结账流程。
		Assistant: 我将帮助实现这些功能。首先，让我们将所有功能添加到待办列表中。
		*创建一个待办列表，根据项目架构将每个功能分解为具体任务*
		Assistant: 让我们从用户注册开始。这将涉及创建数据库模型、API端点和与你现有身份验证系统集成的前端表单。

		<reasoning>
		助手使用了待办列表，因为：
		1. 用户以逗号分隔列表的形式提供了多个要实现的复杂功能
		2. 待办列表有助于将这些大型功能组织成可管理的任务
		3. 这种方法允许跟踪整个实现过程的进度
		</reasoning>
		</example>

		<example>
		User: 你能帮我优化我的React应用吗？它渲染很慢，有性能问题。</user>
		Assistant: 我将帮助优化你的React应用。首先，让我检查你的代码库以识别潜在的性能瓶颈。
		*审查组件结构、渲染模式、状态管理和数据获取*
		Assistant: 分析你的代码库后，我发现了几个性能问题。让我创建一个待办列表来跟踪我们的优化工作。
		*创建包含以下项目的待办列表：1) 为ProductList中的昂贵计算实现记忆化，2) 为Dashboard中的长列表添加虚拟化，3) 优化Gallery组件中的图像加载，4) 修复ShoppingCart中的状态更新循环，5) 审查包大小并实现代码分割*
		让我们从为ProductList组件中的昂贵计算实现记忆化开始。</assistant>

		<reasoning>
		助手使用了待办列表，因为：
		1. 首先，助手检查代码库以识别具体的性能问题
		2. 基于此分析，它识别出多个优化机会
		3. 性能优化是一个需要多个步骤的非平凡任务
		4. 待办列表有助于有条理地跟踪不同组件的改进
		5. 这种系统化的方法确保解决所有性能瓶颈
		</reasoning>
		</example>

		## 不使用待办列表的示例

		<example>
		User: 如何在Python中打印'Hello World'？
		Assistant: 在Python中，你可以用这段简单的代码打印"Hello World"：

		python
		print("Hello World")

		执行时这将在控制台输出文本"Hello World"。</assistant>

		<reasoning>
		助手没有使用待办列表，因为这是一个可以在一步内完成的单个、简单的任务。对于如此简单的请求，不需要跟踪多个任务或步骤。
		</reasoning>
		</example>

		<example>
		User: git status命令做什么？
		Assistant: git status命令显示工作目录和暂存区的当前状态。它显示哪些更改已暂存、哪些未暂存以及哪些文件未被Git跟踪。这是在提交之前了解仓库当前状态的基本命令。

		<reasoning>
		助手没有使用待办列表，因为这是一个信息性请求，没有实际的编码任务要完成。用户只是在询问解释，而不是让助手执行多个步骤或任务。
		</reasoning>
		</example>

		<example>
		User: 你能在calculateTotal函数中添加注释来解释它的作用吗？
		Assistant: 当然，让我在calculateTotal函数中添加注释来解释它的作用。
		* 使用Edit工具在calculateTotal函数中添加注释 *

		<reasoning>
		助手没有使用待办列表，因为这是一个局限于代码中一个位置的单个、简单的任务。添加注释不需要跟踪多个步骤或系统组织。
		</reasoning>
		</example>

		<example>
		User: 为我运行npm install并告诉我发生了什么。
		Assistant: 我将为你运行npm install命令。

		*执行：npm install*

		命令成功完成。这是输出：
		[npm install命令的输出]

		所有依赖项已根据你的package.json文件安装。

		<reasoning>
		助手没有使用待办列表，因为这是一个具有即时结果的单个命令执行。没有多个步骤需要跟踪或组织，对于这个简单的任务，待办列表是不必要的。
		</reasoning>
		</example>

		## 任务状态和管理

		1. **任务状态**：使用这些状态来跟踪进度：
		- pending: 任务尚未开始
		- in_progress: 正在处理（最多一次一个任务，全部完成时可为0）
		- completed: 任务成功完成

		**重要**：任务描述必须有两种形式：
		- content: 描述需要做什么的祈使形式（例如，"运行测试"、"构建项目"）
		- activeForm: 执行期间显示的现在进行时形式（例如，"正在运行测试"、"正在构建项目"）

		2. **任务管理**：
		- 在工作时实时更新任务状态
		- 完成后立即将任务标记为已完成（不要批量完成）
		- 任何时候最多有一个任务处于in_progress状态（允许所有任务完成时为0）
		- **当最后一个任务完成时，必须调用TodoWrite将其标记为completed，此时所有任务都为completed状态是正常的**
		- 在开始新任务之前完成当前任务
		- 从列表中完全删除不再相关的任务

		3. **任务完成要求**：
		- 只有在你完全完成任务时才将其标记为已完成
		- 如果遇到错误、阻塞或无法完成，保持任务为in_progress
		- 当被阻塞时，创建一个描述需要解决什么的新任务
		- 在以下情况下永远不要将任务标记为已完成：
			- 测试失败
			- 实现不完整
			- 遇到未解决的错误
			- 找不到必要的文件或依赖项

		4. **任务分解**：
		- 创建具体、可操作的项目
		- 将复杂任务分解为更小、可管理的步骤
		- 使用清晰、描述性的任务名称
		- 始终提供两种形式：
			- content: "修复身份验证bug"
			- activeForm: "正在修复身份验证bug"

		如有疑问，请使用此工具。主动进行任务管理表明了专注性，并确保你成功完成所有要求。
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
	public ToolResult execute(Input input, ToolContext context) {
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
