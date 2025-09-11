package cn.bitloom.autiva.agentic.agent.browser;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.ResourceUtils;
import reactor.core.publisher.Sinks;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The type Interactive browser context.
 *
 * @author bitloom
 */
@Slf4j
public class InteractiveBrowserContext {

    private final String id;
    private final BrowserContext context;
    private final Sinks.Many<UserMessage> eventSink;
    private final AtomicReference<InteractivePage> currentPage;
    private final Map<String, InteractivePage> pageIdMappedInteractivePage;
    private final Map<Page, InteractivePage> pageMappedInteractivePage;

    /**
     * Instantiates a new Interactive browser context.
     *
     * @param context the context
     */
    public InteractiveBrowserContext(String id, BrowserContext context, Sinks.Many<UserMessage> eventSink) {
        this.id = id;
        this.context = context;
        this.eventSink = eventSink;
        this.currentPage = new AtomicReference<>(null);
        this.pageIdMappedInteractivePage = new HashMap<>();
        this.pageMappedInteractivePage = new HashMap<>();
        this.context.onPage(this::attachPageListener);
        this.context.onClose((toCloseContext) -> {
            try {
                //保存登录信息
                File session = ResourceUtils.getFile(ResourceUtils.CLASSPATH_URL_PREFIX + "/session/" + id + ".json");
                toCloseContext.storageState(
                        new BrowserContext.StorageStateOptions().setPath(session.toPath())
                );
            } catch (FileNotFoundException e) {
                log.error("[InteractiveBrowserContext]-[onClose],保存session文件失败", e);
            }
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
            //如果page已存在则刷新可交互元素
            for (Map.Entry<Page, InteractivePage> entry : this.pageMappedInteractivePage.entrySet()) {
                if (entry.getKey().equals(page)) {
                    InteractivePage interactivePage = entry.getValue();
                    interactivePage.scanInteractiveElement();
                    return;
                }
            }
            //创建可交互page
            String pageId = UUID.randomUUID().toString();
            InteractivePage interactivePage = new InteractivePage(page.url(), page);
            interactivePage.scanInteractiveElement();
            this.pageIdMappedInteractivePage.put(pageId, interactivePage);
            this.pageMappedInteractivePage.put(page, interactivePage);
            //如果当前page为空则设置为当前page
            this.currentPage.compareAndSet(null, interactivePage);
        });
        //刷新
        page.onFrameNavigated((frame) -> this.pageMappedInteractivePage.get(frame.page()).scanInteractiveElement());
        //弹框
//        page.onFrameAttached();
        //弹框消失
//        page.onFrameDetached();
    }

    private void onPageDomMutation(Page page) {
        // 监听到弹窗时发送系统消息
        page.exposeFunction("onDomMutation", event -> {
            this.eventSink.tryEmitNext(UserMessage.builder().text("弹窗").build());
            return null;
        });

        String js = """
                    (() => {
                        const observer = new MutationObserver((mutationsList) => {
                            let shouldNotifyAI = false;
                
                            for (const m of mutationsList) {
                                if (m.type === 'childList' && (m.addedNodes.length > 0 || m.removedNodes.length > 0)) {
                                    shouldNotifyAI = true;
                
                                    // 检查新增节点是否可能是弹窗
                                    m.addedNodes.forEach(node => {
                                        if (node.nodeType !== Node.ELEMENT_NODE) return;
                                        const el = node;
                                        const style = window.getComputedStyle(el);
                                        const rect = el.getBoundingClientRect();
                
                                        if (style.display !== 'none' && style.visibility !== 'hidden'
                                            && rect.width > 50 && rect.height > 50
                                            && (style.position === 'fixed' || style.position === 'absolute')) {
                
                                            // 发现可能弹窗，通知Java
                                            window.onDomMutation('发现可能弹窗: ' + el.innerText);
                
                                            // 尝试点击关闭按钮
                                            const btn = el.querySelector('button, .close, [class*=close]');
                                            if (btn) btn.click();
                                        }
                                    });
                                    break; // 已发现新增节点，跳出
                                }
                
                                if (m.type === 'attributes') {
                                    shouldNotifyAI = true;
                                    break;
                                }
                            }
                
                            if (shouldNotifyAI) {
                                window.onDomMutation('页面更新了');
                            }
                        });
                
                        observer.observe(document.body, { childList: true, subtree: true, attributes: true });
                    })();
                """;

        page.evaluate(js);
    }


}
