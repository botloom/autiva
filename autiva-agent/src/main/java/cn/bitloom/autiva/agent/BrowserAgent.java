package cn.bitloom.autiva.agent;

import cn.bitloom.autiva.agent.acm.AutivaConfig;
import cn.bitloom.autiva.agent.core.AbstractAgent;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;


/**
 * The type Browser agent.
 *
 * @author bitloom
 */
@Component
public class BrowserAgent extends AbstractAgent {

    /**
     * Instantiates a new Abstract base agent.
     *
     * @param chatModel            the chat model
     * @param chatMemoryRepository the chat memory repository
     * @param config           the config
     */
    public BrowserAgent(DeepSeekChatModel chatModel, JdbcChatMemoryRepository chatMemoryRepository, AutivaConfig config) {
        super(
                UUID.randomUUID().toString(),
                config.getWorkDir(),
                config.getBrowser().getDefaultSystemPrompt(),
                List.of(),
                chatModel,
                chatMemoryRepository
        );
    }

    @Override
    protected UserMessage input() {
        return null;
    }

    @Override
    protected void output(Flux<ChatClientResponse> result) {

    }
}
