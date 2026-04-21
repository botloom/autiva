package cn.bitloom.agentic.model;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ChatClientConfig {

    @Bean
    public ChatClient.Builder deepSeekChatClientBuilder(@Qualifier("deepSeekChatModel") ChatModel deepSeekChatModel) {
        return ChatClient.builder(deepSeekChatModel);
    }

    @Bean
    public ChatClient.Builder zhiPuChatClientBuilder(@Qualifier("zhiPuAiChatModel") ChatModel zhiPuAiChatModel) {
        return ChatClient.builder(zhiPuAiChatModel);
    }

}
