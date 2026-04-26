package cn.bitloom.agentic.agent.subagent.code;

import cn.bitloom.agentic.agent.subagent.SubagentDefinition;
import cn.bitloom.agentic.agent.subagent.SubagentExecutor;
import cn.bitloom.agentic.agent.subagent.TaskCall;
import cn.bitloom.agentic.skill.SkillManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CodeSubagentExecutor implements SubagentExecutor {

	private static final Logger logger = LoggerFactory.getLogger(CodeSubagentExecutor.class);

	private final Map<String, ChatClient.Builder> chatClientBuilderMap;

	private final List<ToolCallback> tools;

	private final SkillManager skillManager;

	private final List<String> skillsDirectories;

	public CodeSubagentExecutor(Map<String, ChatClient.Builder> chatClientBuilderMap, List<ToolCallback> tools,
			SkillManager skillManager, List<String> skillsDirectories) {

		Assert.notEmpty(chatClientBuilderMap, "chatClientBuilderMap不能为空");
		Assert.isTrue(chatClientBuilderMap.containsKey("default"),
				"chatClientBuilderMap必须包含一个键为'default'的默认ChatClient.Builder");

		Assert.notNull(skillManager, "skillManager不能为null");
		Assert.notNull(skillsDirectories, "skillsDirectories不能为null");

		this.chatClientBuilderMap = chatClientBuilderMap;
		this.tools = tools;
		this.skillManager = skillManager;
		this.skillsDirectories = skillsDirectories;
	}

	@Override
	public String getKind() {
		return CodeSubagentDefinition.KIND;
	}

	@Override
	public String execute(TaskCall taskCall, SubagentDefinition subagent) {

		var codeSubagent = (CodeSubagentDefinition) subagent;
		var taskChatClient = this.createTaskChatClient(codeSubagent);

		String preloadedSkillsSystemSuffix = "";

		if (!CollectionUtils.isEmpty(codeSubagent.skills()) && !CollectionUtils.isEmpty(this.skillsDirectories)) {

			var skills = this.skillManager.loadDirectories(this.skillsDirectories);

			preloadedSkillsSystemSuffix = "\n"
					+ skills.stream().filter(s -> codeSubagent.skills().contains(s.name())).map(skill -> "%s\nBase directory for this skill: %s\n\n%s".formatted(skill.toXml(),
                            skill.basePath(), skill.content())).collect(Collectors.joining("\n\n"));
		}

		return taskChatClient.prompt()
			.system(codeSubagent.content() + preloadedSkillsSystemSuffix)
			.user(taskCall.prompt())
			.call()
			.content();
	}

	private ChatClient createTaskChatClient(CodeSubagentDefinition codeSubagent) {

		var builder = this.doFindChatClientBuilder(codeSubagent).clone();

		if (!CollectionUtils.isEmpty(this.tools)) {

			List<ToolCallback> subagentTools = new ArrayList<>(this.tools);

			if (!CollectionUtils.isEmpty(codeSubagent.tools())) {
				subagentTools = this.tools.stream()
					.filter(tc -> codeSubagent.tools().contains(tc.getToolDefinition().name()))
					.toList();
			}

			if (!CollectionUtils.isEmpty(codeSubagent.disallowedTools())) {
				subagentTools = subagentTools.stream()
					.filter(tc -> !codeSubagent.disallowedTools().contains(tc.getToolDefinition().name()))
					.toList();
			}

			builder.defaultToolCallbacks(subagentTools);
		}

		if (!codeSubagent.permissionMode().equals("default")) {
            logger.warn("任务permissionMode尚不支持。permissionMode = {}", codeSubagent.permissionMode());
		}

		return builder.defaultAdvisors(ToolCallAdvisor.builder().build()).build();
	}

	private static final Map<String, String> MODEL_NAME_MAPPER = Map.of("opus", "claude-opus-4-64k", "haiku",
			"claude-haiku-4-5-20251001", "sonnet", "claude-sonnet-4-5-20250929");

	protected ChatClient.Builder doFindChatClientBuilder(CodeSubagentDefinition codeSubagent) {

		if (StringUtils.hasText(codeSubagent.getModel())) {
			var providerName = "default";

			var modelRef = codeSubagent.getModel();
			var modelName = modelRef.trim();

			if (modelRef.contains(":")) {
				var parts = modelRef.split(":");
				if (StringUtils.hasText(parts[0])) {
					providerName = parts[0].trim();
				}
				if (StringUtils.hasText(parts[1])) {
					modelName = parts[1].trim();
				}
			}

			if (this.chatClientBuilderMap.containsKey(providerName)) {
				var builder = this.chatClientBuilderMap.get(providerName);
				if (StringUtils.hasText(modelName)) {
					if (MODEL_NAME_MAPPER.containsKey(modelName)) {
						modelName = MODEL_NAME_MAPPER.get(modelName);
					}
					builder = builder.clone().defaultOptions(ChatOptions.builder().model(modelName).build());
				}
				return builder;
			}
		}

		return this.chatClientBuilderMap.get("default");
	}

}
