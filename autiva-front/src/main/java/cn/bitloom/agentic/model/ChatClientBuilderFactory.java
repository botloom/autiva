package cn.bitloom.agentic.model;

import cn.bitloom.agentic.advisor.EvolutionHintProvider;
import cn.bitloom.agentic.advisor.LoggingAdvisor;
import cn.bitloom.agentic.advisor.ProactiveContextAdvisor;
import cn.bitloom.agentic.memory.JournalManager;
import cn.bitloom.agentic.memory.MemorySearchService;
import cn.bitloom.config.ConfigManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Configuration;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
public class ChatClientBuilderFactory {

    private final EnumMap<ModelTypeEnum, ChatClient.Builder> chatClientBuilderMap = new EnumMap<>(ModelTypeEnum.class);
    private final ChatMemory chatMemory;
    private final ToolCallingManager toolCallingManager;
    private final ConfigManager configManager;
    private final OpenAiApi baseOpenAiApi;
    private final OpenAiChatModel baseChatModel;
    private final JournalManager journalManager;
    private final MemorySearchService memorySearchService;
    private final EvolutionHintProvider evolutionHintProvider;

    private ProactiveContextAdvisor proactiveContextAdvisor;

    private ProactiveContextAdvisor getProactiveContextAdvisor() {
        if (this.proactiveContextAdvisor == null) {
            this.proactiveContextAdvisor = new ProactiveContextAdvisor(
                    journalManager, memorySearchService, evolutionHintProvider);
        }
        return this.proactiveContextAdvisor;
    }

    @PostConstruct
    public void init() {
        this.chatClientBuilderMap.put(ModelTypeEnum.DEEPSEEK, this.deepSeekChatClientBuilder());
    }

    public EnumMap<ModelTypeEnum, ChatClient.Builder> chatClientBuilderMap() {
        return this.chatClientBuilderMap;
    }

    public ChatClient.Builder model(ModelTypeEnum model) {
        return this.chatClientBuilderMap.get(model);
    }

    private ChatClient.Builder deepSeekChatClientBuilder() {
        OpenAiApi deepSeekApi = baseOpenAiApi.mutate()
                .baseUrl(configManager.getDeepseekBaseUrl())
                .completionsPath(configManager.getDeepseekCompletionsPath())
                .apiKey(configManager.getDeepseekApiKey())
                .build();
        OpenAiChatModel deepSeekModel = baseChatModel.mutate()
                .openAiApi(deepSeekApi)
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .model(configManager.getDeepseekChatModel())
                                .extraBody(Map.of("thinking", Map.of("type", "disabled")))
                                .build())
                .build();
        Consumer<ChatClient.AdvisorSpec> advisorSpecConsumer = a -> a.advisors(
                MessageChatMemoryAdvisor.builder(this.chatMemory).build(),
                LoggingAdvisor.builder().build(),
                getProactiveContextAdvisor(),
                ToolCallAdvisor.builder()
                        .toolCallingManager(toolCallingManager)
                        .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 300)
                        .conversationHistoryEnabled(true)
                        .disableMemory()
                        .build()
        );
        return ChatClient.builder(deepSeekModel).defaultAdvisors(advisorSpecConsumer);
    }

}
