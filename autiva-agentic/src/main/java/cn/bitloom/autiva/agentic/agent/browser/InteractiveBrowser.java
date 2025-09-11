package cn.bitloom.autiva.agentic.agent.browser;

import cn.bitloom.autiva.agentic.agent.browser.message.BrowserAgentInput;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        Playwright playwright = Playwright.create();
        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
        playwright.selectors().setTestIdAttribute("autiva-interactive-id");
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
        Browser.NewContextOptions options = new Browser.NewContextOptions();
        Path session = Path.of("session", contextId + ".json");
        if (Files.exists(session)) {
            options.setStorageStatePath(session);
        }
        BrowserContext browserContext = browser.newContext(options);
        this.contextMap.put(contextId, new InteractiveBrowserContext(contextId, browserContext, this.eventSink));
    }

    /**
     * Close.
     */
    public void close() {
        this.contextMap.values().forEach(InteractiveBrowserContext::close);
        this.eventSink.tryEmitComplete();
        this.browser.close();
    }

    /**
     * Gets browser status.
     *
     * @param sessionId the session id
     * @return the browser status
     */
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
        browserStatus.setCurrentPage(currentPage.getId());
        browserStatus.setElementTree(currentPage.scanInteractiveElement());
        List<BrowserAgentInput.PageStatus> pageList = context.pageList().stream()
                .map(page -> {
                    BrowserAgentInput.PageStatus pageStatus = new BrowserAgentInput.PageStatus();
                    pageStatus.setId(page.getId());
                    pageStatus.setUrl(page.getUrl());
                    return pageStatus;
                })
                .toList();
        browserStatus.setPageList(pageList);
        return browserStatus;
    }

}
