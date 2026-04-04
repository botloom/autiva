package cn.bitloom.util;

import cn.bitloom.store.Store;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Objects;

/**
 * The type Browser model.
 *
 * @author bitloom
 */
@Slf4j
public class BrowserManager {

    private static Playwright playwright;
    private static Process chromeProcess;
    private static Browser browser;
    private static BrowserContext context;

    static {
        try {
            playwright = Playwright.create();
            int port = 9222;
            ProcessBuilder pb = new ProcessBuilder(
                    Store.browserPath.get().toString(),
                    "--remote-debugging-port=9222"
//                    "--user-data-dir=" + AppConstants.Base.PLAYWRIGHT_DIR
            );
            pb.redirectErrorStream(true);
            chromeProcess = pb.start();
            browser = playwright.chromium().connectOverCDP("http://localhost:" + port);
            context = browser.contexts().isEmpty() ? browser.newContext() : browser.contexts().get(0);
        } catch (IOException e) {
            log.error("浏览器连接失败", e);
        }
    }

    private BrowserManager() {
    }

    /**
     * Open page.
     *
     * @param url the url
     * @return the page
     */
    public static Page open(String url) {
        Page page;
        page = context.newPage();
        page.navigate(url);
        return page;
    }

    /**
     * Close.
     */
    public static void close() {
        if (Objects.nonNull(context)) {
            context.close();
        }
        if (Objects.nonNull(browser)) {
            browser.close();
        }
        if (Objects.nonNull(chromeProcess)) {
            chromeProcess.destroyForcibly();
        }
        if (Objects.nonNull(playwright)) {
            playwright.close();
        }
    }

}
