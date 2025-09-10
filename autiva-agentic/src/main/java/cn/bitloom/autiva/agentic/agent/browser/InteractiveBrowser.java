package cn.bitloom.autiva.agentic.agent.browser;

import cn.bitloom.autiva.agentic.agent.browser.message.BrowserAgentInput;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The type Interactive browser.
 *
 * @author bitloom
 */
@Slf4j
@Component
public class InteractiveBrowser {

    private final Browser browser;
    private final Map<String, InteractiveBrowserContext> contextMap;
    private final Sinks.Many<UserMessage> eventSink;

    /**
     * Instantiates a new Interactive browser.
     */
    public InteractiveBrowser() {
        this.browser = Playwright.create().chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
        this.contextMap = new ConcurrentHashMap<>();
        this.eventSink = Sinks.many().multicast().onBackpressureBuffer();
    }

    /**
     * Gets system message.
     *
     * @return the system message
     */
    public Flux<UserMessage> getEventSink() {
        return this.eventSink.asFlux();
    }

    /**
     * Gets context.
     *
     * @param contextId the context id
     * @return the context
     */
    public InteractiveBrowserContext getContext(String contextId) {
        return this.contextMap.get(contextId);
    }

    /**
     * New context.
     *
     * @param contextId the context id
     */
    public void newContext(String contextId) {
        if (this.contextMap.containsKey(contextId)) {
            return;
        }
        InteractiveBrowserContext interactiveBrowserContext = new InteractiveBrowserContext(this.browser.newContext(), eventSink);
        this.contextMap.put(contextId, interactiveBrowserContext);
    }

    /**
     * Close.
     */
    public void close() {
        this.contextMap.values().forEach(InteractiveBrowserContext::close);
        this.eventSink.tryEmitComplete();
        this.browser.close();
    }

    public BrowserAgentInput.BrowserStatus getBrowserStatus(String sessionId) {
        BrowserAgentInput.BrowserStatus browserStatus = new BrowserAgentInput.BrowserStatus();
        InteractiveBrowserContext context = this.getContext(sessionId);
        if (Objects.isNull(context)) {
            return browserStatus;
        }
        InteractivePage currentPage = context.getCurrentPage();
        if (Objects.isNull(currentPage)) {
            return browserStatus;
        }
        browserStatus.setUrl(context.getCurrentPage().getUrl());
        browserStatus.setElementTree(context.getCurrentPage().getElementTree());
        return browserStatus;
    }

}
