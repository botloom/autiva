package cn.bitloom.agentic.agent.subagent.generic;

import cn.bitloom.agentic.agent.subagent.SubagentType;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.tool.command.CommandTools;
import cn.bitloom.agentic.tool.core.*;
import cn.bitloom.agentic.tool.serach.BochaSearchProvider;
import cn.bitloom.agentic.tool.serach.SearchProvider;
import cn.bitloom.agentic.tool.serach.WebSearchTool;
import org.springframework.ai.chat.client.ChatClient;
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

        private String bochaApiKey = System.getenv("BOCHA_API_KEY");
        private final Map<ModelTypeEnum, ChatClient.Builder> chatClientBuilderMap = new ConcurrentHashMap<>();
        private SkillManager skillManager;
        private final List<String> skillsDirectories = new ArrayList<>();

        public Builder bochaApiKey(String bochaApiKey) {
            this.bochaApiKey = bochaApiKey;
            return this;
        }

        public Builder chatClientBuilder(ModelTypeEnum modelId, ChatClient.Builder chatClientBuilder) {
            this.chatClientBuilderMap.put(modelId, chatClientBuilder);
            return this;
        }

        public Builder chatClientBuilders(Map<ModelTypeEnum, ChatClient.Builder> chatClientBuilderMap) {
            this.chatClientBuilderMap.putAll(chatClientBuilderMap);
            return this;
        }

        public Builder skillManager(SkillManager skillManager) {
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
            } catch (IOException ex) {
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

        public SubagentType build() {
            return new SubagentType(
                    new GenericSubagentResolver(),
                    new GenericSubagentExecutor(this.chatClientBuilderMap, this.defaultGenericSubagentTools(), this.skillManager, this.skillsDirectories)
            );
        }

        private List<ToolCallback> defaultGenericSubagentTools() {

            List<Object> toolObjects = new ArrayList<>(List.of(
                    TodoWriteTool.builder().build(), GrepTool.builder().build(), GlobTool.builder().build(),
                    CommandTools.builder().build(),
                    FileSystemTools.builder().build()
            ));

            List<ToolCallback> commonTools = List.of(MethodToolCallbackProvider.builder()
                    .toolObjects(toolObjects.toArray())
                    .build()
                    .getToolCallbacks());

            List<ToolCallback> defaultCallbacks = new ArrayList<>(commonTools);

            if (this.skillManager != null && !this.skillManager.getAllSkills().isEmpty()) {
                defaultCallbacks.add(this.skillManager.buildToolCallback());
            }

            SearchProvider searchProvider = resolveSearchProvider();
            if (searchProvider != null) {
                defaultCallbacks.add(MethodToolCallbackProvider.builder()
                        .toolObjects(WebSearchTool.builder(searchProvider).resultCount(15).build())
                        .build()
                        .getToolCallbacks()[0]);
            }

            return defaultCallbacks;
        }

        private SearchProvider resolveSearchProvider() {
            if (StringUtils.hasText(this.bochaApiKey)) {
                return new BochaSearchProvider(this.bochaApiKey);
            }
            return null;
        }

    }

}
