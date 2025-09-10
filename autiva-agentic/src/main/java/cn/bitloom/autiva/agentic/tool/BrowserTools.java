package cn.bitloom.autiva.agentic.tool;

import cn.bitloom.autiva.agentic.agent.BrowserAgent;
import cn.bitloom.autiva.agentic.agent.browser.InteractiveBrowserContext;
import cn.bitloom.autiva.agentic.agent.browser.InteractivePage;
import com.microsoft.playwright.ElementHandle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * The type Browser tools.
 *
 * @author bitloom
 */
@Slf4j
public class BrowserTools extends AbstractTools<BrowserAgent> {

    /**
     * Instantiates a new Browser tools.
     *
     * @param AGENT the agent
     */
    public BrowserTools(BrowserAgent AGENT) {
        super(AGENT);
    }

    private String operateElementByIndex(String sessionId, Integer elementId, BiConsumer<InteractivePage, ElementHandle> operator) {
        InteractiveBrowserContext context = this.AGENT.getInteractiveBrowser().getContext(sessionId);
        InteractivePage currentPage = context.getCurrentPage();
        ElementHandle element = Objects.isNull(elementId) ? null : currentPage.getElementById(elementId);
        operator.accept(currentPage, element);
        return "操作成功";
    }

    /**
     * Open page.
     *
     * @param sessionId the session id
     * @param url       the url
     * @return the string
     */
    @Tool(description = "打开网页")
    public String openPage(@ToolParam(description = "会话ID") String sessionId, @ToolParam(description = "要打开网页的URL") String url) {
        log.info("[BrowserTools]-[openWebsite],sessionId:{},url:{}", sessionId, url);
        InteractiveBrowserContext context = this.AGENT.getInteractiveBrowser().getContext(sessionId);
        context.newPage(url);
        return "操作成功";
    }

    /**
     * Terminate current page string.
     *
     * @param sessionId the session id
     * @param pageId    the page id
     * @return the string
     */
    @Tool(description = "关闭页面")
    public String closePage(@ToolParam(description = "会话ID") String sessionId, @ToolParam(description = "页面ID") String pageId) {
        log.info("[BrowserTools]-[closePage],sessionId:{},pageId:{}", sessionId, pageId);
        InteractiveBrowserContext context = this.AGENT.getInteractiveBrowser().getContext(sessionId);
        context.closePage(pageId);
        return "关闭成功";
    }

    /**
     * Switch page string.
     *
     * @param sessionId the session id
     * @param pageId    the page id
     * @return the string
     */
    @Tool(description = "切换页面")
    public String switchPage(@ToolParam(description = "会话ID") String sessionId, @ToolParam(description = "页面ID") String pageId) {
        log.info("[BrowserTools]-[switchPage],sessionId:{},pageId:{}", sessionId, pageId);
        InteractiveBrowserContext context = this.AGENT.getInteractiveBrowser().getContext(sessionId);
        context.switchPage(pageId);
        return "切换成功";
    }

    /**
     * Click string.
     *
     * @param sessionId the session id
     * @param elId      the index
     * @return the string
     */
    @Tool(description = "点击可交互元素")
    public String click(@ToolParam(description = "会话ID") String sessionId, @ToolParam(description = "元素唯一编号") int elId) {
        log.info("[BrowserTools]-[click],sessionId:{},elId:{}", sessionId, elId);
        return this.operateElementByIndex(sessionId, elId, (page, el) -> {
            el.scrollIntoViewIfNeeded();
            el.click();
        });
    }


    /**
     * Input text string.
     *
     * @param sessionId the session id
     * @param elId      the el idx
     * @param text      the text
     * @return the string
     */
    @Tool(description = "在可交互元素中输入文本")
    public String inputText(@ToolParam(description = "会话ID") String sessionId, @ToolParam(description = "元素唯一编号") int elId, @ToolParam String text) {
        log.info("[BrowserTools]-[inputText],sessionId:{},elId:{},text:{}", sessionId, elId, text);
        return operateElementByIndex(sessionId, elId, (page, el) -> {
            el.scrollIntoViewIfNeeded();
            el.fill(text);
        });
    }

}
