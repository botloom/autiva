package cn.bitloom.agentic.model;

import cn.bitloom.config.ConfigManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ModelFactory {

    private final ConfigManager configManager;
    private final EnumMap<ModelTypeEnum, ChatModel> chatModelMap = new EnumMap<>(ModelTypeEnum.class);

    @PostConstruct
    public void init() {
        this.chatModelMap.put(ModelTypeEnum.DEEPSEEK, this.deepSeekChatModel());
    }

    public ChatModel model(ModelTypeEnum model) {
        return this.chatModelMap.get(model);
    }

    private ChatModel deepSeekChatModel() {
        return OpenAiChatModel.builder()
                .options(
                        OpenAiChatOptions.builder()
                                .baseUrl(configManager.getDeepseekBaseUrl())
                                .apiKey(configManager.getDeepseekApiKey())
                                .model(configManager.getDeepseekChatModel())
                                .extraBody(Map.of("thinking", Map.of("type", "disabled")))
                                .build()
                )
                .build();
    }

}
