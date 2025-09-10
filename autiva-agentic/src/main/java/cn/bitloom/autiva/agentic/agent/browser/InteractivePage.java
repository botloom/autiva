package cn.bitloom.autiva.agentic.agent.browser;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
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
        this.clearAllOverlay();
        StringBuilder sb = new StringBuilder();

        List<ElementHandle> elements = page.querySelectorAll(
                "button, input, a, textarea, select, [role='button'], *[onclick], [tabindex], [contenteditable]"
        );

        for (ElementHandle el : elements) {
            try {
                BoundingBox box = el.boundingBox();
                if (box == null || box.width == 0 || box.height == 0) {
                    continue;
                }

                Object elementIdObj = el.evaluate("e => e.dataset.autivaId");
                boolean isNew = false;

                if (elementIdObj == null) {
                    elementIdObj = this.globalId.getAndIncrement();
                    el.evaluate("(e, id) => e.dataset.autivaId = id", elementIdObj);
                    isNew = true;
                }

                int elementId = elementIdObj instanceof Number
                        ? ((Number) elementIdObj).intValue()
                        : Integer.parseInt(String.valueOf(elementIdObj));

                this.highlight(elementId, box);

                String text = el.innerText();
                if (text.isBlank()) {
                    text = el.getAttribute("value") != null ? el.getAttribute("value") : "";
                }

                String tag = el.evaluate("e => e.tagName.toLowerCase()").toString();
                sb.append(isNew ? "*" : "").append("[").append(elementId).append("]<").append(tag).append(">").append(text).append("</").append(tag).append(">\n");

            } catch (Exception e) {
                log.warn("[scanInteractiveElement] 处理失败", e);
            }
        }

        this.elementTree = sb.toString();
    }

    /**
     * 高亮元素
     */
    private void highlight(int idx, BoundingBox box) {
        String[] palette = {"red", "blue", "green", "orange", "purple", "brown"};
        String color = palette[idx % palette.length];
        String js = """
                    ([idx, x, y, width, height, color]) => {
                        const overlay = document.createElement('div');
                        overlay.innerText = '[' + idx + ']';
                        overlay.dataset.autivaOverlay = idx;
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

    private void clearAllOverlay() {
        String js = """
                    () => {
                        document.querySelectorAll('div[data-autiva-overlay]').forEach(overlay => overlay.remove());
                    }
                """;
        try {
            page.evaluate(js);
        } catch (Exception e) {
            log.warn("[clearAllOverlays] 失败", e);
        }
    }

    /**
     * 根据 data-autiva-id 获取元素
     *
     * @param id the id
     * @return the element by id
     */
    public ElementHandle getElementById(int id) {
        try {
            return page.querySelector(String.format("[data-autiva-id='%d']", id));
        } catch (Exception e) {
            log.warn("[getElementById] 失败 id={}", id, e);
            return null;
        }
    }
}
