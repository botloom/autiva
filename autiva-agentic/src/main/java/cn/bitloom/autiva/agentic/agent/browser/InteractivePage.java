package cn.bitloom.autiva.agentic.agent.browser;

import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The type Interactive page.
 */
@Data
@Slf4j
public class InteractivePage {

    private String url;
    private Page page;
    private AtomicInteger globalId = new AtomicInteger(0);
    private String elementTree;

    /**
     * Instantiates a new Interactive page.
     *
     * @param url  the url
     * @param page the page
     */
    public InteractivePage(String url, Page page) {
        this.url = url;
        this.page = page;
    }

    /**
     * 关闭页面
     */
    public void close() {
        this.page.close();
    }

    /**
     * 扫描可交互元素
     */
    public void scanInteractiveElement() {
        StringBuilder treeBuilder = new StringBuilder();
        for (Frame frame : this.page.frames()) {
            //删除所有overlay
            frame.evaluate("() => document.querySelectorAll('div[autiva-interactive-overlay-id]').forEach(overlay => overlay.remove());");
            /*
             * 查询所有可交互元素并设置overlay
             */
            Locator frameLocator = frame.locator("button, input, a, textarea, select, [role='button'], *[onclick], [tabindex], [contenteditable]");
            for (Locator elLocator : frameLocator.all()) {
                BoundingBox box = elLocator.boundingBox();
                if (box == null || box.width == 0 || box.height == 0) {
                    continue;
                }
                String autivaInteractiveId = elLocator.getAttribute("autiva-interactive-id");
                if (Objects.isNull(autivaInteractiveId)) {
                    autivaInteractiveId = frame.name() + "@" + this.globalId.getAndIncrement();
                    elLocator.evaluate("(el, id) => el.setAttribute('autiva-interactive-id', id)", autivaInteractiveId);
                    treeBuilder.append("*");
                }
                String tagName = elLocator.evaluate("el => el.tagName").toString();
                treeBuilder.append("[").append(autivaInteractiveId).append("]<")
                        .append(tagName).append(">").append(elLocator.textContent()).append("</").append(tagName)
                        .append(">\n");
                this.highlight(autivaInteractiveId, box);
            }
        }
        this.elementTree = treeBuilder.toString();
    }

    /**
     * 高亮元素
     */
    private void highlight(String idx, BoundingBox box) {
        String[] palette = {"red", "blue", "green", "orange", "purple", "brown"};
        String color = palette[idx.length() % palette.length];
        String js = """
                    ([idx, x, y, width, height, color]) => {
                        const overlay = document.createElement('div');
                        overlay.innerText = '[' + idx + ']';
                        overlay.setAttribute('autiva-interactive-overlay-id', idx);
                        overlay.style.position = 'absolute';
                        overlay.style.left = x + 'px';
                        overlay.style.top = y + 'px';
                        overlay.style.width = width + 'px';
                        overlay.style.height = height + 'px';
                        overlay.style.backgroundColor = 'rgba(255,0,0,0.2)';
                        overlay.style.border = '2px solid red';
                        overlay.style.color = color;
                        overlay.style.fontSize = '12px';
                        overlay.style.fontWeight = 'bold';
                        overlay.style.display = 'flex';
                        overlay.style.alignItems = 'flex-start';
                        overlay.style.justifyContent = 'flex-start';
                        overlay.style.padding = '2px';
                        overlay.style.zIndex = 999999;
                        overlay.style.pointerEvents = 'none';
                        document.body.appendChild(overlay);
                    }
                """;
        this.page.evaluate(js, new Object[]{
                idx, (int) box.x, (int) box.y, (int) box.width, (int) box.height, color
        });
    }

    /**
     * Gets element by id.
     *
     * @param id the id
     * @return the element by id
     */
    public Locator getElementById(String id) {
        String[] frameAndAutivaInteractiveId = id.split("@");
        return this.page.frame(frameAndAutivaInteractiveId[0]).getByTestId(frameAndAutivaInteractiveId[1]);
    }
}
