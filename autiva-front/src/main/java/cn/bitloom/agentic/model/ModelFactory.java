package cn.bitloom.agentic.model;

import cn.bitloom.config.ConfigManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ModelFactory {

    private final EnumMap<ModelTypeEnum, ChatModel> chatModelMap = new EnumMap<>(ModelTypeEnum.class);
//    private final ChatMemory chatMemory;
//    private final ToolCallingManager toolCallingManager;
    private final ConfigManager configManager;
    private final OpenAiApi baseOpenAiApi;
    private final OpenAiChatModel baseChatModel;

    @PostConstruct
    public void init() {
        this.chatModelMap.put(ModelTypeEnum.DEEPSEEK, this.deepSeekChatModel());
    }


    public ChatModel model(ModelTypeEnum model) {
        return this.chatModelMap.get(model);
    }

    private ChatModel deepSeekChatModel() {
        OpenAiApi deepSeekApi = baseOpenAiApi.mutate()
                .baseUrl(configManager.getDeepseekBaseUrl())
                .completionsPath(configManager.getDeepseekCompletionsPath())
                .apiKey(configManager.getDeepseekApiKey())
                .build();
        return baseChatModel.mutate()
                .openAiApi(deepSeekApi)
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .model(configManager.getDeepseekChatModel())
                                .extraBody(Map.of("thinking", Map.of("type", "disabled")))
                                .build())
                .build();
//        Consumer<ChatClient.AdvisorSpec> advisorSpecConsumer = a -> a.advisors(
//                MessageChatMemoryAdvisor.builder(this.chatMemory).build(),
//                LoggingAdvisor.builder().build(),
//                getProactiveContextAdvisor(),
//                ToolCallAdvisor.builder()
//                        .toolCallingManager(toolCallingManager)
//                        .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 300)
//                        .conversationHistoryEnabled(true)
//                        .disableMemory()
//                        .build()
//        );
//        return ChatClient.builder(deepSeekModel).defaultAdvisors(advisorSpecConsumer);
    }

}
