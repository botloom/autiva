package cn.bitloom.agentic.agent.advisor;

import cn.bitloom.agentic.memory.AgentMemoryStore;
import cn.bitloom.agentic.memory.FileSystemAgentMemoryStore;
import cn.bitloom.agentic.tool.memory.*;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.*;
import java.util.function.BiPredicate;

/**
 * 自动记忆工具 Advisor，为智能体提供长期记忆管理能力。
 *
 * <p>注入记忆工具到智能体的工具列表中，允许智能体通过工具调用管理长期记忆。
 * 支持记忆整合触发器，在合适时机触发记忆合并和精简。
 */
public class AutoMemoryToolsAdvisor implements BaseChatMemoryAdvisor {

	private static final String DEFAULT_MEMORY_SYSTEM_PROMPT = """
			# 自动记忆

			你拥有一个基于文件的持久记忆系统，由 AutoMemoryTools 提供支持。
			记忆根目录为 {MEMORIES_ROOT_DIRECTORY}，所有传递给记忆工具的路径都相对于该根目录。

			你应该随着时间推移逐步构建这个记忆系统，以便未来的对话能够全面了解用户是谁、他们希望如何与你协作、应该避免或保持哪些行为，以及他们交给你的工作背后的背景。

			如果用户明确要求你记住某些内容，立即将其保存为最合适的类型。如果他们要求你忘记某些内容，找到并删除相关条目。

			## 可用的记忆工具

			| 工具 | 用途 |
			|---|---|
			| `MemoryView` | 读取文件或列出目录。在任何会话中首先调用 `MEMORY.md`。 |
			| `MemoryCreate` | 创建新的记忆文件（两步保存的第一步）。 |
			| `MemoryStrReplace` | 更新现有记忆文件或编辑 `MEMORY.md`。 |
			| `MemoryInsert` | 向 `MEMORY.md` 追加新的索引条目（两步保存的第二步）。 |
			| `MemoryDelete` | 删除过时的记忆文件。同时清理其在 `MEMORY.md` 中的条目。 |
			| `MemoryRename` | 重命名或移动记忆文件。同时更新其在 `MEMORY.md` 中的链接。 |

			## 记忆类型

			记忆系统中有几种不同类型的记忆：

			<types>
			<type>
			    <name>user</name>
			    <description>关于用户角色、目标、职责和知识的信息。优质的用户记忆能帮助你根据用户的偏好和视角调整未来的行为。你在读写这些记忆时的目标是逐步了解用户是谁，以及你如何能对他们提供最有针对性的帮助。避免写入可能被视为负面评价或与你正在进行的合作无关的记忆。</description>
			    <when_to_save>当你了解到用户的角色、偏好、职责或知识的任何细节时。</when_to_save>
			    <how_to_use>根据用户的专业水平和视角调整你的回答。用他们已有的领域知识来框架解释。</how_to_use>
			    <examples>
			    user: 我是数据科学家，正在调查我们的日志系统
			    assistant: [保存用户记忆：用户是数据科学家，当前关注可观测性/日志]

			    user: 我写了十年 Go，但这是我第一次接触这个仓库的 React 部分
			    assistant: [保存用户记忆：深厚的 Go 专业知识，对 React 和本项目前端是新手——用后端类比来解释前端概念]
			    </examples>
			</type>
			<type>
			    <name>feedback</name>
			    <description>用户给你的关于如何开展工作的指导——包括应该避免什么和应该保持什么。从失败和成功中都要记录：如果只保存纠正，你会避免过去的错误但会偏离用户已经认可的方法，可能变得过于保守。</description>
			    <when_to_save>当用户纠正你的方法（"不要那样"、"别"、"停止做 X"）或确认某个非显而易见的方法有效时（"对，就是这样"、"完美，继续这样做"、毫无异议地接受了一个不寻常的选择）。记录*原因*，以便你日后判断边界情况。</when_to_save>
			    <how_to_use>让这些记忆指导你的行为，使用户不需要重复同样的指导。</how_to_use>
			    <body_structure>以规则本身开头，然后是 **原因：** 一行（用户给出的理由）和 **如何应用：** 一行（该指导在何时生效）。了解*原因*让你能判断边界情况，而不是盲目遵循规则。</body_structure>
			    <examples>
			    user: 这些测试里不要 mock 数据库——上季度 mock 测试通过了但生产环境迁移失败了，我们被坑了
			    assistant: [保存反馈记忆：集成测试必须连真实数据库，不能用 mock。原因：之前的事故中 mock/生产差异掩盖了迁移缺陷]

			    user: 别在每次回复末尾总结你刚做的事，我能看到 diff
			    assistant: [保存反馈记忆：用户希望简洁的回复，不要尾随总结]

			    user: 对，单个打包 PR 是对的，拆分这个只会是白费功夫
			    assistant: [保存反馈记忆：对于此区域的重构，用户偏好单个打包 PR 而非多个小 PR。一个被验证的判断，而非纠正]
			    </examples>
			</type>
			<type>
			    <name>project</name>
			    <description>关于项目中正在进行的工作、目标、倡议、bug 或事件的信息，这些信息无法从代码或 git 历史中推导出来。项目记忆帮助你理解用户工作背后的更广泛背景和动机。</description>
			    <when_to_save>当你了解到谁在做什么、为什么做或什么时候完成。始终将相对日期转换为绝对日期（例如"周四"→"2026-03-05"），以便记忆在时间推移后仍然可解读。</when_to_save>
			    <how_to_use>使用这些记忆更充分地理解用户请求背后的细节和微妙之处，做出更明智的建议。</how_to_use>
			    <body_structure>以事实或决策开头，然后是 **原因：** 一行（动机——通常是约束、截止日期或利益相关者的要求）和 **如何应用：** 一行（这应该如何影响你的建议）。项目记忆衰减很快，所以"原因"帮助你判断记忆是否仍然有效。</body_structure>
			    <examples>
			    user: 周四之后我们冻结所有非关键合并——移动团队要切发布分支
			    assistant: [保存项目记忆：合并冻结从 2026-03-05 开始，因为移动端发布切割。标记任何在此日期之后安排的非关键 PR 工作]

			    user: 我们重写旧认证中间件的原因是法务标记了它存储会话令牌的方式不符合新的合规要求
			    assistant: [保存项目记忆：认证中间件重写是由会话令牌存储的法律/合规要求驱动的，不是技术债清理——范围决策应优先合规而非便利性]
			    </examples>
			</type>
			<type>
			    <name>reference</name>
			    <description>指向外部系统中信息位置的指针。这些记忆让你记住在项目目录之外去哪里查找最新信息。</description>
			    <when_to_save>当你了解到外部系统中的资源及其用途（Linear 项目、Slack 频道、仪表盘、运维手册等）。</when_to_save>
			    <how_to_use>当用户引用外部系统或外部系统中可能有的信息时。</how_to_use>
			    <examples>
			    user: 如果你想了解这些工单的背景，查一下 Linear 项目 "INGEST"，那是我们追踪所有流水线 bug 的地方
			    assistant: [保存引用记忆：流水线 bug 在 Linear 项目 "INGEST" 中追踪]

			    user: grafana.internal/d/api-latency 是值班人员看的 Grafana 面板——如果你要改请求处理，那就是会触发告警的东西
			    assistant: [保存引用记忆：grafana.internal/d/api-latency 是值班延迟仪表盘——编辑请求路径代码时检查它]
			    </examples>
			</type>
			</types>

			## 不应保存到记忆中的内容

			- 代码模式、约定、架构、文件路径或项目结构——这些可以通过读取当前项目状态推导。
			- Git 历史、最近更改或谁改了什么——`git log` / `git blame` 是权威来源。
			- 调试解决方案或修复方法——修复在代码中；提交信息有上下文。
			- 已在项目 README 或配置文件中记录的任何内容。
			- 临时任务细节：进行中的工作、临时状态、当前对话上下文。

			即使用户明确要求保存，这些排除项也适用。如果他们要求保存 PR 列表或活动摘要，询问其中有什么*令人惊讶*或*非显而易见*的部分——那才是值得保留的。

			## 如何保存记忆

			保存记忆是一个**两步过程**：

			**第一步** — 调用 `MemoryCreate` 写入带 YAML 前置元数据的记忆文件：

			```markdown
			---
			name: {{记忆名称}}
			description: {{一行描述——用于在未来对话中判断相关性，所以要具体}}
			type: {{user, feedback, project, reference}}
			---

			{{记忆内容——对于 feedback/project 类型：规则/事实，然后 **原因：** 和 **如何应用：** 行}}
			```

			**第二步** — 调用 `MemoryInsert`（或 `MemoryStrReplace`）向 `MEMORY.md` 添加指针行：

			```
			- [标题](filename.md) — 一行钩子（≤150字符）
			```

			`MEMORY.md` 是索引，不是记忆——每个条目应该是一行，不超过 150 个字符。永远不要把记忆内容直接写入 `MEMORY.md`。

			额外规则：
			- 创建新记忆前始终先调用 `MemoryView` 查看 `MEMORY.md`，避免重复。
			- 如果已有记忆涵盖了该主题，用 `MemoryStrReplace` 更新它，而非创建新的。
			- 保持 `name`、`description` 和 `type` 前置元数据字段与文件内容同步。
			- 按主题语义组织记忆文件，而非按时间顺序（例如 `feedback_testing.md`、`user_role.md`）。

			## 何时访问记忆

			- 在任何可能需要先前背景的会话开始时读取 `MEMORY.md`（通过 `MemoryView`）。
			- 当某条记忆与当前任务相关时，调用 `MemoryView` 加载该具体文件。
			- 当用户明确要求你检查、回忆或记住时，你**必须**访问记忆。
			- 如果用户说*忽略*记忆：如同 `MEMORY.md` 为空一样继续。不要应用、引用或提及记忆内容。

			## 在根据记忆推荐之前

			命名了特定函数、文件或标志的记忆是对它*在记忆写入时*存在的声明。它可能已被重命名、删除或从未合并。在据此行动之前：

			- 如果记忆命名了文件路径：验证文件是否存在。
			- 如果记忆命名了函数或标志：在代码库中搜索它。
			- 如果用户即将根据你的建议行动，先验证。

			"记忆说 X 存在"不等于"X 现在存在"。如果回忆的记忆与当前信息冲突，信任你观察到的当前信息——并用 `MemoryStrReplace` 或 `MemoryDelete` 更新或删除过时的记忆。

			## 保持记忆整洁

			- 用 `MemoryDelete` 删除记忆文件时，始终用 `MemoryStrReplace` 从 `MEMORY.md` 中移除其行。
			- 用 `MemoryRename` 重命名记忆文件时，始终用 `MemoryStrReplace` 更新其在 `MEMORY.md` 中的链接。
			- 移除或更新被证明是错误或过时的记忆——过时的条目比没有条目更糟糕。
			- `MEMORY.md` 总是被加载到上下文中——保持条目简洁，超过 200 行的部分会被截断。
			""";

	private final int order;

	private final String memorySystemPrompt;

	private final List<ToolCallback> memoryToolCallbacks;

	private final BiPredicate<ChatClientRequest, Instant> memoryConsolidationTrigger;

	private AutoMemoryToolsAdvisor(int order, String memorySystemPrompt, List<ToolCallback> memoryToolCallbacks,
			BiPredicate<ChatClientRequest, Instant> memoryConsolidationTrigger) {
		this.order = order;
		this.memorySystemPrompt = memorySystemPrompt;
		this.memoryToolCallbacks = memoryToolCallbacks;
		this.memoryConsolidationTrigger = memoryConsolidationTrigger;
	}

	@Override
	public @NonNull ChatClientRequest before(ChatClientRequest chatClientRequest, @NonNull AdvisorChain advisorChain) {

		if (chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions toolOptions) {

			Prompt augPrompt = chatClientRequest.prompt()
				.augmentSystemMessage(chatClientRequest.prompt().getSystemMessage().getText() + System.lineSeparator()
						+ System.lineSeparator() + this.memorySystemPrompt + System.lineSeparator()
						+ System.lineSeparator()
						+ (this.memoryConsolidationTrigger.test(chatClientRequest, Instant.now())
								? "<system-reminder>整合长期记忆：总结并移除冗余信息。</system-reminder>"
								: ""));

			ToolCallingChatOptions toolOptionsCopy = toolOptions.mutate().build();

			List<ToolCallback> toolCallbacks = new ArrayList<>(
					Objects.requireNonNullElse(toolOptionsCopy.getToolCallbacks(), List.of()));

			Set<String> existingNames = toolCallbacks.stream()
				.map(tc -> tc.getToolDefinition().name())
				.collect(java.util.stream.Collectors.toSet());

			this.memoryToolCallbacks.stream()
				.filter(tc -> !existingNames.contains(tc.getToolDefinition().name()))
				.forEach(toolCallbacks::add);

			toolOptionsCopy = ((ToolCallingChatOptions.Builder<?>) toolOptionsCopy.mutate())
				.toolCallbacks(new ArrayList<>(toolCallbacks))
				.build();

			return chatClientRequest.mutate().prompt(augPrompt.mutate().chatOptions(toolOptionsCopy).build()).build();

		}

		return chatClientRequest;
	}

	@Override
	public @NonNull ChatClientResponse after(@NonNull ChatClientResponse chatClientResponse, @NonNull AdvisorChain advisorChain) {
		return chatClientResponse;
	}

	@Override
	public int getOrder() {
		return order;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private int order = BaseAdvisor.HIGHEST_PRECEDENCE + 200;

		private AgentMemoryStore memoryStore;

		private String memoriesRootDirectory = "";

		private String memorySystemPrompt = DEFAULT_MEMORY_SYSTEM_PROMPT;

		private BiPredicate<ChatClientRequest, Instant> memoryConsolidationTrigger = (request, instant) -> false;

		private Builder() {
		}

		public Builder order(int order) {
			this.order = order;
			return this;
		}

		/**
		 * 注入自定义 {@link AgentMemoryStore} 实现（如 JDBC/Redis 后端）。
		 * 未设置时使用 {@link FileSystemAgentMemoryStore}，由 {@link #memoriesRootDirectory} 决定路径。
		 */
		public Builder memoryStore(AgentMemoryStore memoryStore) {
			this.memoryStore = memoryStore;
			return this;
		}

		public Builder memoriesRootDirectory(String memoriesRootDirectory) {
			this.memoriesRootDirectory = memoriesRootDirectory;
			return this;
		}

		public Builder memorySystemPrompt(String memorySystemPrompt) {
			Assert.notNull(memorySystemPrompt, "Memory system prompt must not be null");
			this.memorySystemPrompt = memorySystemPrompt;
			return this;
		}

		public Builder memoryConsolidationTrigger(BiPredicate<ChatClientRequest, Instant> memoryConsolidationTrigger) {
			Assert.notNull(memoryConsolidationTrigger, "Memory consolidation trigger must not be null");
			this.memoryConsolidationTrigger = memoryConsolidationTrigger;
			return this;
		}

		public AutoMemoryToolsAdvisor build() {

			Assert.hasText(this.memoriesRootDirectory, "Memories root directory must not be empty");

			// 未注入自定义 store 时，使用文件系统默认实现
			AgentMemoryStore store = this.memoryStore != null
					? this.memoryStore
					: new FileSystemAgentMemoryStore(this.memoriesRootDirectory);
			try {
				store.init();
			} catch (java.io.IOException e) {
				throw new IllegalStateException("初始化记忆存储失败：" + this.memoriesRootDirectory, e);
			}

			List<ToolCallback> memoryToolCallbacks = Arrays.asList(
				MemoryViewTool.builder().store(store).build().toToolCallback(),
				MemoryCreateTool.builder().store(store).build().toToolCallback(),
				MemoryStrReplaceTool.builder().store(store).build().toToolCallback(),
				MemoryInsertTool.builder().store(store).build().toToolCallback(),
				MemoryDeleteTool.builder().store(store).build().toToolCallback(),
				MemoryRenameTool.builder().store(store).build().toToolCallback()
			);

			String memorySystemPromptText = this.memorySystemPrompt.replace("{MEMORIES_ROOT_DIRECTORY}",
					this.memoriesRootDirectory);

			return new AutoMemoryToolsAdvisor(this.order, memorySystemPromptText, memoryToolCallbacks,
					this.memoryConsolidationTrigger);
		}

	}

}
