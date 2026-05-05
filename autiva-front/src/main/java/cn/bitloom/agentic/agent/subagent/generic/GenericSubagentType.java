package cn.bitloom.agentic.agent.subagent.generic;

import cn.bitloom.agentic.deploy.DeployTool;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.agent.subagent.SubagentType;
import cn.bitloom.agentic.session.SessionManager;
import cn.bitloom.agentic.tool.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GenericSubagentType {

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String braveApiKey = System.getenv("BRAVE_API_KEY");

		private final Map<String, ChatClient.Builder> chatClientBuilderMap = new ConcurrentHashMap<>();

		private SkillManager skillManager;

		private final List<String> skillsDirectories = new ArrayList<>();

		private DeployTool deployTool;

		private ChatMemory chatMemory;
		private SessionManager sessionManager;

		public Builder braveApiKey(String braveApiKey) {
			Assert.notNull(braveApiKey, "braveApiKey不能为null");
			this.braveApiKey = braveApiKey;
			return this;
		}

		public Builder chatClientBuilder(String modelId, ChatClient.Builder chatClientBuilder) {
			Assert.notNull(modelId, "modelId不能为null");
			Assert.notNull(chatClientBuilder, "chatClientBuilder不能为null");
			this.chatClientBuilderMap.put(modelId, chatClientBuilder);
			return this;
		}

		public Builder chatClientBuilders(Map<String, ChatClient.Builder> chatClientBuilderMap) {
			Assert.notNull(chatClientBuilderMap, "chatClientBuilderMap不能为null");
			this.chatClientBuilderMap.putAll(chatClientBuilderMap);
			return this;
		}

		public Builder skillManager(SkillManager skillManager) {
			Assert.notNull(skillManager, "skillManager不能为null");
			this.skillManager = skillManager;
			return this;
		}

		public Builder skillsResources(List<Resource> skillsRootPaths) {
			for (Resource skillsRootPath : skillsRootPaths) {
				this.skillsResource(skillsRootPath);
			}
			return this;
		}

		public Builder skillsResource(Resource skillsRootPath) {
			try {
				String path = skillsRootPath.getFile().toPath().toAbsolutePath().toString();
				this.skillsDirectories(path);
			}
			catch (IOException ex) {
				throw new RuntimeException("从目录加载技能失败: " + skillsRootPath, ex);
			}
			return this;
		}

		public Builder skillsDirectories(List<String> skillsDirectories) {
			Assert.notNull(skillsDirectories, "skillsDirectories不能为null");
			this.skillsDirectories.addAll(skillsDirectories);
			return this;
		}

		public Builder skillsDirectories(String skillsDirectory) {
			Assert.notNull(skillsDirectory, "skillsDirectory不能为null");
			this.skillsDirectories.add(skillsDirectory);
			return this;
		}

		public Builder deployTool(DeployTool deployTool) {
			Assert.notNull(deployTool, "deployTool不能为null");
			this.deployTool = deployTool;
			return this;
		}

		public Builder chatMemory(ChatMemory chatMemory) {
			Assert.notNull(chatMemory, "chatMemory不能为null");
			this.chatMemory = chatMemory;
			return this;
		}

		public Builder sessionManager(SessionManager sessionManager) {
			Assert.notNull(sessionManager, "sessionManager不能为null");
			this.sessionManager = sessionManager;
			return this;
		}

		public SubagentType build() {

			Assert.notEmpty(this.chatClientBuilderMap, "至少需要一个chatClientBuilder");
			Assert.isTrue(this.chatClientBuilderMap.containsKey("default"),
					"chatClientBuilderMap必须包含一个'default'构建器用于SmartWebFetchTool");
			Assert.notNull(this.chatMemory, "chatMemory不能为null，请通过chatMemory()方法设置");

			GenericSubagentExecutor executor = new GenericSubagentExecutor(this.chatClientBuilderMap,
					this.defaultGenericSubagentTools(), this.skillManager, this.skillsDirectories, this.chatMemory,
					this.sessionManager);

			return new SubagentType(new GenericSubagentResolver(), executor);
		}

		private List<ToolCallback> defaultGenericSubagentTools() {

			ChatClient.Builder webFetchChatClientBuilder = this.chatClientBuilderMap.get("default");

			List<ToolCallback> defaultCallbacks = new ArrayList<>();

			List<Object> toolObjects = new ArrayList<>(List.of(
					TodoWriteTool.builder().build(), GrepTool.builder().build(), GlobTool.builder().build(),
					ShellTools.builder().build(), FileSystemTools.builder().build(),
					WebFetchTool.builder(webFetchChatClientBuilder.clone().build()).build()
			));

			if (this.deployTool != null) {
				toolObjects.add(this.deployTool);
			}

			List<ToolCallback> commonTools = List.of(MethodToolCallbackProvider.builder()
				.toolObjects(toolObjects.toArray())
				.build()
				.getToolCallbacks());

			defaultCallbacks.addAll(commonTools);

			if (this.skillManager != null && !this.skillManager.getAllSkills().isEmpty()) {
				defaultCallbacks.add(this.skillManager.buildToolCallback());
			}

			if (StringUtils.hasText(this.braveApiKey)) {
				defaultCallbacks.add(MethodToolCallbackProvider.builder()
					.toolObjects(WebSearchTool.builder(braveApiKey).resultCount(15).build())
					.build()
					.getToolCallbacks()[0]);
			}

			return defaultCallbacks;
		}

	}

}
