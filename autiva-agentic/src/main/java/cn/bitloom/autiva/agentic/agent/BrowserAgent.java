package cn.bitloom.autiva.agentic.agent;

import cn.bitloom.autiva.agentic.acm.AutivaConfig;
import cn.bitloom.autiva.agentic.agent.browser.InteractiveBrowser;
import cn.bitloom.autiva.agentic.agent.browser.message.BrowserMessageManager;
import cn.bitloom.autiva.agentic.core.AbstractReActAgent;
import cn.bitloom.autiva.agentic.enums.MetaDataInfoEnum;
import cn.bitloom.autiva.agentic.tool.BrowserTools;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Scanner;


/**
 * The type Browser agent.
 *
 * @author bitloom
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "autiva.browser.enabled", havingValue = "true")
public class BrowserAgent extends AbstractReActAgent implements ApplicationRunner {

    @Getter
    private final InteractiveBrowser interactiveBrowser;

    /**
     * Instantiates a new Abstract base agent.
     *
     * @param chatModel          the chat model
     * @param config             the config
     * @param interactiveBrowser the interactive browser
     */
    public BrowserAgent(DeepSeekChatModel chatModel, AutivaConfig config, InteractiveBrowser interactiveBrowser, BrowserMessageManager messageManager) {
        super(
                config.getBrowser().getDefaultSystemPrompt(),
                config.getWorkDir(),
                messageManager,
                List.of(BrowserTools.class),
                chatModel
        );
        this.interactiveBrowser = interactiveBrowser;
        this.interactiveBrowser.getEventSink()
                .doOnNext(this.messageManager::emit)
                .subscribe();
    }

    @Override
    public Flux<String> run(String sessionId) {
        this.interactiveBrowser.newContext(sessionId);
        return super.run(sessionId);
    }

    @Override
    public void run(ApplicationArguments args) {
        String sessionId = "autiva";
        this.run(sessionId)
                .doOnNext(System.out::print)
                .doOnComplete(System.out::println)
                .subscribe();
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("Autiva> ");
            String nextLine = scanner.nextLine();
            if (nextLine.equalsIgnoreCase("quit")) {
                break;
            }
            this.messageManager.emit(UserMessage.builder().text(nextLine).metadata(Map.of(MetaDataInfoEnum.SESSION_ID.name(), sessionId, MetaDataInfoEnum.MANUAL.name(), "")).build());
        }
    }

}
