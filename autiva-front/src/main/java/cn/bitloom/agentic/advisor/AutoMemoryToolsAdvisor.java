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
package cn.bitloom.agentic.advisor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

import cn.bitloom.agentic.tool.AutoMemoryTools;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * @author Christian Tzolov
 */

public class AutoMemoryToolsAdvisor implements BaseChatMemoryAdvisor {

	private static final Resource DEFAULT_MEMORY_SYSTEM_PROMPT = new DefaultResourceLoader()
		.getResource("classpath:/prompt/AUTO_MEMORY_TOOLS_SYSTEM_PROMPT.md");

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
	public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {

		if (chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions toolOptions) {

			Prompt augPrompt = chatClientRequest.prompt()
				.augmentSystemMessage(chatClientRequest.prompt().getSystemMessage().getText() + System.lineSeparator()
						+ System.lineSeparator() + this.memorySystemPrompt + System.lineSeparator()
						+ System.lineSeparator()
						+ (this.memoryConsolidationTrigger.test(chatClientRequest, Instant.now())
								? "<system-reminder>通过摘要和删除冗余信息来整合长期记忆。</system-reminder>"
								: ""));

			ToolCallingChatOptions toolOptionsCopy = toolOptions.copy();

			List<ToolCallback> toolCallbacks = new ArrayList<>(toolOptionsCopy.getToolCallbacks());

			Set<String> existingNames = toolCallbacks.stream()
				.map(tc -> tc.getToolDefinition().name())
				.collect(java.util.stream.Collectors.toSet());

			this.memoryToolCallbacks.stream()
				.filter(tc -> !existingNames.contains(tc.getToolDefinition().name()))
				.forEach(toolCallbacks::add);

			toolOptionsCopy.setToolCallbacks(new ArrayList<>(toolCallbacks));

			return chatClientRequest.mutate().prompt(augPrompt.mutate().chatOptions(toolOptionsCopy).build()).build();

		}

		return chatClientRequest;
	}

	@Override
	public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
		// 记忆持久化由模型本身在调用期间通过MemoryTools处理。
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

		// 在默认的ToolCallingAdvisor之前，其优先级为HIGHEST_PRECEDENCE + 300
		private int order = BaseAdvisor.HIGHEST_PRECEDENCE + 200;

		private String memoriesRootDirectory = "";

		private Resource memorySystemPrompt = DEFAULT_MEMORY_SYSTEM_PROMPT;

		private BiPredicate<ChatClientRequest, Instant> memoryConsolidationTrigger = (request, instant) -> false;

		private Builder() {
		}

		public Builder order(int order) {
			this.order = order;
			return this;
		}

		public Builder memoriesRootDirectory(String memoriesRootDirectory) {
			this.memoriesRootDirectory = memoriesRootDirectory;
			return this;
		}

		public Builder memorySystemPrompt(Resource memorySystemPrompt) {
			Assert.notNull(memorySystemPrompt, "记忆系统提示不能为null");
			this.memorySystemPrompt = memorySystemPrompt;
			return this;
		}

		public Builder memoryConsolidationTrigger(BiPredicate<ChatClientRequest, Instant> memoryConsolidationTrigger) {
			Assert.notNull(memoryConsolidationTrigger, "记忆整合触发器不能为null");
			this.memoryConsolidationTrigger = memoryConsolidationTrigger;
			return this;
		}

		public AutoMemoryToolsAdvisor build() {

			Assert.notNull(this.memorySystemPrompt, "记忆系统提示不能为null");
			Assert.hasText(this.memoriesRootDirectory, "记忆根目录不能为空");

			List<ToolCallback> memoryToolCallbacks = Arrays.asList(MethodToolCallbackProvider.builder()
				.toolObjects(AutoMemoryTools.builder().memoriesDir(this.memoriesRootDirectory).build())
				.build()
				.getToolCallbacks());

			String memorySystemPromptText = PromptTemplate.builder()
				.resource(this.memorySystemPrompt)
				.variables(Map.of("MEMORIES_ROOT_DIERCTORY", this.memoriesRootDirectory))
				.build()
				.render();

			return new AutoMemoryToolsAdvisor(this.order, memorySystemPromptText, memoryToolCallbacks,
					this.memoryConsolidationTrigger);
		}

	}

}
