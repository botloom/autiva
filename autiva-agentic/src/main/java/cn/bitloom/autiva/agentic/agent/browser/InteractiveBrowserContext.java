package cn.bitloom.autiva.agentic.agent.browser;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Sinks;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The type Interactive browser context.
 *
 * @author bitloom
 */
@Slf4j
public class InteractiveBrowserContext {

    private final BrowserContext context;
    private final Sinks.Many<UserMessage> eventSink;
    private final AtomicReference<InteractivePage> currentPage;
    private final Map<String, InteractivePage> pageIdMappedInteractivePage;
    private final Map<Page, InteractivePage> pageMappedInteractivePage;

    /**
     * Instantiates a new Interactive browser context.
     *
     * @param id        the id
     * @param context   the context
     * @param eventSink the event sink
     */
    public InteractiveBrowserContext(String id, BrowserContext context, Sinks.Many<UserMessage> eventSink) {
        this.context = context;
        this.eventSink = eventSink;
        this.currentPage = new AtomicReference<>(null);
        this.pageIdMappedInteractivePage = new HashMap<>();
        this.pageMappedInteractivePage = new HashMap<>();
        this.context.onPage(this::attachPageListener);
        this.context.onClose((toCloseContext) -> {
            Path session = Path.of("session", id + ".json");
            toCloseContext.storageState(
                    new BrowserContext.StorageStateOptions().setPath(session)
            );
        });
    }

    /**
     * New page.
     *
     * @param url the url
     */
    public void newPage(String url) {
        Page page = this.context.newPage();
        page.navigate(url);
        page.bringToFront();
    }

    /**
     * Gets current page.
     *
     * @return the current page
     */
    public InteractivePage getCurrentPage() {
        return this.currentPage.get();
    }

    /**
     * Switch page.
     *
     * @param pageId the page id
     */
    public void switchPage(String pageId) {
        this.currentPage.set(this.pageIdMappedInteractivePage.get(pageId));
        this.currentPage.get().getPage().bringToFront();
    }

    /**
     * Close page.
     *
     * @param pageId the page id
     */
    public void closePage(String pageId) {
        InteractivePage interactivePage = this.pageIdMappedInteractivePage.get(pageId);
        interactivePage.close();
        this.pageIdMappedInteractivePage.remove(pageId);
        this.pageMappedInteractivePage.remove(interactivePage.getPage());
    }

    /**
     * Page list list.
     *
     * @return the list
     */
    public List<InteractivePage> pageList() {
        return new ArrayList<>(this.pageMappedInteractivePage.values());
    }

    /**
     * Close.
     */
    public void close() {
        this.context.close();
        this.pageIdMappedInteractivePage.values().forEach(InteractivePage::close);
        this.pageIdMappedInteractivePage.clear();
        this.pageMappedInteractivePage.clear();
    }

    private void attachPageListener(Page page) {
        //加载完成
        page.onLoad(loadedPage -> {
            //如果page已存在则直接返回
            if (this.pageMappedInteractivePage.containsKey(page)) {
                return;
            }
            //创建可交互page
            String pageId = UUID.randomUUID().toString();
            InteractivePage interactivePage = new InteractivePage(pageId, page.url(), page);
            this.pageIdMappedInteractivePage.put(pageId, interactivePage);
            this.pageMappedInteractivePage.put(page, interactivePage);
            //如果当前page为空则设置为当前page
            this.currentPage.set(interactivePage);
        });
    }


}
