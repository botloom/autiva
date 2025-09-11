package cn.bitloom.autiva;

import cn.bitloom.autiva.web.AutivaApplication;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Unit test for simple App.
 *
 * @author bitloom
 */
@SpringBootTest(classes = AutivaApplication.class)
public class TestAutivaApplication {
    @Test
    public void test() {
        Playwright playwright = Playwright.create();
        Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
        playwright.selectors().setTestIdAttribute("autiva-interactive-id");
        Page page = browser.newPage();
        page.navigate("https://baidu.com");
    }
}
