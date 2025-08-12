package cn.bitloom.autiva.agent;

import cn.bitloom.autiva.agent.acm.AutivaConfig;
import cn.bitloom.autiva.agent.core.AbstractAgent;
import cn.bitloom.autiva.common.util.UserInputUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/**
 * The type Default agent.
 *
 * @author bitloom
 */
@Slf4j
@Component
public class BaseAgent extends AbstractAgent implements ApplicationRunner {

    public BaseAgent(DeepSeekChatModel chatModel, JdbcChatMemoryRepository chatMemoryRepository, AutivaConfig config) {
        super(
                UUID.randomUUID().toString(),
                config.getWorkDir(),
                config.getBase().getDefaultSystemPrompt(),
                List.of(),
                chatModel,
                chatMemoryRepository
        );
    }

    @Override
    protected UserMessage input() {
        String userInput = UserInputUtil.getUserInputFromConsole();
        return UserMessage.builder()
                .text(userInput)
                .build();
    }

    @Override
    protected void output(Flux<ChatClientResponse> result) {
        result.subscribe(
                response -> System.out.print(response.chatResponse().getResult().getOutput().getText()),
                error -> System.err.println("出错: " + error),
                System.out::println
        );
    }

    @Override
    public void run(ApplicationArguments args) {
        super.run();
    }
}
